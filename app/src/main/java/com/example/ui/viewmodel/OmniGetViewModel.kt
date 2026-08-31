package com.example.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.OmniGetApplication
import com.example.data.local.DownloadEntity
import com.example.data.model.FileType
import com.example.data.model.MediaInspectResult
import com.example.data.model.MediaQualityOption
import com.example.data.model.NetworkSettings
import com.example.engine.UrlInspector
import com.example.service.NotificationHelper
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class OmniGetViewModel(application: Application) : AndroidViewModel(application) {

    private val app = application as OmniGetApplication
    private val repository = app.repository
    private val downloadManager = app.downloadManager

    // UI Input State
    private val _urlInput = MutableStateFlow("")
    val urlInput: StateFlow<String> = _urlInput.asStateFlow()

    private val _isInspecting = MutableStateFlow(false)
    val isInspecting: StateFlow<Boolean> = _isInspecting.asStateFlow()

    private val _inspectResult = MutableStateFlow<MediaInspectResult?>(null)
    val inspectResult: StateFlow<MediaInspectResult?> = _inspectResult.asStateFlow()

    private val _inspectError = MutableStateFlow<String?>(null)
    val inspectError: StateFlow<String?> = _inspectError.asStateFlow()

    private val _selectedThreads = MutableStateFlow(4)
    val selectedThreads: StateFlow<Int> = _selectedThreads.asStateFlow()

    // Search and Category Filters for Library
    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _selectedCategoryFilter = MutableStateFlow<FileType?>(null)
    val selectedCategoryFilter: StateFlow<FileType?> = _selectedCategoryFilter.asStateFlow()

    // Preview / Playback Modal
    private val _activeMediaPreview = MutableStateFlow<DownloadEntity?>(null)
    val activeMediaPreview: StateFlow<DownloadEntity?> = _activeMediaPreview.asStateFlow()

    // Quick Action Snack message
    private val _snackMessage = MutableStateFlow<String?>(null)
    val snackMessage: StateFlow<String?> = _snackMessage.asStateFlow()

    // Bumped whenever a URL is shared into the app from another app, so the UI
    // can switch to the Downloader tab even if it was showing a different tab.
    private val _navigateToDownloaderEvent = MutableStateFlow(0)
    val navigateToDownloaderEvent: StateFlow<Int> = _navigateToDownloaderEvent.asStateFlow()

    // Settings
    val settings: StateFlow<NetworkSettings> = repository.settingsFlow

    // Downloads Streams
    val allDownloads: StateFlow<List<DownloadEntity>> = repository.allDownloads
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val activeDownloads: StateFlow<List<DownloadEntity>> = repository.activeDownloads
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val completedDownloads: StateFlow<List<DownloadEntity>> = repository.completedDownloads
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Filtered Library History
    val filteredLibrary: StateFlow<List<DownloadEntity>> = combine(
        allDownloads,
        _searchQuery,
        _selectedCategoryFilter
    ) { list, query, category ->
        list.filter { item ->
            val matchesQuery = query.isBlank() || item.fileName.contains(query, ignoreCase = true) || item.url.contains(query, ignoreCase = true)
            val matchesCategory = category == null || item.fileType == category
            matchesQuery && matchesCategory
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Overall aggregate download speed
    val totalSpeedFormatted: StateFlow<String> = activeDownloads.combine(_urlInput) { activeList, _ ->
        val totalBytesPerSec = activeList.filter { it.status.name == "DOWNLOADING" }.sumOf { it.speedBytesPerSec }
        NotificationHelper.formatSpeed(totalBytesPerSec)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "0 KB/s")

    fun onUrlInputChanged(newUrl: String) {
        _urlInput.value = newUrl
        _inspectError.value = null
    }

    fun setThreadsCount(threads: Int) {
        _selectedThreads.value = threads.coerceIn(1, 8)
    }

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun setCategoryFilter(category: FileType?) {
        _selectedCategoryFilter.value = category
    }

    fun openMediaPreview(item: DownloadEntity) {
        _activeMediaPreview.value = item
    }

    fun closeMediaPreview() {
        _activeMediaPreview.value = null
    }

    fun clearSnackMessage() {
        _snackMessage.value = null
    }

    fun showSnack(msg: String) {
        _snackMessage.value = msg
    }

    fun isWifiActive(): Boolean = downloadManager.isWifiConnected()

    fun inspectUrl(targetUrl: String? = null) {
        val url = targetUrl ?: _urlInput.value.trim()
        if (url.isBlank()) {
            _inspectError.value = "Lütfen geçerli bir indirme veya medya bağlantısı girin"
            return
        }

        viewModelScope.launch {
            _isInspecting.value = true
            _inspectError.value = null
            _inspectResult.value = null
            try {
                val result = UrlInspector.inspectUrl(url)
                _inspectResult.value = result
            } catch (e: Exception) {
                _inspectError.value = "Bağlantı analiz edilemedi: ${e.localizedMessage ?: e.message}"
            } finally {
                _isInspecting.value = false
            }
        }
    }

    fun handleSharedUrl(url: String) {
        onUrlInputChanged(url)
        inspectUrl(url)
        _navigateToDownloaderEvent.value += 1
    }

    fun clearInspectResult() {
        _inspectResult.value = null
        _inspectError.value = null
    }

    fun startDownloadWithQuality(result: MediaInspectResult, option: MediaQualityOption) {
        val finalFileName = if (option.isAudioOnly && !result.title.endsWith(".mp3", ignoreCase = true)) {
            "${result.title.substringBeforeLast('.')}.mp3"
        } else if (!result.title.contains('.')) {
            "${result.title}.${option.format.lowercase()}"
        } else {
            result.title
        }

        downloadManager.enqueueDownload(
            url = option.directDownloadUrl,
            fileName = finalFileName,
            fileType = if (option.isAudioOnly) FileType.AUDIO else result.fileType,
            totalBytes = option.estimatedBytes,
            mimeType = result.mimeType,
            thumbnailUrl = result.thumbnailUrl,
            threadsCount = _selectedThreads.value,
            isCourseBundle = result.isCourseBundle
        )

        _inspectResult.value = null
        _urlInput.value = ""
        _snackMessage.value = "İndirme sıraya eklendi: $finalFileName"
    }

    fun startBatchCourseDownload(result: MediaInspectResult) {
        if (result.courseLessons.isNotEmpty()) {
            val batchList = result.courseLessons.map { lesson ->
                Pair(lesson.downloadUrl, "${lesson.title}.mp4")
            }
            downloadManager.enqueueBatchDownloads(
                items = batchList,
                fileType = FileType.COURSE,
                isCourseBundle = true
            )
            _inspectResult.value = null
            _urlInput.value = ""
            _snackMessage.value = "${batchList.size} kurs dersi indirme kuyruğuna eklendi!"
        } else {
            val firstOption = result.qualityOptions.firstOrNull()
            if (firstOption != null) {
                startDownloadWithQuality(result, firstOption)
            }
        }
    }

    fun startQuickPresetDownload(preset: MediaInspectResult) {
        val option = preset.qualityOptions.firstOrNull() ?: return
        startDownloadWithQuality(preset, option)
    }

    fun pauseDownload(id: Long) {
        downloadManager.pauseDownload(id)
        _snackMessage.value = "İndirme duraklatıldı"
    }

    fun resumeDownload(id: Long) {
        downloadManager.resumeDownload(id)
        _snackMessage.value = "İndirme devam ettiriliyor"
    }

    fun cancelDownload(id: Long) {
        downloadManager.cancelDownload(id)
        _snackMessage.value = "İndirme iptal edildi"
    }

    fun retryDownload(id: Long) {
        downloadManager.retryDownload(id)
        _snackMessage.value = "Yeniden deneniyor..."
    }

    fun deleteDownload(id: Long, deleteFileFromDisk: Boolean = true) {
        downloadManager.deleteDownload(id, deleteFileFromDisk)
        _snackMessage.value = if (deleteFileFromDisk) "Dosya ve kayıt silindi" else "Kayıt geçmişten silindi"
    }

    fun pauseAll() {
        downloadManager.pauseAll()
        _snackMessage.value = "Tüm indirmeler duraklatıldı"
    }

    fun resumeAll() {
        downloadManager.resumeAll()
        _snackMessage.value = "Tüm indirmeler devam ettiriliyor"
    }

    fun clearCompleted() {
        downloadManager.clearCompleted()
        _snackMessage.value = "Tamamlanan indirmeler temizlendi"
    }

    fun updateSettings(newSettings: NetworkSettings) {
        repository.updateSettings(newSettings)
        _snackMessage.value = "Ağ ve indirme ayarları güncellendi"
    }
}
