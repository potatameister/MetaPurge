package com.metapurge.app.data.repository

import android.content.ContentResolver
import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
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
            "Artist", "Copyright", "ExposureTime", "DateTimeOriginal",
            "DateTimeDigitized", "Flash", "FocalLength", "WhiteBalance",
            "ExposureMode", "ColorSpace", "GPSLatitude", "GPSLatitudeRef",
            "GPSLongitude", "GPSLongitudeRef", "GPSAltitude", "GPSAltitudeRef",
            "GPSTimeStamp", "GPSDateStamp"
        )

        knownTags.forEach { tag ->
            exif.getAttribute(tag)?.let { value ->
                when {
                    tag.startsWith("GPS") -> gpsTags[tag] = value
                    tag in listOf("ExposureTime", "DateTimeOriginal", "DateTimeDigitized", "Flash", 
                                 "FocalLength", "WhiteBalance", "ExposureMode", "ColorSpace") -> exifTags[tag] = value
                    else -> imageTags[tag] = value
                }
            }
        }

        return AllTags(imageTags, exifTags, gpsTags)
    }

    suspend fun purgeMetadata(uri: Uri, originalName: String): Uri? = withContext(Dispatchers.IO) {
        try {
            val contentResolver = context.contentResolver
            val inputStream = contentResolver.openInputStream(uri) ?: return@withContext null

            val fullBytes = inputStream.readBytes()
            inputStream.close()

            val cleanBytes = purgeMetadataBytes(fullBytes)

            val outputUri = saveCleanImage(cleanBytes, originalName)
            outputUri
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    private fun purgeMetadataBytes(input: ByteArray): ByteArray {
        val output = mutableListOf<Byte>()

        output.add(0xFF.toByte())
        output.add(0xD8.toByte())

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
                    while (i < input.size) {
                        output.add(input[i])
                        i++
                    }
                    break
                }

                if (marker == 0xD8) {
                    i += 2
                } else if (marker == 0xD9) {
                    output.add(0xFF.toByte())
                    output.add(0xD9.toByte())
                    i += 2
                } else {
                    if (i + 3 < input.size) {
                        val len = ((input[i + 2].toInt() and 0xFF) shl 8) or (input[i + 3].toInt() and 0xFF)
                        if (len > 0 && len < input.size - i - 2) {
                            for (j in 0 until len + 2) {
                                output.add(input[i + j])
                            }
                            i += len + 2
                        } else {
                            output.add(input[i])
                            output.add(input[i + 1])
                            i += 2
                        }
                    } else {
                        i++
                    }
                }
            } else {
                output.add(input[i])
                i++
            }
        }

        return output.toByteArray()
    }

    private suspend fun saveCleanImage(bytes: ByteArray, originalName: String): Uri? = withContext(Dispatchers.IO) {
        try {
            val cleanName = "purged_$originalName"

            val contentValues = ContentValues().apply {
                put(MediaStore.Images.Media.DISPLAY_NAME, cleanName)
                put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
                    put(MediaStore.Images.Media.IS_PENDING, 1)
                }
            }

            val resolver = context.contentResolver
            val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues)
                ?: return@withContext null

            resolver.openOutputStream(uri)?.use { outputStream ->
                outputStream.write(bytes)
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                contentValues.clear()
                contentValues.put(MediaStore.Images.Media.IS_PENDING, 0)
                resolver.update(uri, contentValues, null, null)
            } else {
                ContentValues().apply {
                    put(MediaStore.Images.Media.DATA, 
                        Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS).absolutePath + "/$cleanName")
                }
            }

            uri
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
}
