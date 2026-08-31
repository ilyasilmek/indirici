package com.example.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import com.example.MainActivity
import com.example.R
import com.example.data.local.DownloadEntity
import java.util.Locale

object NotificationHelper {

    const val CHANNEL_DOWNLOADING = "omniget_download_channel"
    const val CHANNEL_COMPLETED = "omniget_completed_channel"
    const val NOTIFICATION_ID_FOREGROUND = 1001

    fun createNotificationChannels(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

            val downloadChannel = NotificationChannel(
                CHANNEL_DOWNLOADING,
                "Aktif İndirmeler (Active Downloads)",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "OmniGet arka planda hızlı indirme durumu ve hız göstergesi"
                enableVibration(false)
                setShowBadge(false)
            }

            val completedChannel = NotificationChannel(
                CHANNEL_COMPLETED,
                "Tamamlanan İndirmeler (Completed)",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "İndirme tamamlandığında veya hata oluştuğunda gelen bildirimler"
                enableVibration(true)
                setShowBadge(true)
            }

            notificationManager.createNotificationChannel(downloadChannel)
            notificationManager.createNotificationChannel(completedChannel)
        }
    }

    fun buildForegroundNotification(
        context: Context,
        activeCount: Int,
        currentFile: String,
        overallProgress: Int,
        speedFormatted: String
    ): Notification {
        val openAppIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            0,
            openAppIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0)
        )

        val title = if (activeCount > 1) {
            "OmniGet: $activeCount dosya indiriliyor ($speedFormatted)"
        } else {
            "OmniGet: $currentFile ($speedFormatted)"
        }

        val content = if (activeCount > 1) {
            "İlerleme: %$overallProgress • Toplam $activeCount aktif aktarım"
        } else {
            "İlerleme: %$overallProgress • Arka plan indirme servisi aktif"
        }

        return NotificationCompat.Builder(context, CHANNEL_DOWNLOADING)
            .setContentTitle(title)
            .setContentText(content)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setProgress(100, overallProgress, overallProgress <= 0)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_PROGRESS)
            .build()
    }

    fun showDownloadCompletedNotification(context: Context, item: DownloadEntity) {
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        val openAppIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("OPEN_TAB", "library")
            putExtra("DOWNLOAD_ID", item.id)
        }

        val pendingIntent = PendingIntent.getActivity(
            context,
            item.id.toInt(),
            openAppIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0)
        )

        val sizeFormatted = formatFileSize(item.totalBytes)

        val notification = NotificationCompat.Builder(context, CHANNEL_COMPLETED)
            .setContentTitle("İndirme Tamamlandı: ${item.fileName}")
            .setContentText("Boyut: $sizeFormatted • Dosya kaydedildi")
            .setSmallIcon(android.R.drawable.stat_sys_download_done)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .build()

        notificationManager.notify(item.id.toInt() + 2000, notification)
    }

    fun showDownloadFailedNotification(context: Context, item: DownloadEntity, reason: String) {
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        val openAppIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }

        val pendingIntent = PendingIntent.getActivity(
            context,
            item.id.toInt(),
            openAppIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0)
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_COMPLETED)
            .setContentTitle("İndirme Başarısız: ${item.fileName}")
            .setContentText(reason)
            .setSmallIcon(android.R.drawable.stat_notify_error)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()

        notificationManager.notify(item.id.toInt() + 3000, notification)
    }

    fun formatFileSize(bytes: Long): String {
        if (bytes <= 0) return "0 B"
        val kb = bytes / 1024.0
        val mb = kb / 1024.0
        val gb = mb / 1024.0
        return when {
            gb >= 1.0 -> String.format(Locale.US, "%.2f GB", gb)
            mb >= 1.0 -> String.format(Locale.US, "%.2f MB", mb)
            kb >= 1.0 -> String.format(Locale.US, "%.1f KB", kb)
            else -> "$bytes B"
        }
    }

    fun formatSpeed(bytesPerSec: Long): String {
        if (bytesPerSec <= 0) return "0 KB/s"
        val kb = bytesPerSec / 1024.0
        val mb = kb / 1024.0
        return if (mb >= 1.0) {
            String.format(Locale.US, "%.2f MB/s", mb)
        } else {
            String.format(Locale.US, "%.0f KB/s", kb)
        }
    }
}
