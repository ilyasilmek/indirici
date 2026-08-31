package com.example.engine

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Environment
import com.example.data.local.DownloadEntity
import com.example.data.model.DownloadStatus
import com.example.data.model.FileType
import com.example.data.model.NetworkSettings
import com.example.data.repository.DownloadRepository
import com.example.service.DownloadService
import com.example.service.NotificationHelper
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.io.RandomAccessFile
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.coroutines.coroutineContext

class DownloadManager(
    private val context: Context,
    private val repository: DownloadRepository
) {
    private val managerScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val activeJobs = ConcurrentHashMap<Long, Job>()
    private val pausedFlags = ConcurrentHashMap<Long, AtomicBoolean>()
    private val downloadMutex = Mutex()

    private val httpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .followRedirects(true)
            .followSslRedirects(true)
            .build()
    }

    init {
        // Observe queued and resume if needed
        managerScope.launch {
            repository.settingsFlow.collect { settings ->
                checkAndProcessQueue()
            }
        }
    }

    fun isWifiConnected(): Boolean {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return false
        val activeNetwork = cm.activeNetwork ?: return false
        val caps = cm.getNetworkCapabilities(activeNetwork) ?: return false
        return caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) || caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)
    }

    fun isNetworkAvailable(): Boolean {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return false
        val activeNetwork = cm.activeNetwork ?: return false
        val caps = cm.getNetworkCapabilities(activeNetwork) ?: return false
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    fun enqueueDownload(
        url: String,
        fileName: String,
        fileType: FileType,
        totalBytes: Long = -1L,
        mimeType: String = "",
        thumbnailUrl: String? = null,
        threadsCount: Int = 4,
        isCourseBundle: Boolean = false,
        courseChapter: String? = null
    ): Job = managerScope.launch {
        val entity = DownloadEntity(
            url = url,
            fileName = sanitizeFileName(fileName),
            fileType = fileType,
            totalBytes = totalBytes,
            downloadedBytes = 0L,
            status = DownloadStatus.QUEUED,
            mimeType = mimeType,
            thumbnailUrl = thumbnailUrl,
            threadsCount = threadsCount,
            isCourseBundle = isCourseBundle,
            courseChapter = courseChapter,
            createdAt = System.currentTimeMillis()
        )

        val id = repository.insertDownload(entity)
        checkAndProcessQueue()
    }

    fun enqueueBatchDownloads(
        items: List<Pair<String, String>>, // URL, Title
        fileType: FileType,
        isCourseBundle: Boolean = false
    ): Job = managerScope.launch {
        val entities = items.mapIndexed { index, pair ->
            DownloadEntity(
                url = pair.first,
                fileName = sanitizeFileName(pair.second),
                fileType = fileType,
                status = DownloadStatus.QUEUED,
                isCourseBundle = isCourseBundle,
                courseChapter = if (isCourseBundle) "Modül ${index + 1}" else null,
                createdAt = System.currentTimeMillis() + index
            )
        }
        repository.insertDownloads(entities)
        checkAndProcessQueue()
    }

    fun pauseDownload(id: Long) {
        pausedFlags[id]?.set(true)
        activeJobs[id]?.cancel()
        activeJobs.remove(id)

        managerScope.launch {
            repository.updateStatus(id, DownloadStatus.PAUSED)
            checkAndProcessQueue()
        }
    }

    fun resumeDownload(id: Long) {
        pausedFlags[id]?.set(false)
        managerScope.launch {
            repository.updateStatus(id, DownloadStatus.QUEUED)
            checkAndProcessQueue()
        }
    }

    fun cancelDownload(id: Long) {
        pausedFlags[id]?.set(true)
        activeJobs[id]?.cancel()
        activeJobs.remove(id)

        managerScope.launch {
            val item = repository.getDownloadById(id)
            if (item != null && item.filePath.isNotBlank()) {
                val file = File(item.filePath)
                if (file.exists()) file.delete()
            }
            repository.updateStatus(id, DownloadStatus.CANCELLED)
            checkAndProcessQueue()
        }
    }

    fun retryDownload(id: Long) {
        managerScope.launch {
            val item = repository.getDownloadById(id) ?: return@launch
            repository.updateProgress(id, 0L, item.totalBytes, 0L, 0L, DownloadStatus.QUEUED)
            checkAndProcessQueue()
        }
    }

    fun pauseAll() {
        activeJobs.keys.forEach { id ->
            pauseDownload(id)
        }
    }

    fun resumeAll() {
        managerScope.launch {
            repository.resumeAllPaused()
            checkAndProcessQueue()
        }
    }

    fun deleteDownload(id: Long, deleteFileFromDisk: Boolean = true) {
        cancelDownload(id)
        managerScope.launch {
            val item = repository.getDownloadById(id)
            if (deleteFileFromDisk && item != null && item.filePath.isNotBlank()) {
                val file = File(item.filePath)
                if (file.exists()) file.delete()
            }
            repository.deleteDownload(id)
        }
    }

    fun clearCompleted() {
        managerScope.launch {
            repository.clearCompleted()
        }
    }

    fun checkAndProcessQueue() {
        managerScope.launch {
            downloadMutex.withLock {
                val settings = repository.settingsFlow.value

                // Check WiFi constraints
                val isWifi = isWifiConnected()
                if (settings.wifiOnly && !isWifi) {
                    return@withLock
                }

                val currentRunning = activeJobs.size
                val maxConcurrent = settings.maxConcurrentDownloads.coerceAtLeast(1)

                if (currentRunning >= maxConcurrent) {
                    return@withLock
                }

                val slotsAvailable = maxConcurrent - currentRunning
                val queuedItems = repository.getQueuedDownloads()

                val toStart = queuedItems.take(slotsAvailable)
                for (item in toStart) {
                    if (!activeJobs.containsKey(item.id)) {
                        startDownloadExecution(item.id)
                    }
                }
            }
        }
    }

    fun startDownloadExecution(downloadId: Long) {
        if (activeJobs.containsKey(downloadId)) return

        val job = managerScope.launch {
            executeDownloadInternal(downloadId)
        }
        activeJobs[downloadId] = job
        DownloadService.startService(context)
    }

    private suspend fun executeDownloadInternal(downloadId: Long) {
        val item = repository.getDownloadById(downloadId) ?: return
        val settings = repository.settingsFlow.value

        // Check network constraints
        if (settings.wifiOnly && !isWifiConnected()) {
            repository.markFailed(downloadId, "Yalnızca Wi-Fi modunda: Wi-Fi bağlantısı bekleniyor")
            return
        }

        pausedFlags[downloadId] = AtomicBoolean(false)
        repository.updateStatus(downloadId, DownloadStatus.DOWNLOADING)

        val targetDir = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS) ?: context.filesDir
        val targetFile = File(targetDir, item.fileName)
        val tempFile = File(targetDir, "${item.fileName}.omniget_tmp")

        var totalBytes = item.totalBytes
        var downloadedBytes = if (tempFile.exists()) tempFile.length() else 0L

        var lastSpeedCalcTime = System.currentTimeMillis()
        var bytesSinceLastSpeedCalc = 0L
        var currentSpeed = 0L
        var lastDbUpdateTime = 0L

        var response: okhttp3.Response? = null
        var outputStream: FileOutputStream? = null
        var inputStream: InputStream? = null

        try {
            val requestBuilder = Request.Builder()
                .url(item.url)
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/128.0.0.0 Safari/537.36")
                .header("Accept", "*/*")
                .header("Accept-Language", "en-US,en;q=0.9")
                .header("Connection", "keep-alive")

            val lowerUrl = item.url.lowercase()
            if (lowerUrl.contains("cdninstagram.com") || lowerUrl.contains("fbcdn.net") || lowerUrl.contains("instagram.com")) {
                requestBuilder.header("Referer", "https://www.instagram.com/")
            } else if (lowerUrl.contains("googlevideo.com") || lowerUrl.contains("youtube.com")) {
                requestBuilder.header("Referer", "https://www.youtube.com/")
            } else if (lowerUrl.contains("tiktokcdn.com") || lowerUrl.contains("tikwm.com") || lowerUrl.contains("tiktok.com")) {
                requestBuilder.header("Referer", "https://www.tiktok.com/")
            } else if (lowerUrl.contains("telesco.pe") || lowerUrl.contains("telegram.org") || lowerUrl.contains("t.me")) {
                requestBuilder.header("Referer", "https://t.me/")
            }

            if (downloadedBytes > 0) {
                requestBuilder.header("Range", "bytes=$downloadedBytes-")
            }

            val call = httpClient.newCall(requestBuilder.build())
            response = call.execute()

            if (!response.isSuccessful && response.code != 206) {
                // If Range not supported and 416, restart from 0
                if (response.code == 416) {
                    tempFile.delete()
                    downloadedBytes = 0L
                    executeDownloadInternal(downloadId)
                    return
                } else {
                    repository.markFailed(downloadId, "HTTP Hatası: ${response.code} ${response.message}")
                    return
                }
            }

            val body = response.body
            if (body == null) {
                repository.markFailed(downloadId, "Sunucu boş yanıt döndürdü")
                return
            }

            val bodyLength = body.contentLength()
            if (totalBytes <= 0) {
                totalBytes = if (response.code == 206) {
                    val contentRange = response.header("Content-Range")
                    contentRange?.substringAfterLast('/')?.toLongOrNull() ?: (downloadedBytes + bodyLength)
                } else {
                    bodyLength
                }
            }

            val activeOutputStream = if (downloadedBytes > 0 && response.code == 206) {
                FileOutputStream(tempFile, true)
            } else {
                FileOutputStream(tempFile, false)
            }
            outputStream = activeOutputStream

            val activeInputStream: InputStream = body.byteStream()
            inputStream = activeInputStream
            val buffer = ByteArray(32 * 1024) // 32KB buffer for speed
            var bytesRead: Int

            while (coroutineContext.isActive && !pausedFlags[downloadId]?.get().isTrue()) {
                bytesRead = activeInputStream.read(buffer)
                if (bytesRead == -1) break

                activeOutputStream.write(buffer, 0, bytesRead)
                downloadedBytes += bytesRead
                bytesSinceLastSpeedCalc += bytesRead

                val now = System.currentTimeMillis()

                // Calculate speed every 500ms
                val timeDiff = now - lastSpeedCalcTime
                if (timeDiff >= 500) {
                    currentSpeed = (bytesSinceLastSpeedCalc * 1000) / timeDiff
                    lastSpeedCalcTime = now
                    bytesSinceLastSpeedCalc = 0L

                    // Calculate ETA
                    val remainingBytes = if (totalBytes > downloadedBytes) totalBytes - downloadedBytes else 0L
                    val eta = if (currentSpeed > 0) remainingBytes / currentSpeed else 0L

                    // Update DB throttle (every 500ms)
                    if (now - lastDbUpdateTime >= 500) {
                        repository.updateProgress(
                            id = downloadId,
                            downloaded = downloadedBytes,
                            total = totalBytes,
                            speed = currentSpeed,
                            eta = eta,
                            status = DownloadStatus.DOWNLOADING
                        )
                        lastDbUpdateTime = now
                    }
                }

                // Throttle speed if speedLimitKbps is set
                if (settings.speedLimitKbps > 0) {
                    val maxBytesPerSec = settings.speedLimitKbps * 1024L
                    if (currentSpeed > maxBytesPerSec) {
                        delay(20)
                    }
                }
            }

            activeOutputStream.flush()
            activeOutputStream.close()
            activeInputStream.close()
            response.close()
            outputStream = null
            inputStream = null

            if (pausedFlags[downloadId]?.get().isTrue()) {
                repository.updateStatus(downloadId, DownloadStatus.PAUSED)
            } else if (coroutineContext.isActive) {
                // Completed! Move tempFile to targetFile
                if (targetFile.exists()) targetFile.delete()
                val moved = tempFile.renameTo(targetFile)
                if (!moved) {
                    repository.markFailed(downloadId, "Dosya taşınamadı: hedef konuma yazılamadı")
                    return
                }

                val completedItem = item.copy(
                    downloadedBytes = downloadedBytes,
                    totalBytes = if (totalBytes > 0) totalBytes else downloadedBytes,
                    status = DownloadStatus.COMPLETED,
                    filePath = targetFile.absolutePath,
                    completedAt = System.currentTimeMillis()
                )

                repository.markCompleted(
                    id = downloadId,
                    completedAt = System.currentTimeMillis(),
                    filePath = targetFile.absolutePath
                )

                if (settings.notificationSound || settings.notificationVibration) {
                    NotificationHelper.showDownloadCompletedNotification(context, completedItem)
                }
            }
        } catch (e: CancellationException) {
            // Cancelled or Paused gracefully
            repository.updateStatus(downloadId, DownloadStatus.PAUSED)
        } catch (e: Exception) {
            e.printStackTrace()
            repository.markFailed(downloadId, "İndirme Hatası: ${e.localizedMessage ?: e.message}")
            val failedItem = item.copy(status = DownloadStatus.FAILED)
            NotificationHelper.showDownloadFailedNotification(context, failedItem, e.localizedMessage ?: "Bağlantı kesildi")
        } finally {
            try { inputStream?.close() } catch (_: Exception) {}
            try { outputStream?.close() } catch (_: Exception) {}
            try { response?.close() } catch (_: Exception) {}
            activeJobs.remove(downloadId)
            checkAndProcessQueue()
        }
    }

    private fun sanitizeFileName(name: String): String {
        val cleaned = name.replace(Regex("[\\\\/:*?\"<>|]"), "_").trim()
        return when {
            cleaned.isBlank() -> "OmniGet_File"
            cleaned == "." || cleaned == ".." || cleaned.all { it == '.' } -> "OmniGet_File"
            else -> cleaned
        }
    }
}

private fun Boolean?.isTrue(): Boolean = this == true
