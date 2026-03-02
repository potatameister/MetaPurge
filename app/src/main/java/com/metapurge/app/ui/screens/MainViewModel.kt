package com.metapurge.app.ui.screens

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.metapurge.app.data.repository.MetadataRepository
import com.metapurge.app.data.repository.StatsRepository
import com.metapurge.app.domain.model.ImageItem
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.util.*

class MainViewModel(
    private val context: Context,
    private val metadataRepository: MetadataRepository,
    private val statsRepository: StatsRepository
) : ViewModel() {

    private val _images = MutableStateFlow<List<ImageItem>>(emptyList())
    val images: StateFlow<List<ImageItem>> = _images

    private val _isProcessing = MutableStateFlow(false)
    val isProcessing: StateFlow<Boolean> = _isProcessing

    private val _toast = MutableStateFlow<String?>(null)
    val toast: StateFlow<String?> = _toast

    fun processUris(uris: List<Uri>) {
        val sessionId = System.currentTimeMillis()
        viewModelScope.launch {
            _isProcessing.value = true
            uris.forEach { uri ->
                val fileName = getFileName(uri) ?: "image_${System.currentTimeMillis()}.jpg"
                val size = getFileSize(uri)
                val metadata = metadataRepository.readMetadata(uri)
                val newItem = ImageItem(
                    id = UUID.randomUUID().toString(),
                    uri = uri.toString(),
                    name = fileName,
                    size = size,
                    metadata = metadata,
                    sessionId = sessionId
                )
                
                val current = _images.value.toMutableList()
                current.add(0, newItem)
                _images.value = current
            }
            _isProcessing.value = false
        }
    }

    fun purgeImage(id: String) {
        viewModelScope.launch {
            val image = _images.value.find { it.id == id } ?: return@launch
            val cleanUri = metadataRepository.purgeMetadata(Uri.parse(image.uri), image.name)
            if (cleanUri != null) {
                _images.value = _images.value.map { if (it.id == id) it.copy(isPurged = true, cleanedUri = cleanUri.toString()) else it }
                if (image.metadata?.hasExif == true) {
                    statsRepository.incrementStats(1, image.metadata.metadataSize, if (image.metadata.gps != null) 1 else 0)
                }
            }
        }
    }

    fun purgeAll() {
        viewModelScope.launch {
            _isProcessing.value = true
            val imagesToPurge = _images.value.filter { !it.isPurged && it.metadata?.hasExif == true }
            imagesToPurge.forEach { image ->
                val cleanUri = metadataRepository.purgeMetadata(Uri.parse(image.uri), image.name)
                if (cleanUri != null) {
                    _images.value = _images.value.map { if (it.id == image.id) it.copy(isPurged = true, cleanedUri = cleanUri.toString()) else it }
                    statsRepository.incrementStats(1, image.metadata?.metadataSize ?: 0, if (image.metadata?.gps != null) 1 else 0)
                }
            }
            _isProcessing.value = false
        }
    }

    fun saveSessionToGallery(sessionId: Long) {
        viewModelScope.launch {
            val sessionImages = _images.value.filter { it.sessionId == sessionId && it.isPurged }
            var saved = 0
            sessionImages.forEach { image ->
                val uri = image.cleanedUri?.let { Uri.parse(it) } ?: return@forEach
                val mime = when {
                    image.name.lowercase().endsWith(".png") -> "image/png"
                    image.name.lowercase().endsWith(".webp") -> "image/webp"
                    image.name.lowercase().endsWith(".gif") -> "image/gif"
                    else -> "image/jpeg"
                }
                if (metadataRepository.saveToGallery(uri, image.name, mime) != null) saved++
            }
            if (saved > 0) _toast.value = "Saved $saved images to Pictures/MetaPurge"
        }
    }

    fun saveImageToGallery(id: String) {
        viewModelScope.launch {
            val image = _images.value.find { it.id == id } ?: return@launch
            val uri = image.cleanedUri?.let { Uri.parse(it) } ?: return@launch
            val mime = when {
                image.name.lowercase().endsWith(".png") -> "image/png"
                image.name.lowercase().endsWith(".webp") -> "image/webp"
                image.name.lowercase().endsWith(".gif") -> "image/gif"
                else -> "image/jpeg"
            }
            if (metadataRepository.saveToGallery(uri, image.name, mime) != null) {
                _toast.value = "Saved to Pictures/MetaPurge"
            }
        }
    }

    fun saveAllCleanedToGallery() {
        viewModelScope.launch {
            val cleanedImages = _images.value.filter { it.isPurged }
            if (cleanedImages.isEmpty()) return@launch
            
            var saved = 0
            cleanedImages.forEach { image ->
                val uri = image.cleanedUri?.let { Uri.parse(it) } ?: return@forEach
                val mime = when {
                    image.name.lowercase().endsWith(".png") -> "image/png"
                    image.name.lowercase().endsWith(".webp") -> "image/webp"
                    image.name.lowercase().endsWith(".gif") -> "image/gif"
                    else -> "image/jpeg"
                }
                if (metadataRepository.saveToGallery(uri, image.name, mime) != null) saved++
            }
            if (saved > 0) {
                _toast.value = "Saved $saved images to Pictures/MetaPurge"
            }
        }
    }

    fun removeImage(id: String) { _images.value = _images.value.filter { it.id != id } }
    fun clearAll() { _images.value = emptyList() }
    fun dismissToast() { _toast.value = null }

    fun formatBytes(bytes: Long): String {
        if (bytes <= 0) return "0 B"
        val units = listOf("B", "KB", "MB", "GB", "TB")
        val digitGroup = (Math.log10(bytes.toDouble()) / Math.log10(1024.0)).toInt()
        return "%.1f %s".format(bytes / Math.pow(1024.0, digitGroup.toDouble()), units[digitGroup])
    }

    private fun getFileName(uri: Uri): String? {
        var res: String? = null
        if (uri.scheme == "content") {
            context.contentResolver.query(uri, null, null, null, null)?.use {
                if (it.moveToFirst()) {
                    val index = it.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (index != -1) res = it.getString(index)
                }
            }
        }
        return res ?: uri.path?.substringAfterLast('/')
    }

    private fun getFileSize(uri: Uri): Long {
        var size = 0L
        if (uri.scheme == "content") {
            context.contentResolver.query(uri, null, null, null, null)?.use {
                if (it.moveToFirst()) {
                    val index = it.getColumnIndex(OpenableColumns.SIZE)
                    if (index != -1) size = it.getLong(index)
                }
            }
        }
        return size
    }

    class Factory(
        private val context: Context,
        private val metadataRepository: MetadataRepository,
        private val statsRepository: StatsRepository
    ) : ViewModelProvider.Factory {
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return MainViewModel(context, metadataRepository, statsRepository) as T
        }
    }
}
