package com.example.engine.extractors

import com.example.data.model.FileType
import com.example.data.model.MediaInspectResult
import com.example.data.model.MediaQualityOption
import com.example.service.NotificationHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request

object TelegramExtractor {

    private val BROWSER_USER_AGENT =
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/128.0.0.0 Safari/537.36"

    fun matches(url: String): Boolean {
        val lower = url.lowercase()
        return lower.contains("t.me/") || lower.contains("telegram.me/") || lower.contains("telesco.pe/")
    }

    suspend fun extract(
        url: String,
        httpClient: OkHttpClient
    ): MediaInspectResult? = withContext(Dispatchers.IO) {
        val cleanUrl = url.trim()
        val embedUrl = if (cleanUrl.contains("?")) "$cleanUrl&embed=1" else "$cleanUrl?embed=1"

        try {
            val request = Request.Builder()
                .url(embedUrl)
                .header("User-Agent", BROWSER_USER_AGENT)
                .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
                .header("Accept-Language", "en-US,en;q=0.9,tr;q=0.8")
                .build()

            httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@withContext null
                val body = response.body?.string() ?: return@withContext null

                // Extract video stream URL. A post without an inline <video> often still
                // exposes telesco.pe file links for its JPEG preview thumbnails - those
                // must never be accepted here, so every fallback below requires an
                // explicit video extension rather than matching any telesco.pe link.
                val videoSrcRegex = Regex("""<video[^>]+src=["']([^"']+)["']""", RegexOption.IGNORE_CASE)
                val videoExt = """(?:mp4|mov|mkv|webm|m4v)"""
                val docHrefRegex = Regex("""<a[^>]+class=["'][^"']*tgme_widget_message_document_wrap[^"']*["'][^>]+href=["']([^"']+\.$videoExt(?:\?[^"']*)?)["']""", RegexOption.IGNORE_CASE)
                val rawVideoRegex = Regex("""(https://cdn\d*\.telesco\.pe/file/[a-zA-Z0-9_-]+\.$videoExt(?:\?[^"'\s]*)?|https://[a-zA-Z0-9.-]+\.telegram\.org/[^"'\s]+\.$videoExt(?:\?[^"'\s]*)?)""", RegexOption.IGNORE_CASE)

                val videoUrlMatch = videoSrcRegex.find(body) ?: docHrefRegex.find(body) ?: rawVideoRegex.find(body)
                val directVideoUrl = videoUrlMatch?.groupValues?.getOrNull(1) ?: videoUrlMatch?.groupValues?.getOrNull(0)
                    // No genuine video found (e.g. a text/photo-only post) - fail
                    // honestly instead of "downloading" the Telegram post page itself.
                    ?: return@withContext null

                // Extract thumbnail
                val posterRegex = Regex("""<video[^>]+poster=["']([^"']+)["']""", RegexOption.IGNORE_CASE)
                val bgThumbRegex = Regex("""background-image:\s*url\(['"]?([^'"]+)['"]?\)""", RegexOption.IGNORE_CASE)
                val thumbUrl = (posterRegex.find(body)?.groupValues?.getOrNull(1)
                    ?: bgThumbRegex.find(body)?.groupValues?.getOrNull(1))
                    ?.let { if (it.startsWith("//")) "https:$it" else it }

                // Extract channel & title
                val channelNameRegex = Regex("""<div class=["']tgme_widget_message_owner_name["']>[\s\S]*?<span dir=["']auto["']>([^<]+)</span>""", RegexOption.IGNORE_CASE)
                val channelName = channelNameRegex.find(body)?.groupValues?.getOrNull(1)?.trim() ?: "Telegram Kanalı"

                val messageTextRegex = Regex("""<div class=["']tgme_widget_message_text[^"']*["'][^>]*>([\s\S]*?)</div>""", RegexOption.IGNORE_CASE)
                val rawText = messageTextRegex.find(body)?.groupValues?.getOrNull(1) ?: ""
                val cleanText = rawText.replace(Regex("<[^>]+>"), " ").trim().take(60)

                val displayTitle = if (cleanText.isNotBlank()) cleanText else "Telegram_Media_${System.currentTimeMillis() % 100000}"
                val safeTitle = displayTitle.replace(Regex("""[\\/:*?"<>|\r\n\t]"""), " ").trim()
                val fileName = if (safeTitle.endsWith(".mp4", ignoreCase = true)) safeTitle else "$safeTitle.mp4"

                val finalStreamUrl = directVideoUrl

                val qualities = listOf(
                    MediaQualityOption(
                        id = "tg_video_original",
                        title = "Orijinal Telegram Videosu (MP4)",
                        resolution = "HD Video",
                        format = "MP4 Video",
                        estimatedSize = "22.4 MB",
                        estimatedBytes = 23_488_102L,
                        directDownloadUrl = finalStreamUrl
                    ),
                    MediaQualityOption(
                        id = "tg_audio_extract",
                        title = "Sadece Ses (M4A / AAC)",
                        resolution = "Stereo Audio",
                        format = "M4A Audio",
                        estimatedSize = "4.2 MB",
                        estimatedBytes = 4_404_019L,
                        isAudioOnly = true,
                        directDownloadUrl = finalStreamUrl
                    )
                )

                return@withContext MediaInspectResult(
                    title = fileName,
                    originalUrl = cleanUrl,
                    hostPlatform = "Telegram ($channelName)",
                    fileType = FileType.VIDEO,
                    totalSizeText = "22.4 MB",
                    totalSizeBytes = 23_488_102L,
                    mimeType = "video/mp4",
                    supportsMultiThread = true,
                    qualityOptions = qualities,
                    author = channelName,
                    durationText = "Telegram Post",
                    thumbnailUrl = thumbUrl
                )
            }
        } catch (e: Exception) {
            // telegram inspection error
        }
        return@withContext null
    }
}
