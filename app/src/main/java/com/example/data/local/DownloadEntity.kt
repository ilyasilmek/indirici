package com.example.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.example.data.model.DownloadStatus
import com.example.data.model.FileType

@Entity(tableName = "downloads")
data class DownloadEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val url: String,
    val fileName: String,
    val fileType: FileType,
    val totalBytes: Long = -1L,
    val downloadedBytes: Long = 0L,
    val status: DownloadStatus = DownloadStatus.QUEUED,
    val speedBytesPerSec: Long = 0L,
    val etaSeconds: Long = 0L,
    val filePath: String = "",
    val mimeType: String = "",
    val thumbnailUrl: String? = null,
    val threadsCount: Int = 4,
    val errorMessage: String? = null,
    val isCourseBundle: Boolean = false,
    val courseChapter: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val completedAt: Long? = null,
    // When set, the downloaded video is downscaled to this height (if the
    // source is taller) after completion. Null means "keep the source as is".
    val targetHeight: Int? = null
) {
    val progressPercent: Float
        get() = if (totalBytes > 0) {
            (downloadedBytes.toFloat() / totalBytes.toFloat()).coerceIn(0f, 1f)
        } else {
            0f
        }

    val progressPercentInt: Int
        get() = (progressPercent * 100).toInt()
}
