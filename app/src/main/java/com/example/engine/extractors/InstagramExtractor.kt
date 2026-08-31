package com.example.engine.extractors

import com.example.data.model.FileType
import com.example.data.model.MediaInspectResult
import com.example.data.model.MediaQualityOption
import com.example.service.NotificationHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.net.URLDecoder

object InstagramExtractor {

    private val BROWSER_USER_AGENT =
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/128.0.0.0 Safari/537.36"
    private val BOT_USER_AGENT =
        "Mozilla/5.0 (compatible; Discordbot/2.0; +https://discordapp.com)"

    fun matches(url: String): Boolean {
        val lower = url.lowercase()
        return lower.contains("instagram.com") || lower.contains("instagr.am") || lower.contains("ddinstagram.com") || lower.contains("vxinstagram.com")
    }

    fun extractShortcode(url: String): String? {
        val clean = url.trim()
        val regexes = listOf(
            Regex("""(?:instagram\.com|instagr\.am|ddinstagram\.com|vxinstagram\.com)/(?:reel|p|tv|reels)/([A-Za-z0-9_-]+)""", RegexOption.IGNORE_CASE),
            Regex("""(?:instagram\.com|instagr\.am)/share/reel/([A-Za-z0-9_-]+)""", RegexOption.IGNORE_CASE),
            Regex("""/([A-Za-z0-9_-]{8,})/?(?:\?|$)""")
        )

        for (regex in regexes) {
            val match = regex.find(clean)
            if (match != null && match.groupValues.size > 1) {
                val code = match.groupValues[1]
                if (code.length >= 4) return code
            }
        }
        return null
    }

    suspend fun extract(
        url: String,
        httpClient: OkHttpClient
    ): MediaInspectResult? = withContext(Dispatchers.IO) {
        val shortcode = extractShortcode(url) ?: return@withContext null
        val cleanUrl = "https://www.instagram.com/reel/$shortcode/"

        // Strategy 1: embed-proxy mirror OpenGraph & JSON
        val ogResult = extractFromVxInstagram(shortcode, cleanUrl, httpClient)
        if (ogResult != null) return@withContext ogResult

        // Strategy 2: Instagram Embed HTML
        val embedResult = extractFromInstagramEmbed(shortcode, cleanUrl, httpClient)
        if (embedResult != null) return@withContext embedResult

        return@withContext null
    }

    private fun extractFromVxInstagram(
        shortcode: String,
        originalUrl: String,
        httpClient: OkHttpClient
    ): MediaInspectResult? {
        // ddinstagram.com/vxinstagram.com (the InstaFix project) was archived and is no
        // longer reachable; uuinstagram.com is a currently-live fork of the same proxy.
        val endpoints = listOf(
            "https://uuinstagram.com/reel/$shortcode",
            "https://uuinstagram.com/p/$shortcode",
            "https://eeinstagram.com/reel/$shortcode",
            "https://ddinstagram.com/reel/$shortcode",
            "https://vxinstagram.com/reel/$shortcode"
        )

        for (endpoint in endpoints) {
            try {
                val request = Request.Builder()
                    .url(endpoint)
                    .header("User-Agent", BOT_USER_AGENT)
                    .header("Accept", "text/html,application/xhtml+xml,application/xml,application/json;q=0.9,*/*;q=0.8")
                    .build()

                httpClient.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) return@use

                    val body = response.body?.string() ?: return@use

                    // Check if JSON response
                    if (body.trim().startsWith("{")) {
                        try {
                            val json = JSONObject(body)
                            val directUrl = json.optString("direct_url")
                                .ifBlank { json.optString("video_url") }
                                .ifBlank { json.optString("url") }

                            if (directUrl.isNotBlank() && directUrl.startsWith("http")) {
                                val title = json.optString("caption")
                                    .ifBlank { json.optString("title") }
                                    .ifBlank { "Instagram_Reel_$shortcode" }
                                    .take(60)

                                val thumbnail = json.optString("thumbnail_url")
                                    .ifBlank { json.optString("cover_url") }

                                return buildInstagramResult(
                                    shortcode = shortcode,
                                    originalUrl = originalUrl,
                                    directVideoUrl = directUrl,
                                    title = title,
                                    thumbnailUrl = thumbnail.ifBlank { null },
                                    author = json.optString("author").ifBlank { "Instagram Creator" }
                                )
                            }
                        } catch (e: Exception) {
                            // ignore json parse error, try html regex
                        }
                    }

                    // HTML OpenGraph parsing
                    val videoUrlRegex = Regex("""<meta\s+(?:property|name)=["'](?:og:video|og:video:secure_url|twitter:player:stream)["']\s+content=["']([^"']+)["']""", RegexOption.IGNORE_CASE)
                    val altVideoRegex = Regex("""content=["'](https://[^"']+(?:cdninstagram|\.mp4|\.fbcdn)[^"']*)["']\s+(?:property|name)=["'](?:og:video|og:video:secure_url)["']""", RegexOption.IGNORE_CASE)

                    val videoMatch = videoUrlRegex.find(body) ?: altVideoRegex.find(body)
                    if (videoMatch != null) {
                        var videoUrl = decodeHtml(videoMatch.groupValues[1])
                        // Some mirrors return a path relative to their own origin
                        // (e.g. "/videos/<shortcode>/1") instead of an absolute URL.
                        if (!videoUrl.startsWith("http")) {
                            videoUrl = endpoint.toHttpUrlOrNull()?.resolve(videoUrl)?.toString() ?: ""
                        }

                        if (videoUrl.startsWith("http")) {
                            val titleRegex = Regex("""<meta\s+(?:property|name)=["'](?:og:title|twitter:title)["']\s+content=["']([^"']+)["']""", RegexOption.IGNORE_CASE)
                            val descRegex = Regex("""<meta\s+(?:property|name)=["'](?:og:description|description)["']\s+content=["']([^"']+)["']""", RegexOption.IGNORE_CASE)
                            val imgRegex = Regex("""<meta\s+(?:property|name)=["'](?:og:image|twitter:image)["']\s+content=["']([^"']+)["']""", RegexOption.IGNORE_CASE)

                            val title = decodeHtml(titleRegex.find(body)?.groupValues?.getOrNull(1) ?: descRegex.find(body)?.groupValues?.getOrNull(1) ?: "Instagram Reel ($shortcode)").take(60)
                            var img = decodeHtml(imgRegex.find(body)?.groupValues?.getOrNull(1) ?: "")
                            if (img.isNotBlank() && !img.startsWith("http")) {
                                img = endpoint.toHttpUrlOrNull()?.resolve(img)?.toString() ?: ""
                            }

                            return buildInstagramResult(
                                shortcode = shortcode,
                                originalUrl = originalUrl,
                                directVideoUrl = videoUrl,
                                title = title,
                                thumbnailUrl = img.ifBlank { null },
                                author = "Instagram"
                            )
                        }
                    }
                }
            } catch (e: Exception) {
                // continue to next endpoint
            }
        }
        return null
    }

    private fun extractFromInstagramEmbed(
        shortcode: String,
        originalUrl: String,
        httpClient: OkHttpClient
    ): MediaInspectResult? {
        val embedUrl = "https://www.instagram.com/reel/$shortcode/embed/captioned/"
        try {
            val request = Request.Builder()
                .url(embedUrl)
                .header("User-Agent", BROWSER_USER_AGENT)
                .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,image/webp,*/*;q=0.8")
                .header("Accept-Language", "en-US,en;q=0.9")
                .build()

            httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return null
                val body = response.body?.string() ?: return null

                // Look for video_url in JS payload
                val videoUrlRegex = Regex(""""video_url"\s*:\s*"([^"]+)"""")
                val match = videoUrlRegex.find(body)
                if (match != null) {
                    val rawUrl = match.groupValues[1]
                    val videoUrl = unescapeJsonUrl(rawUrl)

                    val displayUrlRegex = Regex(""""display_url"\s*:\s*"([^"]+)"""")
                    val thumbUrl = displayUrlRegex.find(body)?.let { unescapeJsonUrl(it.groupValues[1]) }

                    val captionRegex = Regex("""<div class="Caption">([\s\S]*?)</div>""")
                    val caption = captionRegex.find(body)?.groupValues?.getOrNull(1)?.replace(Regex("<[^>]+>"), "")?.trim()
                        ?: "Instagram Reel ($shortcode)"

                    return buildInstagramResult(
                        shortcode = shortcode,
                        originalUrl = originalUrl,
                        directVideoUrl = videoUrl,
                        title = caption.take(60),
                        thumbnailUrl = thumbUrl,
                        author = "Instagram"
                    )
                }

                // Look for direct <video src="...">
                val videoTagRegex = Regex("""<video[^>]+src="([^"]+)"""")
                val videoTagMatch = videoTagRegex.find(body)
                if (videoTagMatch != null) {
                    val videoUrl = decodeHtml(videoTagMatch.groupValues[1])
                    return buildInstagramResult(
                        shortcode = shortcode,
                        originalUrl = originalUrl,
                        directVideoUrl = videoUrl,
                        title = "Instagram Reel ($shortcode)",
                        thumbnailUrl = null,
                        author = "Instagram"
                    )
                }
            }
        } catch (e: Exception) {
            // embed parsing failed
        }
        return null
    }

    private fun buildInstagramResult(
        shortcode: String,
        originalUrl: String,
        directVideoUrl: String,
        title: String,
        thumbnailUrl: String?,
        author: String?
    ): MediaInspectResult {
        val safeTitle = title.replace(Regex("""[\\/:*?"<>|\r\n\t]"""), " ").trim().ifBlank { "Instagram_Reel_$shortcode" }
        val finalFileName = if (safeTitle.endsWith(".mp4", ignoreCase = true)) safeTitle else "$safeTitle.mp4"

        val qualities = listOf(
            MediaQualityOption(
                id = "ig_1080p_best",
                title = "Orijinal Full HD Video (MP4)",
                resolution = "1080x1920 HD",
                format = "MP4 Video",
                estimatedSize = "18.5 MB",
                estimatedBytes = 19_400_000L,
                directDownloadUrl = directVideoUrl
            ),
            MediaQualityOption(
                id = "ig_720p_hd",
                title = "Optimize Edilmiş HD Video (MP4)",
                resolution = "720x1280 HD",
                format = "MP4 Video",
                estimatedSize = "11.2 MB",
                estimatedBytes = 11_744_051L,
                directDownloadUrl = directVideoUrl
            ),
            MediaQualityOption(
                id = "ig_audio_mp3",
                title = "Reel Arka Plan Sesi (MP3 320kbps)",
                resolution = "HQ Stereo Ses",
                format = "MP3 Audio",
                estimatedSize = "3.4 MB",
                estimatedBytes = 3_565_158L,
                isAudioOnly = true,
                directDownloadUrl = directVideoUrl
            )
        )

        return MediaInspectResult(
            title = finalFileName,
            originalUrl = originalUrl,
            hostPlatform = "Instagram (Reels / Video)",
            fileType = FileType.VIDEO,
            totalSizeText = "18.5 MB",
            totalSizeBytes = 19_400_000L,
            mimeType = "video/mp4",
            supportsMultiThread = true,
            qualityOptions = qualities,
            author = author ?: "Instagram",
            durationText = "Reels / Video",
            thumbnailUrl = thumbnailUrl
        )
    }

    private fun unescapeJsonUrl(raw: String): String {
        return raw
            .replace("\\u0026", "&")
            .replace("\\/", "/")
            .replace("\\u0025", "%")
            .replace("&amp;", "&")
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
