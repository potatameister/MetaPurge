package com.metapurge.app.data.repository

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.provider.OpenableColumns
import android.util.Log
import androidx.exifinterface.media.ExifInterface
import com.metapurge.app.domain.model.AllTags
import com.metapurge.app.domain.model.GpsData
import com.metapurge.app.domain.model.ImageMetadata
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.*

class MetadataRepository(private val context: Context) {

    suspend fun readMetadata(uri: Uri): ImageMetadata? = withContext(Dispatchers.IO) {
        try {
            val metadataSize = calculateMetadataSizeStreaming(uri)
            val freshStream = context.contentResolver.openInputStream(uri) ?: return@withContext null
            val exif = ExifInterface(freshStream)
            freshStream.close()

            ImageMetadata(
                hasExif = metadataSize > 0,
                metadataSize = metadataSize,
                gps = extractGps(exif),
                camera = extractCamera(exif),
                dateTime = extractDateTime(exif),
                software = extractSoftware(exif),
                allTags = extractAllTags(exif)
            )
        } catch (e: Exception) {
            Log.e("MetaPurge", "Error reading metadata", e)
            null
        }
    }

    private fun calculateMetadataSizeStreaming(uri: Uri): Long {
        return try {
            context.contentResolver.openInputStream(uri)?.use { input ->
                val bis = BufferedInputStream(input)
                bis.mark(12)
                val head = ByteArray(12); bis.read(head); bis.reset()
                when {
                    isPng(head) -> calculatePngMetadataSizeStreaming(bis)
                    isWebp(head) -> calculateWebpMetadataSizeStreaming(bis)
                    isGif(head) -> calculateGifMetadataSizeStreaming(uri)
                    else -> calculateJpegMetadataSizeStreaming(bis)
                }
            } ?: 0
        } catch (e: Exception) { 0 }
    }

    suspend fun purgeMetadata(uri: Uri, originalName: String): Uri? = withContext(Dispatchers.IO) {
        val tempFile = File(context.cacheDir, "purging_${System.currentTimeMillis()}")
        var finalName = originalName
        try {
            context.contentResolver.openInputStream(uri)?.use { inputStream ->
                val bis = BufferedInputStream(inputStream)
                bis.mark(12)
                val head = ByteArray(12); bis.read(head); bis.reset()
                val needsRotation = checkOrientation(uri)
                val isTiff = isTiff(head)
                if (needsRotation || isTiff) {
                    finalName = originalName.substringBeforeLast(".") + ".jpg"
                }
                FileOutputStream(tempFile).use { outputStream ->
                    if (needsRotation) {
                        fixRotationAndPurgeSafe(uri, outputStream)
                    } else {
                        when {
                            isPng(head) -> purgePngStream(bis, outputStream)
                            isWebp(head) -> purgeWebpStream(bis, outputStream)
                            isGif(head) -> purgeGifStream(bis, outputStream)
                            isTiff -> normalizeTiffToJpeg(uri, outputStream)
                            else -> purgeJpegStream(bis, outputStream)
                        }
                    }
                }
            }
            val finalFile = File(context.cacheDir, "purged_${finalName}")
            if (finalFile.exists()) finalFile.delete()
            tempFile.renameTo(finalFile)
            Uri.fromFile(finalFile)
        } catch (e: Exception) {
            if (tempFile.exists()) tempFile.delete()
            Log.e("MetaPurge", "Purge failed", e)
            null
        }
    }

    private fun fixRotationAndPurgeSafe(uri: Uri, out: OutputStream) {
        val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, options) }
        val sampleSize = if ((options.outWidth * options.outHeight) > 20_000_000) 2 else 1
        val bitmap = context.contentResolver.openInputStream(uri)?.use { 
            BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sampleSize }) 
        } ?: return
        val exif = context.contentResolver.openInputStream(uri)?.use { ExifInterface(it) }
        val orientation = exif?.getAttributeInt(ExifInterface.TAG_ORIENTATION, 1) ?: 1
        val matrix = Matrix()
        when (orientation) {
            ExifInterface.ORIENTATION_ROTATE_90 -> matrix.postRotate(90f)
            ExifInterface.ORIENTATION_ROTATE_180 -> matrix.postRotate(180f)
            ExifInterface.ORIENTATION_ROTATE_270 -> matrix.postRotate(270f)
        }
        val rotated = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
        if (rotated != bitmap) bitmap.recycle()
        rotated.compress(Bitmap.CompressFormat.JPEG, 90, out)
        rotated.recycle()
    }

    private fun normalizeTiffToJpeg(uri: Uri, out: OutputStream) {
        context.contentResolver.openInputStream(uri)?.use { input ->
            val bitmap = BitmapFactory.decodeStream(input)
            bitmap?.compress(Bitmap.CompressFormat.JPEG, 95, out)
            bitmap?.recycle()
        }
    }

    private fun purgeJpegStream(input: InputStream, out: OutputStream) {
        val dis = DataInputStream(input)
        try {
            if (dis.readUnsignedShort() != 0xFFD8) return
            out.write(byteArrayOf(0xFF.toByte(), 0xD8.toByte()))
            while (true) {
                var b = dis.readUnsignedByte()
                if (b != 0xFF) continue
                var m = dis.readUnsignedByte()
                while (m == 0xFF) m = dis.readUnsignedByte()
                if (m == 0xDA) { out.write(0xFF); out.write(0xDA); dis.copyTo(out); break }
                val l = dis.readUnsignedShort()
                if (m !in 0xE0..0xEF && m != 0xFE) {
                    out.write(0xFF); out.write(m); out.write((l shr 8) and 0xFF); out.write(l and 0xFF)
                    val buffer = ByteArray(l - 2); dis.readFully(buffer); out.write(buffer)
                } else dis.skipBytes(l - 2)
            }
        } catch (e: Exception) {}
    }

    private fun purgePngStream(input: InputStream, out: OutputStream) {
        val head = ByteArray(8); input.read(head); out.write(head)
        val dis = DataInputStream(input)
        try {
            while (true) {
                val len = dis.readInt(); val type = ByteArray(4); dis.readFully(type)
                val ts = String(type)
                if (ts in listOf("IHDR", "PLTE", "IDAT", "IEND", "tRNS", "sRGB", "gAMA", "cHRM")) {
                    val dos = DataOutputStream(out); dos.writeInt(len); dos.write(type)
                    val buf = ByteArray(8192); var rem = len
                    while (rem > 0) { val r = dis.read(buf, 0, minOf(rem, buf.size)); dos.write(buf, 0, r); rem -= r }
                    dos.writeInt(dis.readInt())
                } else dis.skipBytes(len + 4)
                if (ts == "IEND") break
            }
        } catch (e: Exception) {}
    }

    private fun purgeWebpStream(input: InputStream, out: OutputStream) {
        val bodyFile = File(context.cacheDir, "webp_body_${System.currentTimeMillis()}")
        try {
            val dis = DataInputStream(input); dis.skipBytes(12)
            var bodySize = 0L
            bodyFile.outputStream().use { bodyOut ->
                try {
                    while (true) {
                        val type = ByteArray(4); dis.readFully(type)
                        val ts = String(type); val lenLE = dis.readInt(); val len = Integer.reverseBytes(lenLE)
                        if (ts !in listOf("EXIF", "XMP ", "ICCP")) {
                            bodyOut.write(type); DataOutputStream(bodyOut).writeInt(lenLE)
                            val buf = ByteArray(8192); var rem = len
                            while (rem > 0) { val r = dis.read(buf, 0, minOf(rem, buf.size)); bodyOut.write(buf, 0, r); rem -= r }
                            bodySize += 8 + len
                            if (len % 2 != 0) { bodyOut.write(0); dis.skipBytes(1); bodySize += 1 }
                        } else dis.skipBytes(len + (len % 2))
                    }
                } catch (e: Exception) {}
            }
            val dos = DataOutputStream(out)
            dos.write("RIFF".toByteArray()); dos.writeInt(Integer.reverseBytes((bodySize + 4).toInt())); dos.write("WEBP".toByteArray())
            bodyFile.inputStream().use { it.copyTo(out) }
        } finally { if (bodyFile.exists()) bodyFile.delete() }
    }

    private fun purgeGifStream(input: InputStream, out: OutputStream) {
        val dis = DataInputStream(input); val head = ByteArray(6); dis.readFully(head); out.write(head)
        val lsd = ByteArray(7); dis.readFully(lsd); out.write(lsd)
        if (lsd[4].toInt() and 0x80 != 0) {
            val size = 3 * (1 shl ((lsd[4].toInt() and 0x07) + 1)); val gct = ByteArray(size); dis.readFully(gct); out.write(gct)
        }
        try {
            while (true) {
                val bt = dis.readUnsignedByte()
                if (bt == 0x21) {
                    val et = dis.readUnsignedByte(); var isMeta = et == 0xFE || et == 0xFF
                    val blocks = mutableListOf<ByteArray>()
                    while (true) { val sl = dis.readUnsignedByte(); val sub = ByteArray(sl + 1); sub[0] = sl.toByte(); if (sl > 0) dis.readFully(sub, 1, sl); blocks.add(sub); if (sl == 0) break }
                    if (et == 0xFF && blocks.isNotEmpty() && String(blocks[0], 1, minOf(8, blocks[0].size - 1)) == "NETSCAPE") isMeta = false
                    if (!isMeta) { out.write(0x21); out.write(et); blocks.forEach { out.write(it) } }
                } else if (bt == 0x2C) {
                    out.write(0x2C); val id = ByteArray(9); dis.readFully(id); out.write(id)
                    if (id[8].toInt() and 0x80 != 0) {
                        val size = 3 * (1 shl ((id[8].toInt() and 0x07) + 1)); val lct = ByteArray(size); dis.readFully(lct); out.write(lct)
                    }
                    out.write(dis.readUnsignedByte())
                    while (true) { val sl = dis.readUnsignedByte(); out.write(sl); if (sl == 0) break; val sub = ByteArray(sl); dis.readFully(sub); out.write(sub) }
                } else if (bt == 0x3B) { out.write(0x3B); break } else break
            }
        } catch (e: Exception) {}
    }

    private fun calculateJpegMetadataSizeStreaming(input: InputStream): Long {
        var size = 0L; val dis = DataInputStream(input)
        try {
            if (dis.readUnsignedShort() != 0xFFD8) return 0
            while (true) {
                var b = dis.readUnsignedByte()
                if (b != 0xFF) continue
                var m = dis.readUnsignedByte()
                while (m == 0xFF) m = dis.readUnsignedByte()
                if (m == 0xDA) break
                val l = dis.readUnsignedShort()
                if (m in 0xE0..0xEF || m == 0xFE) size += l + 2
                dis.skipBytes(l - 2)
            }
        } catch (e: Exception) {}
        return size
    }

    private fun calculatePngMetadataSizeStreaming(input: InputStream): Long {
        var size = 0L; input.skip(8); val dis = DataInputStream(input)
        try {
            while (true) {
                val l = dis.readInt(); val t = ByteArray(4); dis.readFully(t); val ts = String(t)
                if (ts !in listOf("IHDR", "PLTE", "IDAT", "IEND", "tRNS", "sRGB", "gAMA", "cHRM")) size += l + 12
                dis.skipBytes(l + 4); if (ts == "IEND") break
            }
        } catch (e: Exception) {}
        return size
    }

    private fun calculateWebpMetadataSizeStreaming(input: InputStream): Long {
        var size = 0L; input.skip(12); val dis = DataInputStream(input)
        try {
            while (true) {
                val t = ByteArray(4); dis.readFully(t); val ts = String(t)
                val l = Integer.reverseBytes(dis.readInt())
                if (ts in listOf("EXIF", "XMP ", "ICCP")) size += l + 8
                dis.skipBytes(l + (l % 2))
            }
        } catch (e: Exception) {}
        return size
    }

    private fun calculateGifMetadataSizeStreaming(uri: Uri): Long {
        var size = 0L
        try {
            context.contentResolver.openInputStream(uri)?.use { input ->
                val dis = DataInputStream(input); dis.skipBytes(13)
                // Skip Global Color Table if present
                val headBuffer = ByteArray(13); context.contentResolver.openInputStream(uri)?.use { it.read(headBuffer) }
                val lsdPacked = headBuffer[10].toInt() and 0xFF
                if (lsdPacked and 0x80 != 0) dis.skipBytes(3 * (1 shl ((lsdPacked and 0x07) + 1)))
                while (true) {
                    val bt = dis.readUnsignedByte()
                    if (bt == 0x21) {
                        val et = dis.readUnsignedByte(); var isMeta = et == 0xFE || et == 0xFF
                        var blockSize = 2
                        while (true) { val sl = dis.readUnsignedByte(); blockSize += sl + 1; if (sl == 0) break; dis.skipBytes(sl) }
                        if (isMeta) size += blockSize
                    } else if (bt == 0x2C) {
                        dis.skipBytes(9)
                        val idPacked = dis.readUnsignedByte(); if (idPacked and 0x80 != 0) dis.skipBytes(3 * (1 shl ((idPacked and 0x07) + 1)))
                        dis.readUnsignedByte(); while (true) { val sl = dis.readUnsignedByte(); if (sl == 0) break; dis.skipBytes(sl) }
                    } else break
                }
            }
        } catch (e: Exception) {}
        return size
    }

    private fun isPng(b: ByteArray) = b.size >= 8 && b[0].toInt() and 0xFF == 0x89 && b[1].toInt() and 0xFF == 0x50
    private fun isWebp(b: ByteArray) = b.size >= 12 && String(b, 0, 4) == "RIFF" && String(b, 8, 4) == "WEBP"
    private fun isGif(b: ByteArray) = b.size >= 3 && String(b, 0, 3) == "GIF"
    private fun isTiff(b: ByteArray) = b.size >= 2 && (String(b, 0, 2) == "II" || String(b, 0, 2) == "MM")

    private fun checkOrientation(uri: Uri): Boolean {
        return try {
            context.contentResolver.openInputStream(uri)?.use { 
                val exif = ExifInterface(it); val o = exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, 1)
                o != 1 && o != 0
            } ?: false
        } catch (e: Exception) { false }
    }

    private fun extractGps(exif: ExifInterface): GpsData? {
        val ll = exif.latLong ?: return null
        return GpsData(ll[0], ll[1], "%.4f, %.4f".format(ll[0], ll[1]), "https://maps.google.com/?q=${ll[0]},${ll[1]}")
    }

    private fun extractCamera(exif: ExifInterface): String? = exif.getAttribute(ExifInterface.TAG_MODEL)
    private fun extractDateTime(exif: ExifInterface): String? = exif.getAttribute(ExifInterface.TAG_DATETIME)
    private fun extractSoftware(exif: ExifInterface): String? = exif.getAttribute(ExifInterface.TAG_SOFTWARE)

    private fun extractAllTags(exif: ExifInterface): AllTags {
        val imageTags = mutableMapOf<String, String>(); val exifTags = mutableMapOf<String, String>()
        val gpsTags = mutableMapOf<String, String>(); val techTags = mutableMapOf<String, String>()
        val tags = listOf(ExifInterface.TAG_MAKE, ExifInterface.TAG_MODEL, ExifInterface.TAG_SOFTWARE, ExifInterface.TAG_DATETIME, ExifInterface.TAG_ARTIST, ExifInterface.TAG_COPYRIGHT, ExifInterface.TAG_USER_COMMENT, ExifInterface.TAG_IMAGE_DESCRIPTION, ExifInterface.TAG_EXPOSURE_TIME, ExifInterface.TAG_F_NUMBER, ExifInterface.TAG_ISO_SPEED_RATINGS, ExifInterface.TAG_FOCAL_LENGTH, ExifInterface.TAG_GPS_LATITUDE, ExifInterface.TAG_GPS_LONGITUDE, ExifInterface.TAG_ORIENTATION, ExifInterface.TAG_IMAGE_WIDTH, ExifInterface.TAG_IMAGE_LENGTH)
        tags.forEach { t -> exif.getAttribute(t)?.let { v -> when { t.startsWith("GPS") -> gpsTags[t] = v; t in listOf(ExifInterface.TAG_MAKE, ExifInterface.TAG_MODEL, ExifInterface.TAG_SOFTWARE, ExifInterface.TAG_DATETIME) -> imageTags[t] = v; t in listOf(ExifInterface.TAG_IMAGE_WIDTH, ExifInterface.TAG_IMAGE_LENGTH, ExifInterface.TAG_ORIENTATION) -> techTags[t] = v; else -> exifTags[t] = v } } }
        return AllTags(imageTags, exifTags, gpsTags, techTags)
    }

    suspend fun saveToGallery(uri: Uri, originalName: String, mimeType: String): Uri? = withContext(Dispatchers.IO) {
        try {
            val resolver = context.contentResolver
            val inputStream = resolver.openInputStream(uri) ?: return@withContext null
            val values = ContentValues().apply { put(MediaStore.Images.Media.DISPLAY_NAME, "purged_$originalName"); put(MediaStore.Images.Media.MIME_TYPE, mimeType); if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) { put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/MetaPurge"); put(MediaStore.Images.Media.IS_PENDING, 1) } }
            val outUri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values) ?: return@withContext null
            resolver.openOutputStream(outUri)?.use { inputStream.copyTo(it) }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) { values.clear(); values.put(MediaStore.Images.Media.IS_PENDING, 0); resolver.update(outUri, values, null, null) }
            outUri
        } catch (e: Exception) { null }
    }
}
