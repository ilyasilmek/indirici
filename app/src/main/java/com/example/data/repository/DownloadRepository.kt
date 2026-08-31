package com.example.data.repository

import android.content.Context
import android.content.SharedPreferences
import com.example.data.local.DownloadDao
import com.example.data.local.DownloadEntity
import com.example.data.model.DownloadStatus
import com.example.data.model.FileType
import com.example.data.model.NetworkSettings
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

class DownloadRepository(
    private val downloadDao: DownloadDao,
    context: Context
) {
    private val prefs: SharedPreferences = context.getSharedPreferences("omniget_settings", Context.MODE_PRIVATE)

    private val _settingsFlow = MutableStateFlow(loadSettings())
    val settingsFlow = _settingsFlow.asStateFlow()

    val allDownloads: Flow<List<DownloadEntity>> = downloadDao.getAllDownloads()
    val activeDownloads: Flow<List<DownloadEntity>> = downloadDao.getActiveDownloads()
    val completedDownloads: Flow<List<DownloadEntity>> = downloadDao.getCompletedDownloads()

    private fun loadSettings(): NetworkSettings {
        return NetworkSettings(
            smartNetworkMode = prefs.getBoolean("smartNetworkMode", true),
            wifiOnly = prefs.getBoolean("wifiOnly", false),
            maxConcurrentDownloads = prefs.getInt("maxConcurrentDownloads", 3),
            connectionThreadsPerFile = prefs.getInt("connectionThreadsPerFile", 4),
            speedLimitKbps = prefs.getInt("speedLimitKbps", 0),
            autoResumeOnWifi = prefs.getBoolean("autoResumeOnWifi", true),
            notificationSound = prefs.getBoolean("notificationSound", true),
            notificationVibration = prefs.getBoolean("notificationVibration", true),
            autoCategorizeFiles = prefs.getBoolean("autoCategorizeFiles", true)
        )
    }

    fun updateSettings(newSettings: NetworkSettings) {
        prefs.edit().apply {
            putBoolean("smartNetworkMode", newSettings.smartNetworkMode)
            putBoolean("wifiOnly", newSettings.wifiOnly)
            putInt("maxConcurrentDownloads", newSettings.maxConcurrentDownloads)
            putInt("connectionThreadsPerFile", newSettings.connectionThreadsPerFile)
            putInt("speedLimitKbps", newSettings.speedLimitKbps)
            putBoolean("autoResumeOnWifi", newSettings.autoResumeOnWifi)
            putBoolean("notificationSound", newSettings.notificationSound)
            putBoolean("notificationVibration", newSettings.notificationVibration)
            putBoolean("autoCategorizeFiles", newSettings.autoCategorizeFiles)
            apply()
        }
        _settingsFlow.value = newSettings
    }

    suspend fun insertDownload(item: DownloadEntity): Long = downloadDao.insert(item)

    suspend fun insertDownloads(items: List<DownloadEntity>): List<Long> = downloadDao.insertAll(items)

    suspend fun updateDownload(item: DownloadEntity) = downloadDao.update(item)

    suspend fun updateStatus(id: Long, status: DownloadStatus) = downloadDao.updateStatus(id, status)

    suspend fun updateProgress(id: Long, downloaded: Long, total: Long, speed: Long, eta: Long, status: DownloadStatus) =
        downloadDao.updateProgress(id, downloaded, total, speed, eta, status)

    suspend fun markCompleted(id: Long, completedAt: Long, filePath: String) =
        downloadDao.markCompleted(id, completedAt, filePath)

    suspend fun markFailed(id: Long, error: String) = downloadDao.markFailed(id, error)

    suspend fun deleteDownload(id: Long) = downloadDao.deleteById(id)

    suspend fun clearCompleted() = downloadDao.clearCompleted()

    suspend fun clearAll() = downloadDao.clearAll()

    suspend fun getDownloadById(id: Long): DownloadEntity? = downloadDao.getDownloadByIdDirect(id)

    suspend fun getQueuedDownloads(): List<DownloadEntity> = downloadDao.getQueuedDownloadsDirect()

    suspend fun getCurrentlyDownloading(): List<DownloadEntity> = downloadDao.getCurrentlyDownloadingDirect()
}
