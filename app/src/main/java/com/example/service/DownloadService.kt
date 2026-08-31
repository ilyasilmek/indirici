package com.example.service

import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.ServiceCompat
import com.example.OmniGetApplication
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class DownloadService : Service() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var isForegroundRunning = false

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action

        when (action) {
            ACTION_STOP_SERVICE -> {
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
                return START_NOT_STICKY
            }
            ACTION_START_OR_UPDATE -> {
                startOrUpdateForeground()
            }
            else -> {
                startOrUpdateForeground()
            }
        }

        observeDownloads()
        return START_STICKY
    }

    private fun startOrUpdateForeground() {
        val notification = NotificationHelper.buildForegroundNotification(
            this,
            1,
            "OmniGet Hızlı İndirici",
            0,
            "Başlatılıyor..."
        )

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                ServiceCompat.startForeground(
                    this,
                    NotificationHelper.NOTIFICATION_ID_FOREGROUND,
                    notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
                )
            } else {
                startForeground(NotificationHelper.NOTIFICATION_ID_FOREGROUND, notification)
            }
            isForegroundRunning = true
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun observeDownloads() {
        val repository = (applicationContext as? OmniGetApplication)?.repository ?: return

        serviceScope.launch {
            repository.activeDownloads.collectLatest { activeList ->
                val downloadingList = activeList.filter { it.status.isActive() }

                if (downloadingList.isEmpty()) {
                    // No active downloads left, delay and stop service
                    stopForeground(STOP_FOREGROUND_REMOVE)
                    stopSelf()
                } else {
                    val count = downloadingList.size
                    val totalSpeed = downloadingList.sumOf { it.speedBytesPerSec }
                    val speedStr = NotificationHelper.formatSpeed(totalSpeed)

                    val firstItem = downloadingList.first()
                    val overallProgress = if (downloadingList.all { it.totalBytes > 0 }) {
                        val totalBytes = downloadingList.sumOf { it.totalBytes }
                        val downloadedBytes = downloadingList.sumOf { it.downloadedBytes }
                        if (totalBytes > 0) ((downloadedBytes * 100) / totalBytes).toInt() else 0
                    } else {
                        firstItem.progressPercentInt
                    }

                    val updatedNotification = NotificationHelper.buildForegroundNotification(
                        this@DownloadService,
                        count,
                        firstItem.fileName,
                        overallProgress,
                        speedStr
                    )

                    val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as android.app.NotificationManager
                    notificationManager.notify(NotificationHelper.NOTIFICATION_ID_FOREGROUND, updatedNotification)
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()
        isForegroundRunning = false
    }

    companion object {
        const val ACTION_START_OR_UPDATE = "com.example.omniget.ACTION_START"
        const val ACTION_STOP_SERVICE = "com.example.omniget.ACTION_STOP"

        fun startService(context: Context) {
            val intent = Intent(context, DownloadService::class.java).apply {
                action = ACTION_START_OR_UPDATE
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stopService(context: Context) {
            val intent = Intent(context, DownloadService::class.java).apply {
                action = ACTION_STOP_SERVICE
            }
            context.startService(intent)
        }
    }
}
