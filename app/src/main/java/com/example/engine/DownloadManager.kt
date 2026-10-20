package com.example.engine

import android.content.ContentValues
import android.content.Context
import android.media.MediaScannerConnection
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
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
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStream
import java.io.RandomAccessFile
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicLongArray
import kotlin.coroutines.coroutineContext

class DownloadManager(
    private val context: Context,
    private val repository: DownloadRepository
) {
    private val managerScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val activeJobs = ConcurrentHashMap<Long, Job>()
    private val pausedFlags = ConcurrentHashMap<Long, AtomicBoolean>()
    private val cancelledFlags = ConcurrentHashMap<Long, AtomicBoolean>()
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
        // Observe settings changes and process queue
        managerScope.launch {
            repository.settingsFlow.collect {
                checkAndProcessQueue()
            }
        }
        registerSmartNetworkMonitor()
    }

    private fun registerSmartNetworkMonitor() {
        runCatching {
            val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return
            cm.registerDefaultNetworkCallback(object : ConnectivityManager.NetworkCallback() {
                override fun onAvailable(network: Network) {
                    handleNetworkTransition()
                }

                override fun onCapabilitiesChanged(network: Network, networkCapabilities: NetworkCapabilities) {
                    handleNetworkTransition()
                }

                override fun onLost(network: Network) {
                    val settings = repository.settingsFlow.value
                    if (settings.wifiOnly || settings.smartNetworkMode) {
                        if (!isNetworkAvailable() || (settings.wifiOnly && !isWifiConnected())) {
                            pauseActiveForNetworkLoss()
                        }
                    }
                }
            })
        }
    }

    private fun handleNetworkTransition() {
        managerScope.launch {
            val settings = repository.settingsFlow.value
            val wifiNow = isWifiConnected()

            if (settings.wifiOnly && !wifiNow) {
                pauseActiveForNetworkLoss()
                return@launch
            }

            if (wifiNow && (settings.autoResumeOnWifi || settings.smartNetworkMode)) {
                repository.resumeAllPaused()
            }
            checkAndProcessQueue()
        }
    }

    private fun pauseActiveForNetworkLoss() {
        activeJobs.keys.toList().forEach { id ->
            pauseDownload(id)
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

    fun resolveEffectiveThreads(requestedThreads: Int, settings: NetworkSettings, isWifi: Boolean): Int {
        val base = requestedThreads.coerceIn(1, 8)
        if (!settings.smartNetworkMode) return base
        return if (isWifi) {
            maxOf(base, settings.connectionThreadsPerFile).coerceIn(1, 8)
        } else {
            // Stabilize mobile data bandwidth by capping concurrent segments per file to 2
            minOf(base, 2).coerceAtLeast(1)
        }
    }

    fun getCategorySubfolderName(fileType: FileType): String {
        return when (fileType) {
            FileType.VIDEO -> "Videos"
            FileType.AUDIO -> "Audio"
            FileType.COURSE -> "Courses"
            FileType.DOCUMENT -> "Documents"
            FileType.ARCHIVE -> "Archives"
            FileType.IMAGE -> "Images"
            FileType.OTHER -> "Others"
        }
    }

    private fun resolveTargetDirectory(fileType: FileType, settings: NetworkSettings): File {
        val baseDir = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS) ?: context.filesDir
        val dir = if (settings.autoCategorizeFiles) {
            File(baseDir, getCategorySubfolderName(fileType))
        } else {
            baseDir
        }
        if (!dir.exists()) {
            dir.mkdirs()
        }
        return dir
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
        courseChapter: String? = null,
        targetHeight: Int? = null
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
            threadsCount = threadsCount.coerceIn(1, 8),
            isCourseBundle = isCourseBundle,
            courseChapter = courseChapter,
            createdAt = System.currentTimeMillis(),
            targetHeight = targetHeight
        )

        repository.insertDownload(entity)
        checkAndProcessQueue()
    }

    fun enqueueBatchDownloads(
        items: List<Pair<String, String>>, // URL, Title
        fileType: FileType,
        isCourseBundle: Boolean = false
    ): Job = managerScope.launch {
        val defaultThreads = repository.settingsFlow.value.connectionThreadsPerFile.coerceIn(1, 8)
        val entities = items.mapIndexed { index, pair ->
            DownloadEntity(
                url = pair.first,
                fileName = sanitizeFileName(pair.second),
                fileType = fileType,
                status = DownloadStatus.QUEUED,
                threadsCount = defaultThreads,
                isCourseBundle = isCourseBundle,
                courseChapter = if (isCourseBundle) "Modül ${index + 1}" else null,
                createdAt = System.currentTimeMillis() + index
            )
        }
        repository.insertDownloads(entities)
        checkAndProcessQueue()
    }

    fun pauseDownload(id: Long) {
        cancelledFlags[id]?.set(false)
        pausedFlags.getOrPut(id) { AtomicBoolean(true) }.set(true)
        activeJobs[id]?.cancel()
        activeJobs.remove(id)

        managerScope.launch {
            repository.updateStatus(id, DownloadStatus.PAUSED)
            checkAndProcessQueue()
        }
    }

    fun resumeDownload(id: Long) {
        cancelledFlags[id]?.set(false)
        pausedFlags[id]?.set(false)
        managerScope.launch {
            repository.updateStatus(id, DownloadStatus.QUEUED)
            checkAndProcessQueue()
        }
    }

    fun cancelDownload(id: Long) {
        cancelledFlags.getOrPut(id) { AtomicBoolean(true) }.set(true)
        pausedFlags.getOrPut(id) { AtomicBoolean(true) }.set(true)
        activeJobs[id]?.cancel()
        activeJobs.remove(id)

        managerScope.launch {
            val item = repository.getDownloadById(id)
            if (item != null) {
                cleanupDownloadFiles(item, deleteFinalFile = true)
            }
            repository.updateProgress(id, 0L, item?.totalBytes ?: -1L, 0L, 0L, DownloadStatus.CANCELLED)
            checkAndProcessQueue()
        }
    }

    fun retryDownload(id: Long) {
        cancelledFlags[id]?.set(false)
        pausedFlags[id]?.set(false)
        managerScope.launch {
            val item = repository.getDownloadById(id) ?: return@launch
            cleanupDownloadFiles(item, deleteFinalFile = false)
            repository.updateProgress(id, 0L, item.totalBytes, 0L, 0L, DownloadStatus.QUEUED)
            checkAndProcessQueue()
        }
    }

    fun pauseAll() {
        activeJobs.keys.toList().forEach { id ->
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
        cancelledFlags.getOrPut(id) { AtomicBoolean(true) }.set(true)
        pausedFlags.getOrPut(id) { AtomicBoolean(true) }.set(true)
        activeJobs[id]?.cancel()
        activeJobs.remove(id)

        managerScope.launch {
            val item = repository.getDownloadById(id)
            if (item != null) {
                cleanupDownloadFiles(item, deleteFinalFile = deleteFileFromDisk)
            }
            repository.deleteDownload(id)
            checkAndProcessQueue()
        }
    }

    private fun cleanupDownloadFiles(item: DownloadEntity, deleteFinalFile: Boolean) {
        val baseDir = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS) ?: context.filesDir
        val candidateDirs = listOf(
            baseDir,
            File(baseDir, getCategorySubfolderName(item.fileType))
        )
        for (dir in candidateDirs) {
            File(dir, "${item.fileName}.omniget_tmp").takeIf { it.exists() }?.delete()
            File(dir, "${item.fileName}.omniget_parts").takeIf { it.exists() }?.delete()
            if (deleteFinalFile) {
                File(dir, item.fileName).takeIf { it.exists() }?.delete()
            }
        }
        if (deleteFinalFile && item.filePath.isNotBlank()) {
            File(item.filePath).takeIf { it.exists() }?.delete()
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

                val isWifi = isWifiConnected()
                if (settings.wifiOnly && !isWifi) {
                    return@withLock
                }

                val currentRunning = activeJobs.size
                val maxConcurrent = if (settings.smartNetworkMode && !isWifi) {
                    minOf(settings.maxConcurrentDownloads, 2).coerceAtLeast(1)
                } else {
                    settings.maxConcurrentDownloads.coerceAtLeast(1)
                }

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

    private fun applyPlatformHeaders(builder: Request.Builder, url: String): Request.Builder {
        builder
            .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/128.0.0.0 Safari/537.36")
            .header("Accept", "*/*")
            .header("Accept-Language", "en-US,en;q=0.9")
            .header("Connection", "keep-alive")

        val lowerUrl = url.lowercase()
        when {
            lowerUrl.contains("cdninstagram.com") || lowerUrl.contains("fbcdn.net") || lowerUrl.contains("instagram.com") ->
                builder.header("Referer", "https://www.instagram.com/")
            lowerUrl.contains("googlevideo.com") || lowerUrl.contains("youtube.com") ->
                builder.header("Referer", "https://www.youtube.com/")
            lowerUrl.contains("tiktokcdn.com") || lowerUrl.contains("tikwm.com") || lowerUrl.contains("tiktok.com") ->
                builder.header("Referer", "https://www.tiktok.com/")
            lowerUrl.contains("telesco.pe") || lowerUrl.contains("telegram.org") || lowerUrl.contains("t.me") ->
                builder.header("Referer", "https://t.me/")
            lowerUrl.contains("twimg.com") || lowerUrl.contains("twitter.com") || lowerUrl.contains("x.com") ->
                builder.header("Referer", "https://x.com/")
        }
        return builder
    }

    private data class TransferProbe(val totalBytes: Long, val supportsRanges: Boolean, val isHls: Boolean)

    private fun probeTransferCapabilities(url: String, fallbackTotalBytes: Long): TransferProbe {
        if (url.substringBefore('?').endsWith(".m3u8", ignoreCase = true)) {
            return TransferProbe(fallbackTotalBytes, supportsRanges = false, isHls = true)
        }
        try {
            val req = applyPlatformHeaders(Request.Builder().url(url).header("Range", "bytes=0-0"), url).build()
            httpClient.newCall(req).execute().use { resp ->
                val ct = resp.header("Content-Type")?.lowercase() ?: ""
                if (ct.contains("mpegurl")) {
                    return TransferProbe(fallbackTotalBytes, supportsRanges = false, isHls = true)
                }
                if (resp.code == 206) {
                    val contentRange = resp.header("Content-Range")
                    val total = contentRange?.substringAfterLast('/')?.trim()?.toLongOrNull() ?: fallbackTotalBytes
                    return TransferProbe(total, supportsRanges = total > 0, isHls = false)
                }
                if (resp.isSuccessful) {
                    val len = resp.header("Content-Length")?.toLongOrNull() ?: fallbackTotalBytes
                    val acceptRanges = resp.header("Accept-Ranges")?.equals("bytes", ignoreCase = true) == true
                    return TransferProbe(len, supportsRanges = acceptRanges && len > 0, isHls = false)
                }
            }
        } catch (_: Exception) {
            // Fall back to single stream
        }
        return TransferProbe(fallbackTotalBytes, supportsRanges = false, isHls = false)
    }

    private suspend fun executeDownloadInternal(downloadId: Long) {
        val item = repository.getDownloadById(downloadId) ?: return
        val settings = repository.settingsFlow.value

        // If Wi-Fi only is enabled and Wi-Fi is disconnected, pause gracefully instead of failing
        if (settings.wifiOnly && !isWifiConnected()) {
            repository.updateStatus(downloadId, DownloadStatus.PAUSED)
            activeJobs.remove(downloadId)
            return
        }

        pausedFlags[downloadId] = AtomicBoolean(false)
        cancelledFlags[downloadId] = AtomicBoolean(false)

        val effectiveThreads = resolveEffectiveThreads(item.threadsCount, settings, isWifiConnected())
        if (effectiveThreads != item.threadsCount) {
            repository.updateDownload(item.copy(threadsCount = effectiveThreads, status = DownloadStatus.DOWNLOADING))
        } else {
            repository.updateStatus(downloadId, DownloadStatus.DOWNLOADING)
        }

        val targetDir = resolveTargetDirectory(item.fileType, settings)
        val targetFile = File(targetDir, item.fileName)
        val tempFile = File(targetDir, "${item.fileName}.omniget_tmp")
        val partsMetaFile = File(targetDir, "${item.fileName}.omniget_parts")

        try {
            val probe = probeTransferCapabilities(item.url, item.totalBytes)
            val resolvedTotalBytes = if (probe.totalBytes > 0) probe.totalBytes else item.totalBytes

            val transferSucceeded = when {
                probe.isHls -> downloadHlsStream(downloadId, item.url, tempFile, settings)
                effectiveThreads > 1 && probe.supportsRanges && resolvedTotalBytes > 512 * 1024L ->
                    downloadMultiThreaded(
                        downloadId = downloadId,
                        url = item.url,
                        tempFile = tempFile,
                        partsMetaFile = partsMetaFile,
                        totalBytes = resolvedTotalBytes,
                        threadsCount = effectiveThreads,
                        settings = settings
                    )
                else ->
                    downloadSingleStream(
                        downloadId = downloadId,
                        url = item.url,
                        tempFile = tempFile,
                        initialTotalBytes = resolvedTotalBytes,
                        settings = settings
                    )
            }

            if (cancelledFlags[downloadId]?.get().isTrue()) {
                tempFile.delete()
                partsMetaFile.delete()
                repository.updateStatus(downloadId, DownloadStatus.CANCELLED)
                return
            }

            if (pausedFlags[downloadId]?.get().isTrue() || !coroutineContext.isActive) {
                repository.updateStatus(downloadId, DownloadStatus.PAUSED)
                return
            }

            if (!transferSucceeded) {
                return
            }

            // Completed! Move tempFile to targetFile and clean up parts metadata
            partsMetaFile.delete()
            if (targetFile.exists()) targetFile.delete()
            val moved = tempFile.renameTo(targetFile)
            if (!moved) {
                val reason = "Dosya taşınamadı: hedef konuma yazılamadı"
                repository.markFailed(downloadId, reason)
                NotificationHelper.showDownloadFailedNotification(context, item.copy(status = DownloadStatus.FAILED), reason)
                return
            }

            val finalFile = postProcess(item, targetFile, targetDir)
            if (finalFile == null) {
                val reason = "Medya işlenemedi (ses ayrıştırma başarısız oldu)"
                repository.markFailed(downloadId, reason)
                NotificationHelper.showDownloadFailedNotification(context, item.copy(status = DownloadStatus.FAILED), reason)
                return
            }

            // Export a public copy to system Downloads/OmniGet via MediaStore so it appears in Gallery/Files
            // and survives app uninstallation.
            exportToPublicDownloads(finalFile, item.fileType, item.mimeType, settings.autoCategorizeFiles)

            val finalSize = finalFile.length()
            val completedItem = item.copy(
                downloadedBytes = finalSize,
                totalBytes = finalSize,
                status = DownloadStatus.COMPLETED,
                filePath = finalFile.absolutePath,
                fileName = finalFile.name,
                threadsCount = effectiveThreads,
                speedBytesPerSec = 0L,
                etaSeconds = 0L,
                completedAt = System.currentTimeMillis()
            )

            repository.updateDownload(completedItem)

            NotificationHelper.showDownloadCompletedNotification(
                context = context,
                item = completedItem,
                playSound = settings.notificationSound,
                vibrate = settings.notificationVibration
            )
        } catch (e: CancellationException) {
            if (cancelledFlags[downloadId]?.get().isTrue()) {
                tempFile.delete()
                partsMetaFile.delete()
                repository.updateStatus(downloadId, DownloadStatus.CANCELLED)
            } else {
                repository.updateStatus(downloadId, DownloadStatus.PAUSED)
            }
        } catch (e: Exception) {
            if (cancelledFlags[downloadId]?.get().isTrue()) {
                tempFile.delete()
                partsMetaFile.delete()
                repository.updateStatus(downloadId, DownloadStatus.CANCELLED)
            } else if (pausedFlags[downloadId]?.get().isTrue()) {
                repository.updateStatus(downloadId, DownloadStatus.PAUSED)
            } else {
                e.printStackTrace()
                val msg = e.localizedMessage ?: e.message ?: "Bağlantı kesildi"
                repository.markFailed(downloadId, "İndirme Hatası: $msg")
                val failedItem = item.copy(status = DownloadStatus.FAILED)
                NotificationHelper.showDownloadFailedNotification(context, failedItem, msg)
            }
        } finally {
            activeJobs.remove(downloadId)
            checkAndProcessQueue()
        }
    }

    /**
     * Real multi-threaded segmented download using parallel coroutines, HTTP Byte-Range
     * requests, and RandomAccessFile, with per-segment checkpoint persistence in `.omniget_parts`.
     */
    private suspend fun downloadMultiThreaded(
        downloadId: Long,
        url: String,
        tempFile: File,
        partsMetaFile: File,
        totalBytes: Long,
        threadsCount: Int,
        settings: NetworkSettings
    ): Boolean = coroutineScope {
        val segmentProgress = AtomicLongArray(threadsCount)
        val chunkSize = totalBytes / threadsCount

        // Restore saved per-segment progress if `.omniget_parts` matches threadsCount & totalBytes
        var restored = false
        if (tempFile.exists() && partsMetaFile.exists()) {
            runCatching {
                val content = partsMetaFile.readText().trim()
                val parts = content.split(":")
                if (parts.size == 3 && parts[0].toInt() == threadsCount && parts[1].toLong() == totalBytes) {
                    val offsets = parts[2].split(",").map { it.toLong() }
                    if (offsets.size == threadsCount) {
                        for (i in 0 until threadsCount) {
                            segmentProgress.set(i, offsets[i])
                        }
                        restored = true
                    }
                }
            }
        }

        if (!restored) {
            RandomAccessFile(tempFile, "rw").use { raf ->
                raf.setLength(totalBytes)
            }
            for (i in 0 until threadsCount) {
                segmentProgress.set(i, 0L)
            }
            savePartsMeta(partsMetaFile, threadsCount, totalBytes, segmentProgress)
        }

        var initialDownloaded = 0L
        for (i in 0 until threadsCount) {
            initialDownloaded += segmentProgress.get(i)
        }

        val globalDownloaded = AtomicLong(initialDownloaded)
        val bytesSinceLastSpeedCalc = AtomicLong(0L)
        val lastSpeedCalcTime = AtomicLong(System.currentTimeMillis())
        val lastDbUpdateTime = AtomicLong(0L)
        val currentSpeed = AtomicLong(0L)
        val progressMutex = Mutex()

        val jobs = (0 until threadsCount).map { threadIndex ->
            async(Dispatchers.IO) {
                val startByte = threadIndex * chunkSize
                val endByte = if (threadIndex == threadsCount - 1) totalBytes - 1 else (startByte + chunkSize - 1)
                val segmentLength = (endByte - startByte) + 1
                var alreadyInSegment = segmentProgress.get(threadIndex).coerceIn(0L, segmentLength)

                if (alreadyInSegment >= segmentLength) {
                    return@async true
                }

                val currentStart = startByte + alreadyInSegment
                val request = applyPlatformHeaders(
                    Request.Builder()
                        .url(url)
                        .header("Range", "bytes=$currentStart-$endByte"),
                    url
                ).build()

                httpClient.newCall(request).execute().use { response ->
                    if (!response.isSuccessful && response.code != 206) {
                        throw IllegalStateException("Segment $threadIndex HTTP ${response.code}")
                    }
                    val contentType = response.header("Content-Type")?.lowercase() ?: ""
            if (item.fileType in listOf(FileType.VIDEO, FileType.AUDIO, FileType.ARCHIVE) &&
                (contentType.startsWith("text/html") || contentType.startsWith("text/plain") ||
                 (contentType.startsWith("application/json") && !url.contains(".json")))) {
                val reason = "İçerik korumalı veya sunucu web sayfası döndürdü (Video akışı doğrulanamadı)"
                repository.markFailed(downloadId, reason)
                val itm = repository.getDownloadById(downloadId)
                if (itm != null) {
                    NotificationHelper.showDownloadFailedNotification(context, itm.copy(status = DownloadStatus.FAILED), reason)
                }
                return false
            }

            val body = response.body ?: throw IllegalStateException("Segment $threadIndex boş yanıt")

                    RandomAccessFile(tempFile, "rw").use { raf ->
                        raf.seek(currentStart)
                        body.byteStream().use { input ->
                            val buffer = ByteArray(32 * 1024)
                            var bytesRead: Int
                            while (coroutineContext.isActive &&
                                !pausedFlags[downloadId]?.get().isTrue() &&
                                !cancelledFlags[downloadId]?.get().isTrue()
                            ) {
                                val remainingInSegment = segmentLength - alreadyInSegment
                                if (remainingInSegment <= 0L) break
                                val toRead = minOf(buffer.size.toLong(), remainingInSegment).toInt()
                                bytesRead = input.read(buffer, 0, toRead)
                                if (bytesRead == -1) break

                                raf.write(buffer, 0, bytesRead)
                                alreadyInSegment += bytesRead
                                segmentProgress.set(threadIndex, alreadyInSegment)

                                val totalNow = globalDownloaded.addAndGet(bytesRead.toLong())
                                val sinceCalc = bytesSinceLastSpeedCalc.addAndGet(bytesRead.toLong())
                                val now = System.currentTimeMillis()
                                val timeDiff = now - lastSpeedCalcTime.get()

                                if (timeDiff >= 500 && progressMutex.tryLock()) {
                                    try {
                                        val elapsed = now - lastSpeedCalcTime.get()
                                        if (elapsed >= 500) {
                                            val spd = (bytesSinceLastSpeedCalc.getAndSet(0L) * 1000L) / elapsed
                                            currentSpeed.set(spd)
                                            lastSpeedCalcTime.set(now)

                                            val remainingTotal = (totalBytes - totalNow).coerceAtLeast(0L)
                                            val eta = if (spd > 0) remainingTotal / spd else 0L

                                            savePartsMeta(partsMetaFile, threadsCount, totalBytes, segmentProgress)

                                            if (now - lastDbUpdateTime.get() >= 500) {
                                                repository.updateProgress(
                                                    id = downloadId,
                                                    downloaded = totalNow,
                                                    total = totalBytes,
                                                    speed = spd,
                                                    eta = eta,
                                                    status = DownloadStatus.DOWNLOADING
                                                )
                                                lastDbUpdateTime.set(now)
                                            }
                                        }
                                    } finally {
                                        progressMutex.unlock()
                                    }
                                }

                                val speedLimit = repository.settingsFlow.value.speedLimitKbps
                                if (speedLimit > 0) {
                                    val maxBytesPerSec = speedLimit * 1024L
                                    if (currentSpeed.get() > maxBytesPerSec) {
                                        delay(25)
                                    }
                                }
                            }
                        }
                    }
                }
                true
            }
        }

        val results = jobs.awaitAll()
        savePartsMeta(partsMetaFile, threadsCount, totalBytes, segmentProgress)
        results.all { it }
    }

    private fun savePartsMeta(
        partsMetaFile: File,
        threadsCount: Int,
        totalBytes: Long,
        segmentProgress: AtomicLongArray
    ) {
        runCatching {
            val sb = StringBuilder()
            sb.append(threadsCount).append(':').append(totalBytes).append(':')
            for (i in 0 until threadsCount) {
                if (i > 0) sb.append(',')
                sb.append(segmentProgress.get(i))
            }
            partsMetaFile.writeText(sb.toString())
        }
    }

    private suspend fun downloadSingleStream(
        downloadId: Long,
        url: String,
        tempFile: File,
        initialTotalBytes: Long,
        settings: NetworkSettings
    ): Boolean {
        var totalBytes = initialTotalBytes
        var downloadedBytes = if (tempFile.exists()) tempFile.length() else 0L

        var lastSpeedCalcTime = System.currentTimeMillis()
        var bytesSinceLastSpeedCalc = 0L
        var currentSpeed = 0L
        var lastDbUpdateTime = 0L

        val requestBuilder = applyPlatformHeaders(Request.Builder().url(url), url)
        if (downloadedBytes > 0) {
            requestBuilder.header("Range", "bytes=$downloadedBytes-")
        }

        httpClient.newCall(requestBuilder.build()).execute().use { response ->
            if (!response.isSuccessful && response.code != 206) {
                if (response.code == 416) {
                    tempFile.delete()
                    return downloadSingleStream(downloadId, url, tempFile, initialTotalBytes, settings)
                } else {
                    val reason = "HTTP Hatası: ${response.code} ${response.message}"
                    repository.markFailed(downloadId, reason)
                    val item = repository.getDownloadById(downloadId)
                    if (item != null) {
                        NotificationHelper.showDownloadFailedNotification(context, item.copy(status = DownloadStatus.FAILED), reason)
                    }
                    return false
                }
            }

            val body = response.body
            if (body == null) {
                val reason = "Sunucu boş yanıt döndürdü"
                repository.markFailed(downloadId, reason)
                return false
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

            val appendMode = downloadedBytes > 0 && response.code == 206
            if (!appendMode) {
                downloadedBytes = 0L
            }

            FileOutputStream(tempFile, appendMode).use { activeOutputStream ->
                body.byteStream().use { activeInputStream ->
                    val buffer = ByteArray(32 * 1024)
                    var bytesRead: Int

                    while (coroutineContext.isActive &&
                        !pausedFlags[downloadId]?.get().isTrue() &&
                        !cancelledFlags[downloadId]?.get().isTrue()
                    ) {
                        bytesRead = activeInputStream.read(buffer)
                        if (bytesRead == -1) break

                        activeOutputStream.write(buffer, 0, bytesRead)
                        downloadedBytes += bytesRead
                        bytesSinceLastSpeedCalc += bytesRead

                        val now = System.currentTimeMillis()
                        val timeDiff = now - lastSpeedCalcTime
                        if (timeDiff >= 500) {
                            currentSpeed = (bytesSinceLastSpeedCalc * 1000) / timeDiff
                            lastSpeedCalcTime = now
                            bytesSinceLastSpeedCalc = 0L

                            val remainingBytes = if (totalBytes > downloadedBytes) totalBytes - downloadedBytes else 0L
                            val eta = if (currentSpeed > 0) remainingBytes / currentSpeed else 0L

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

                        val speedLimit = repository.settingsFlow.value.speedLimitKbps
                        if (speedLimit > 0) {
                            val maxBytesPerSec = speedLimit * 1024L
                            if (currentSpeed > maxBytesPerSec) {
                                delay(20)
                            }
                        }
                    }
                    activeOutputStream.flush()
                }
            }
        }
        return true
    }

    /**
     * Downloads HLS (.m3u8) media playlists by resolving segment URLs and concatenating
     * stream segments into `tempFile`.
     */
    private suspend fun downloadHlsStream(
        downloadId: Long,
        playlistUrl: String,
        tempFile: File,
        settings: NetworkSettings
    ): Boolean {
        val playlistReq = applyPlatformHeaders(Request.Builder().url(playlistUrl), playlistUrl).build()
        val playlistBody = httpClient.newCall(playlistReq).execute().use { resp ->
            if (!resp.isSuccessful) throw IllegalStateException("HLS Playlist HTTP ${resp.code}")
            resp.body?.string() ?: ""
        }

        val lines = playlistBody.lines().map { it.trim() }
        // If master playlist, follow the first media stream playlist
        val subPlaylistLine = lines.firstOrNull { !it.startsWith("#") && it.contains(".m3u8", ignoreCase = true) }
        if (subPlaylistLine != null) {
            val resolvedSub = playlistUrl.toHttpUrlOrNull()?.resolve(subPlaylistLine)?.toString() ?: subPlaylistLine
            return downloadHlsStream(downloadId, resolvedSub, tempFile, settings)
        }

        val segmentUrls = lines
            .filter { it.isNotBlank() && !it.startsWith("#") }
            .map { seg -> playlistUrl.toHttpUrlOrNull()?.resolve(seg)?.toString() ?: seg }

        if (segmentUrls.isEmpty()) {
            throw IllegalStateException("HLS akışında indirilebilir segment bulunamadı")
        }

        var downloadedBytes = 0L
        var lastSpeedCalcTime = System.currentTimeMillis()
        var bytesSinceLastCalc = 0L

        FileOutputStream(tempFile, false).use { out ->
            for ((idx, segUrl) in segmentUrls.withIndex()) {
                if (!coroutineContext.isActive ||
                    pausedFlags[downloadId]?.get().isTrue() ||
                    cancelledFlags[downloadId]?.get().isTrue()
                ) {
                    break
                }

                val segReq = applyPlatformHeaders(Request.Builder().url(segUrl), segUrl).build()
                httpClient.newCall(segReq).execute().use { segResp ->
                    if (!segResp.isSuccessful) throw IllegalStateException("HLS Segment HTTP ${segResp.code}")
                    segResp.body?.byteStream()?.use { input ->
                        val buf = ByteArray(32 * 1024)
                        var read: Int
                        while (input.read(buf).also { read = it } != -1) {
                            out.write(buf, 0, read)
                            downloadedBytes += read
                            bytesSinceLastCalc += read
                        }
                    }
                }

                val now = System.currentTimeMillis()
                val elapsed = (now - lastSpeedCalcTime).coerceAtLeast(1L)
                val speed = (bytesSinceLastCalc * 1000L) / elapsed
                lastSpeedCalcTime = now
                bytesSinceLastCalc = 0L

                val estimatedTotal = if (idx > 0) (downloadedBytes * segmentUrls.size) / (idx + 1) else downloadedBytes * segmentUrls.size
                val remainingSegs = segmentUrls.size - (idx + 1)
                val eta = if (speed > 0 && idx > 0) ((estimatedTotal - downloadedBytes).coerceAtLeast(0L)) / speed else remainingSegs.toLong()

                repository.updateProgress(
                    id = downloadId,
                    downloaded = downloadedBytes,
                    total = estimatedTotal,
                    speed = speed,
                    eta = eta,
                    status = DownloadStatus.DOWNLOADING
                )
            }
            out.flush()
        }
        return true
    }

    /**
     * Exports a public copy of the completed download into the system `Downloads/OmniGet`
     * folder via MediaStore (Android 10+) or triggers MediaScannerConnection on older devices.
     */
    private fun exportToPublicDownloads(
        sourceFile: File,
        fileType: FileType,
        mimeType: String,
        categorize: Boolean
    ) {
        runCatching {
            val resolvedMime = mimeType.ifBlank {
                when (fileType) {
                    FileType.VIDEO, FileType.COURSE -> "video/mp4"
                    FileType.AUDIO -> "audio/mp4"
                    FileType.DOCUMENT -> "application/pdf"
                    FileType.IMAGE -> "image/jpeg"
                    else -> "application/octet-stream"
                }
            }
            val subFolder = if (categorize) "OmniGet/${getCategorySubfolderName(fileType)}" else "OmniGet"

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val resolver = context.contentResolver
                val values = ContentValues().apply {
                    put(MediaStore.Downloads.DISPLAY_NAME, sourceFile.name)
                    put(MediaStore.Downloads.MIME_TYPE, resolvedMime)
                    put(MediaStore.Downloads.RELATIVE_PATH, "${Environment.DIRECTORY_DOWNLOADS}/$subFolder")
                    put(MediaStore.Downloads.IS_PENDING, 1)
                }
                val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values) ?: return
                resolver.openOutputStream(uri)?.use { out ->
                    FileInputStream(sourceFile).use { input ->
                        input.copyTo(out, bufferSize = 32 * 1024)
                    }
                }
                values.clear()
                values.put(MediaStore.Downloads.IS_PENDING, 0)
                resolver.update(uri, values, null, null)
            } else {
                MediaScannerConnection.scanFile(
                    context,
                    arrayOf(sourceFile.absolutePath),
                    arrayOf(resolvedMime),
                    null
                )
            }
        }
    }

    private suspend fun postProcess(item: DownloadEntity, file: File, targetDir: File): File? {
        // Anti-fake file check: if a video file has HTML/error contents, reject it
        if (item.fileType == FileType.VIDEO) {
            val headerBytes = ByteArray(minOf(file.length(), 256).toInt())
            if (headerBytes.isNotEmpty()) {
                FileInputStream(file).use { it.read(headerBytes) }
                val headerStr = String(headerBytes, Charsets.UTF_8).lowercase()
                if (headerStr.contains("<!doctype") || headerStr.contains("<html") || headerStr.contains("{"error"")) {
                    file.delete()
                    return null
                }
            }
        }

        val probe = try {
            MediaTranscoder.probe(file)
        } catch (_: Exception) {
            return file
        }

        if (item.fileType == FileType.AUDIO && probe.hasVideo) {
            val scratchOut = File(targetDir, "${file.name}.extracted.m4a")
            if (scratchOut.exists()) scratchOut.delete()
            val ok = try {
                MediaTranscoder.extractAudio(context, file, scratchOut)
            } catch (_: Exception) {
                false
            }
            if (!ok) {
                scratchOut.delete()
                return null
            }
            val finalName = "${file.nameWithoutExtension}.m4a"
            file.delete()
            val finalOut = File(targetDir, finalName)
            if (finalOut.exists()) finalOut.delete()
            scratchOut.renameTo(finalOut)
            return finalOut
        }

        val targetHeight = item.targetHeight
        if (targetHeight != null && probe.hasVideo && probe.height > targetHeight) {
            val scratchOut = File(targetDir, "${file.name}.resized")
            if (scratchOut.exists()) scratchOut.delete()
            val ok = try {
                MediaTranscoder.downscale(context, file, scratchOut, targetHeight)
            } catch (_: Exception) {
                false
            }
            if (ok) {
                file.delete()
                val finalOut = File(targetDir, file.name)
                scratchOut.renameTo(finalOut)
                return finalOut
            }
            scratchOut.delete()
        }

        return file
    }

    fun sanitizeFileName(name: String): String {
        val cleaned = name.replace(Regex("[\\\\/:*?\"<>|]"), "_").trim()
        return when {
            cleaned.isBlank() -> "OmniGet_File"
            cleaned == "." || cleaned == ".." || cleaned.all { it == '.' } -> "OmniGet_File"
            else -> cleaned
        }
    }
}

private fun Boolean?.isTrue(): Boolean = this == true
