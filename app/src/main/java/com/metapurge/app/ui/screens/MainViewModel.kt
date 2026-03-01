package com.metapurge.app.ui.screens

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.metapurge.app.data.repository.MetadataRepository
import com.metapurge.app.data.repository.StatsRepository
import com.metapurge.app.domain.model.ImageItem
import com.metapurge.app.domain.model.Stats
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.UUID

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val metadataRepository = MetadataRepository(application)
    private val statsRepository = StatsRepository(application)

    private val _images = MutableStateFlow<List<ImageItem>>(emptyList())
    val images: StateFlow<List<ImageItem>> = _images.asStateFlow()

    private val _stats = MutableStateFlow(Stats())
    val stats: StateFlow<Stats> = _stats.asStateFlow()

    private val _isProcessing = MutableStateFlow(false)
    val isProcessing: StateFlow<Boolean> = _isProcessing.asStateFlow()

    private val _toast = MutableStateFlow<String?>(null)
    val toast: StateFlow<String?> = _toast.asStateFlow()

    init {
        viewModelScope.launch {
            statsRepository.stats.collect { stats ->
                _stats.value = stats
            }
        }
    }

    fun processUris(uris: List<Uri>) {
        viewModelScope.launch {
            _isProcessing.value = true
            val newImages = mutableListOf<ImageItem>()

            for (uri in uris) {
                val context = getApplication<Application>()
                val cursor = context.contentResolver.query(uri, null, null, null, null)
                var name = "image_${System.currentTimeMillis()}.jpg"
                var size = 0L

                cursor?.use {
                    if (it.moveToFirst()) {
                        val nameIndex = it.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                        val sizeIndex = it.getColumnIndex(android.provider.OpenableColumns.SIZE)
                        if (nameIndex >= 0) name = it.getString(nameIndex) ?: name
                        if (sizeIndex >= 0) size = it.getLong(sizeIndex)
                    }
                }

                val metadata = metadataRepository.readMetadata(uri)
                val item = ImageItem(
                    id = UUID.randomUUID().toString(),
                    uri = uri.toString(),
                    name = name,
                    size = size,
                    metadata = metadata
                )
                newImages.add(item)
            }

            _images.value = newImages
            _isProcessing.value = false
        }
    }

    fun purgeImage(imageId: String) {
        viewModelScope.launch {
            val index = _images.value.indexOfFirst { it.id == imageId }
            if (index == -1) return@launch

            val image = _images.value[index]
            
            if (image.metadata?.hasExif != true) {
                showToast("No metadata to purge")
                return@launch
            }
            
            val cleanedUri = metadataRepository.purgeMetadata(Uri.parse(image.uri), image.name)

            if (cleanedUri != null) {
                val metadataSize = image.metadata?.metadataSize ?: 0
                val hasGps = image.metadata?.gps != null

                _images.value = _images.value.map {
                    if (it.id == imageId) {
                        it.copy(isPurged = true, cleanedUri = cleanedUri.toString())
                    } else it
                }

                statsRepository.incrementStats(1, metadataSize, if (hasGps) 1 else 0)
                showToast("Metadata removed successfully!")
            } else {
                showToast("Error: Could not process image. Try again.")
            }
        }
    }

    fun purgeAll() {
        viewModelScope.launch {
            _isProcessing.value = true
            var totalDataRemoved = 0L
            var totalGpsFound = 0

            for (image in _images.value) {
                if (!image.isPurged && image.metadata?.hasExif == true) {
                    val cleanedUri = metadataRepository.purgeMetadata(Uri.parse(image.uri), image.name)
                    if (cleanedUri != null) {
                        totalDataRemoved += image.metadata.metadataSize
                        if (image.metadata.gps != null) totalGpsFound++

                        _images.value = _images.value.map {
                            if (it.id == image.id) {
                                it.copy(isPurged = true, cleanedUri = cleanedUri.toString())
                            } else it
                        }
                    }
                }
            }

            if (totalDataRemoved > 0 || totalGpsFound > 0) {
                statsRepository.incrementStats(
                    _images.value.count { it.isPurged },
                    totalDataRemoved,
                    totalGpsFound
                )
                showToast("All metadata purged!")
            }

            _isProcessing.value = false
        }
    }

    fun clearAll() {
        _images.value = emptyList()
    }

    fun dismissToast() {
        _toast.value = null
    }

    private fun showToast(message: String) {
        _toast.value = message
    }

    fun formatBytes(bytes: Long): String {
        return when {
            bytes < 1024 -> "$bytes B"
            bytes < 1024 * 1024 -> "%.1f KB".format(bytes / 1024.0)
            else -> "%.1f MB".format(bytes / (1024.0 * 1024.0))
        }
    }
}
