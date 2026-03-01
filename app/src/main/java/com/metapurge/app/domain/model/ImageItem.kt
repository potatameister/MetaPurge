package com.metapurge.app.domain.model

data class ImageItem(
    val id: String,
    val uri: String,
    val name: String,
    val size: Long,
    val metadata: ImageMetadata?,
    val isPurged: Boolean = false,
    val cleanedUri: String? = null,
    val sessionId: Long = 0L
)

data class ImageMetadata(
    val hasExif: Boolean,
    val metadataSize: Long,
    val gps: GpsData?,
    val camera: String?,
    val dateTime: String?,
    val software: String?,
    val allTags: AllTags
)

data class GpsData(
    val latitude: Double,
    val longitude: Double,
    val display: String,
    val mapUrl: String
)

data class AllTags(
    val image: Map<String, String>,
    val exif: Map<String, String>,
    val gps: Map<String, String>,
    val technical: Map<String, String> = emptyMap()
)

data class Stats(
    val filesPurged: Int = 0,
    val dataRemoved: Long = 0,
    val gpsFound: Int = 0
)
