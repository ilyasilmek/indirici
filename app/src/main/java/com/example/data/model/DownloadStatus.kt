package com.example.data.model

enum class DownloadStatus {
    QUEUED,
    DOWNLOADING,
    PAUSED,
    COMPLETED,
    FAILED,
    CANCELLED;

    fun isTerminal(): Boolean = this == COMPLETED || this == FAILED || this == CANCELLED
    fun isActive(): Boolean = this == DOWNLOADING || this == QUEUED
}

enum class FileType {
    VIDEO,
    AUDIO,
    DOCUMENT,
    COURSE,
    ARCHIVE,
    IMAGE,
    OTHER;

    companion object {
        fun fromMimeOrUrl(mimeType: String?, url: String): FileType {
            val mime = mimeType?.lowercase() ?: ""
            val ext = url.substringBefore('?').substringAfterLast('.', "").lowercase()

            return when {
                mime.startsWith("video/") || ext in listOf("mp4", "mkv", "webm", "avi", "mov", "flv", "m4v") -> VIDEO
                mime.startsWith("audio/") || ext in listOf("mp3", "m4a", "aac", "wav", "flac", "ogg", "opus") -> AUDIO
                mime.contains("pdf") || mime.contains("document") || mime.contains("text") ||
                        ext in listOf("pdf", "epub", "docx", "txt", "xlsx", "pptx", "md") -> DOCUMENT
                mime.contains("zip") || mime.contains("compressed") || mime.contains("tar") ||
                        ext in listOf("zip", "rar", "7z", "tar", "gz", "bz2") -> ARCHIVE
                mime.startsWith("image/") || ext in listOf("jpg", "jpeg", "png", "gif", "webp", "svg") -> IMAGE
                url.contains("udemy") || url.contains("course") || url.contains("skool") || url.contains("kiwify") -> COURSE
                else -> OTHER
            }
        }
    }
}

data class MediaQualityOption(
    val id: String,
    val title: String,
    val resolution: String,
    val format: String,
    val estimatedSize: String,
    val estimatedBytes: Long,
    val isAudioOnly: Boolean = false,
    val directDownloadUrl: String,
    // When set, the source is downscaled to this height after download if it
    // turns out to actually be taller (some extractors only have one real
    // source file and label multiple "quality" tiers off of it).
    val targetHeight: Int? = null
)

data class MediaInspectResult(
    val title: String,
    val originalUrl: String,
    val hostPlatform: String,
    val fileType: FileType,
    val totalSizeText: String,
    val totalSizeBytes: Long,
    val mimeType: String,
    val supportsMultiThread: Boolean,
    val qualityOptions: List<MediaQualityOption>,
    val author: String? = null,
    val durationText: String? = null,
    val thumbnailUrl: String? = null,
    val isCourseBundle: Boolean = false,
    val courseLessons: List<CourseLessonItem> = emptyList()
)

data class CourseLessonItem(
    val index: Int,
    val title: String,
    val duration: String,
    val downloadUrl: String,
    val sizeText: String,
    val sizeBytes: Long
)

data class NetworkSettings(
    val smartNetworkMode: Boolean = true,
    val wifiOnly: Boolean = false,
    val maxConcurrentDownloads: Int = 3,
    val connectionThreadsPerFile: Int = 4,
    val speedLimitKbps: Int = 0, // 0 is unlimited
    val autoResumeOnWifi: Boolean = true,
    val notificationSound: Boolean = true,
    val notificationVibration: Boolean = true,
    val autoCategorizeFiles: Boolean = true
)
