package com.example.engine.extractors

import com.example.data.model.CourseLessonItem
import com.example.data.model.FileType
import com.example.data.model.MediaInspectResult
import com.example.data.model.MediaQualityOption
import com.example.engine.UrlInspector
import com.example.service.NotificationHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request

object CourseExtractor {

    private const val BROWSER_USER_AGENT =
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/128.0.0.0 Safari/537.36"

    fun matches(url: String): Boolean {
        val lower = url.lowercase()
        return lower.contains("udemy.com/") ||
            lower.contains("skool.com/") ||
            lower.contains("kiwify.com") ||
            lower.contains("teachable.com/") ||
            lower.contains("coursera.org/")
    }

    suspend fun extract(
        url: String,
        httpClient: OkHttpClient
    ): MediaInspectResult? = withContext(Dispatchers.IO) {
        val cleanUrl = url.trim()
        val hostPlatform = UrlInspector.detectPlatform(cleanUrl)

        try {
            val request = Request.Builder()
                .url(cleanUrl)
                .header("User-Agent", BROWSER_USER_AGENT)
                .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
                .build()

            httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@withContext null
                val body = response.body?.string() ?: return@withContext null

                val titleRegex = Regex("""<meta\s+(?:property|name)=["'](?:og:title|twitter:title)["']\s+content=["']([^"']+)["']""", RegexOption.IGNORE_CASE)
                val htmlTitleRegex = Regex("""<title[^>]*>([^<]+)</title>""", RegexOption.IGNORE_CASE)
                val imgRegex = Regex("""<meta\s+(?:property|name)=["'](?:og:image|twitter:image)["']\s+content=["']([^"']+)["']""", RegexOption.IGNORE_CASE)

                val rawTitle = titleRegex.find(body)?.groupValues?.getOrNull(1)
                    ?: htmlTitleRegex.find(body)?.groupValues?.getOrNull(1)
                    ?: "Online_Course_Module"
                val cleanTitle = decodeHtml(rawTitle).replace(Regex("""[\\/:*?"<>|\r\n\t]"""), " ").trim().take(70)
                val thumbUrl = imgRegex.find(body)?.groupValues?.getOrNull(1)?.let { decodeHtml(it) }

                // Discover direct video/document/archive links embedded in course page
                val mediaUrlRegex = Regex("""(https?://[^\s"'<>\\]+\.(?:mp4|m4v|webm|m3u8|pdf|zip)(?:\?[^\s"'<>\\]*)?)""", RegexOption.IGNORE_CASE)
                val foundUrls = mediaUrlRegex.findAll(body)
                    .map { decodeHtml(it.groupValues[1].replace("\\/", "/")) }
                    .distinct()
                    .toList()

                if (foundUrls.isEmpty()) {
                    // Check relative video/source tags
                    val srcRegex = Regex("""<(?:video|source)[^>]+src=["']([^"']+)["']""", RegexOption.IGNORE_CASE)
                    val relMatch = srcRegex.find(body)?.groupValues?.getOrNull(1)
                    val resolvedUrl = relMatch?.let { cleanUrl.toHttpUrlOrNull()?.resolve(it)?.toString() }
                    if (resolvedUrl.isNullOrBlank()) return@withContext null
                    return@withContext buildSingleCourseMediaResult(cleanTitle, cleanUrl, hostPlatform, resolvedUrl, thumbUrl)
                }

                val lessons = foundUrls.mapIndexed { idx, mediaUrl ->
                    val probedSize = UrlInspector.probeContentLength(mediaUrl, cleanUrl)
                    val finalBytes = if (probedSize > 0) probedSize else 25_000_000L
                    val ext = mediaUrl.substringBefore('?').substringAfterLast('.', "mp4").lowercase()
                    CourseLessonItem(
                        index = idx + 1,
                        title = "%02d. %s - Ders %d (%s)".format(idx + 1, cleanTitle.take(32), idx + 1, ext.uppercase()),
                        duration = "Modül ${idx + 1}",
                        downloadUrl = mediaUrl,
                        sizeText = if (probedSize > 0) NotificationHelper.formatFileSize(probedSize) else "~24.0 MB",
                        sizeBytes = finalBytes
                    )
                }

                val totalBytes = lessons.sumOf { it.sizeBytes }
                val totalSizeFormatted = NotificationHelper.formatFileSize(totalBytes)
                val primaryUrl = lessons.first().downloadUrl

                val qualities = listOf(
                    MediaQualityOption(
                        id = "course_primary_hd",
                        title = "Ana Ders Videosu / Materyali (${lessons.first().title})",
                        resolution = "Full HD",
                        format = primaryUrl.substringBefore('?').substringAfterLast('.', "MP4").uppercase(),
                        estimatedSize = lessons.first().sizeText,
                        estimatedBytes = lessons.first().sizeBytes,
                        directDownloadUrl = primaryUrl
                    )
                )

                return@withContext MediaInspectResult(
                    title = "$cleanTitle.mp4",
                    originalUrl = cleanUrl,
                    hostPlatform = hostPlatform,
                    fileType = FileType.COURSE,
                    totalSizeText = totalSizeFormatted,
                    totalSizeBytes = totalBytes,
                    mimeType = "video/mp4",
                    supportsMultiThread = true,
                    qualityOptions = qualities,
                    author = hostPlatform,
                    durationText = "${lessons.size} Ders / Materyal",
                    thumbnailUrl = thumbUrl,
                    isCourseBundle = lessons.size > 1,
                    courseLessons = lessons
                )
            }
        } catch (e: Exception) {
            return@withContext null
        }
    }

    private fun buildSingleCourseMediaResult(
        title: String,
        originalUrl: String,
        hostPlatform: String,
        mediaUrl: String,
        thumbUrl: String?
    ): MediaInspectResult {
        val probedBytes = UrlInspector.probeContentLength(mediaUrl, originalUrl)
        val sizeBytes = if (probedBytes > 0) probedBytes else 30_000_000L
        val sizeFormatted = if (probedBytes > 0) NotificationHelper.formatFileSize(probedBytes) else "~28.6 MB"

        return MediaInspectResult(
            title = if (title.endsWith(".mp4", true)) title else "$title.mp4",
            originalUrl = originalUrl,
            hostPlatform = hostPlatform,
            fileType = FileType.COURSE,
            totalSizeText = sizeFormatted,
            totalSizeBytes = sizeBytes,
            mimeType = "video/mp4",
            supportsMultiThread = true,
            qualityOptions = listOf(
                MediaQualityOption(
                    id = "course_single_hd",
                    title = "Kurs Ders Videosu (MP4)",
                    resolution = "1080p HD",
                    format = "MP4 Video",
                    estimatedSize = sizeFormatted,
                    estimatedBytes = sizeBytes,
                    directDownloadUrl = mediaUrl
                )
            ),
            author = hostPlatform,
            durationText = "Kurs Modülü",
            thumbnailUrl = thumbUrl,
            isCourseBundle = false
        )
    }

    private fun decodeHtml(input: String): String {
        return input
            .replace("&amp;", "&")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace("&quot;", "\"")
            .replace("&#39;", "'")
            .replace("&#x2F;", "/")
    }
}
