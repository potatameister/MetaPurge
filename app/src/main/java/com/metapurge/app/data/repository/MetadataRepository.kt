package com.metapurge.app.data.repository

import android.content.ContentResolver
import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import androidx.exifinterface.media.ExifInterface
import com.metapurge.app.domain.model.AllTags
import com.metapurge.app.domain.model.GpsData
import com.metapurge.app.domain.model.ImageMetadata
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.io.OutputStream

class MetadataRepository(private val context: Context) {

    suspend fun readMetadata(uri: Uri): ImageMetadata? = withContext(Dispatchers.IO) {
        try {
            val contentResolver = context.contentResolver
            val inputStream = contentResolver.openInputStream(uri) ?: return@withContext null

            val fullBytes = inputStream.readBytes()
            val metadataSize = calculateMetadataSize(fullBytes)
            val hasExif = metadataSize > 0

            inputStream.close()

            val freshStream = contentResolver.openInputStream(uri) ?: return@withContext null
            val exif = ExifInterface(freshStream)
            freshStream.close()

            val gps = extractGps(exif)
            val camera = extractCamera(exif)
            val dateTime = extractDateTime(exif)
            val software = extractSoftware(exif)
            val allTags = extractAllTags(exif)

            ImageMetadata(
                hasExif = hasExif,
                metadataSize = metadataSize,
                gps = gps,
                camera = camera,
                dateTime = dateTime,
                software = software,
                allTags = allTags
            )
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    private fun calculateMetadataSize(bytes: ByteArray): Long {
        var size: Long = 0
        var i = 0
        while (i < bytes.size - 1) {
            if (bytes[i].toInt() and 0xFF == 0xFF) {
                val marker = bytes[i + 1].toInt() and 0xFF
                if (marker >= 0xE0 && marker <= 0xEF || marker == 0xFE) {
                    if (i + 3 < bytes.size) {
                        val len = ((bytes[i + 2].toInt() and 0xFF) shl 8) or (bytes[i + 3].toInt() and 0xFF)
                        size += len + 2
                        i += 2 + len
                    } else {
                        i++
                    }
                } else if (marker == 0xDA) {
                    break
                } else {
                    i += 2
                }
            } else {
                i++
            }
        }
        return size
    }

    private fun extractGps(exif: ExifInterface): GpsData? {
        val latLong = exif.latLong ?: return null
        val lat = latLong[0]
        val lon = latLong[1]
        return GpsData(
            latitude = lat,
            longitude = lon,
            display = "%.6f°%s, %.6f°%s".format(
                kotlin.math.abs(lat), if (lat >= 0) "N" else "S",
                kotlin.math.abs(lon), if (lon >= 0) "E" else "W"
            ),
            mapUrl = "https://www.google.com/maps?q=$lat,$lon"
        )
    }

    private fun extractCamera(exif: ExifInterface): String? {
        val make = exif.getAttribute(ExifInterface.TAG_MAKE)
        val model = exif.getAttribute(ExifInterface.TAG_MODEL)
        return listOfNotNull(make, model).joinToString(" ").takeIf { it.isNotBlank() }
    }

    private fun extractDateTime(exif: ExifInterface): String? {
        return exif.getAttribute(ExifInterface.TAG_DATETIME_ORIGINAL)
            ?: exif.getAttribute(ExifInterface.TAG_DATETIME)
    }

    private fun extractSoftware(exif: ExifInterface): String? {
        return exif.getAttribute(ExifInterface.TAG_SOFTWARE)
    }

    private fun extractAllTags(exif: ExifInterface): AllTags {
        val imageTags = mutableMapOf<String, String>()
        val exifTags = mutableMapOf<String, String>()
        val gpsTags = mutableMapOf<String, String>()

        val knownTags = listOf(
            "Make", "Model", "Orientation", "Software", "DateTime",
            "Artist", "Copyright", "UserComment", "ImageDescription",
            "ExposureTime", "DateTimeOriginal", "DateTimeDigitized", 
            "Flash", "FocalLength", "WhiteBalance", "ExposureMode", 
            "ColorSpace", "FNumber", "ISOSpeedRatings", "LensModel",
            "LensMake", "ApertureValue", "ShutterSpeedValue",
            "GPSLatitude", "GPSLatitudeRef", "GPSLongitude", "GPSLongitudeRef", 
            "GPSAltitude", "GPSAltitudeRef", "GPSTimeStamp", "GPSDateStamp",
            "GPSProcessingMethod"
        )

        knownTags.forEach { tag ->
            exif.getAttribute(tag)?.let { value ->
                when {
                    tag.startsWith("GPS") -> gpsTags[tag] = value
                    tag in listOf("ExposureTime", "DateTimeOriginal", "DateTimeDigitized", "Flash", 
                                 "FocalLength", "WhiteBalance", "ExposureMode", "ColorSpace",
                                 "FNumber", "ISOSpeedRatings", "LensModel", "LensMake",
                                 "ApertureValue", "ShutterSpeedValue") -> exifTags[tag] = value
                    else -> imageTags[tag] = value
                }
            }
        }

        return AllTags(imageTags, exifTags, gpsTags)
    }

    suspend fun purgeMetadata(uri: Uri, originalName: String): Uri? = withContext(Dispatchers.IO) {
        try {
            val contentResolver = context.contentResolver
            val inputStream = contentResolver.openInputStream(uri) ?: run {
                Log.e("MetaPurge", "Cannot open input stream for: $uri")
                return@withContext null
            }

            val fullBytes = inputStream.readBytes()
            inputStream.close()

            if (fullBytes.isEmpty()) {
                Log.e("MetaPurge", "Empty bytes read from: $uri")
                return@withContext null
            }

            val cleanBytes = purgeMetadataBytes(fullBytes)
            
            if (cleanBytes.size < fullBytes.size / 2) {
                Log.w("MetaPurge", "Clean bytes suspiciously small: ${cleanBytes.size} vs ${fullBytes.size}")
            }

            val outputUri = saveCleanImage(cleanBytes, originalName)
            outputUri
        } catch (e: Exception) {
            Log.e("MetaPurge", "Error purging metadata", e)
            null
        }
    }

    private fun purgeMetadataBytes(input: ByteArray): ByteArray {
        if (input.size < 2 || input[0].toInt() and 0xFF != 0xFF || input[1].toInt() and 0xFF != 0xD8) {
            return input
        }

        val output = ByteArrayOutputStream(input.size)
        output.write(0xFF)
        output.write(0xD8)

        var i = 2
        while (i < input.size - 1) {
            if (input[i].toInt() and 0xFF == 0xFF) {
                val marker = input[i + 1].toInt() and 0xFF

                if (marker >= 0xE0 && marker <= 0xEF || marker == 0xFE) {
                    if (i + 3 < input.size) {
                        val len = ((input[i + 2].toInt() and 0xFF) shl 8) or (input[i + 3].toInt() and 0xFF)
                        i += 2 + len
                        continue
                    }
                }

                if (marker == 0xDA) {
                    output.write(input, i, input.size - i)
                    break
                }

                if (marker == 0xD8) {
                    i += 2
                } else if (marker == 0xD9) {
                    output.write(0xFF)
                    output.write(0xD9)
                    i += 2
                } else {
                    if (i + 3 < input.size) {
                        val len = ((input[i + 2].toInt() and 0xFF) shl 8) or (input[i + 3].toInt() and 0xFF)
                        if (len > 0 && len < input.size - i - 2) {
                            output.write(input, i, len + 2)
                            i += len + 2
                        } else {
                            output.write(0xFF)
                            output.write(input[i + 1].toInt() and 0xFF)
                            i += 2
                        }
                    } else {
                        i++
                    }
                }
            } else {
                output.write(input[i].toInt() and 0xFF)
                i++
            }
        }

        val result = output.toByteArray()
        return if (result.size < 10) input else result
    }

    private suspend fun saveCleanImage(bytes: ByteArray, originalName: String): Uri? = withContext(Dispatchers.IO) {
        try {
            val baseName = originalName.substringBeforeLast(".")
            val extension = originalName.substringAfterLast(".", "jpg")
            val cleanName = "purged_${baseName}.$extension"

            val contentValues = ContentValues().apply {
                put(MediaStore.Images.Media.DISPLAY_NAME, cleanName)
                put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/MetaPurge")
                    put(MediaStore.Images.Media.IS_PENDING, 1)
                }
            }

            val resolver = context.contentResolver
            val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues)
                ?: run {
                    Log.e("MetaPurge", "Failed to insert MediaStore entry")
                    return@withContext null
                }

            resolver.openOutputStream(uri)?.use { outputStream ->
                outputStream.write(bytes)
            } ?: run {
                Log.e("MetaPurge", "Failed to open output stream")
                resolver.delete(uri, null, null)
                return@withContext null
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                contentValues.clear()
                contentValues.put(MediaStore.Images.Media.IS_PENDING, 0)
                resolver.update(uri, contentValues, null, null)
            }
            
            Log.d("MetaPurge", "Saved clean image: $uri")
            uri
        } catch (e: Exception) {
            Log.e("MetaPurge", "Error saving clean image", e)
            null
        }
    }
}
