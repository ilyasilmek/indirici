package com.example.engine.extractors

import com.example.data.model.FileType
import com.example.data.model.MediaInspectResult
import com.example.data.model.MediaQualityOption
import com.example.engine.UrlInspector
import com.example.service.NotificationHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject

object TwitterExtractor {

    private const val BROWSER_USER_AGENT =
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/128.0.0.0 Safari/537.36"

    fun matches(url: String): Boolean {
        val lower = url.lowercase()
        return lower.contains("twitter.com") || lower.contains("x.com") || lower.contains("vxtwitter.com") || lower.contains("fixupx.com")
    }

    fun extractTweetId(url: String): String? {
        val regex = Regex("""(?:status|statuses)/(\d+)""")
        return regex.find(url)?.groupValues?.getOrNull(1)
    }

    suspend fun extract(
        url: String,
        httpClient: OkHttpClient
    ): MediaInspectResult? = withContext(Dispatchers.IO) {
        val tweetId = extractTweetId(url) ?: return@withContext null
        val cleanUrl = "https://x.com/i/status/$tweetId"

        // Strategy 1: VxTwitter API
        try {
            val vxUrl = "https://api.vxtwitter.com/Twitter/status/$tweetId"
            val request = Request.Builder()
                .url(vxUrl)
                .header("User-Agent", BROWSER_USER_AGENT)
                .header("Accept", "application/json")
                .build()

            httpClient.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    val body = response.body?.string() ?: return@use
                    val json = JSONObject(body)

                    val text = json.optString("text", "X Post").take(60)
                    val userName = json.optString("user_name", "X User")
                    val mediaExtended = json.optJSONArray("media_extended")

                    var videoUrl = ""
                    var thumbUrl: String? = null

                    if (mediaExtended != null && mediaExtended.length() > 0) {
                        for (i in 0 until mediaExtended.length()) {
                            val media = mediaExtended.optJSONObject(i) ?: continue
                            val type = media.optString("type")
                            if (type == "video" || type == "gif") {
                                videoUrl = media.optString("url")
                                thumbUrl = media.optString("thumbnail_url")
                                break
                            }
                        }
                    }

                    if (videoUrl.isBlank()) {
                        val mediaUrls = json.optJSONArray("mediaURLs")
                        if (mediaUrls != null && mediaUrls.length() > 0) {
                            videoUrl = mediaUrls.optString(0)
                        }
                    }

                    if (videoUrl.isNotBlank() && videoUrl.startsWith("http")) {
                        val safeTitle = text.replace(Regex("""[\\/:*?"<>|\r\n\t]"""), " ").trim().ifBlank { "X_Video_$tweetId" }
                        val fileName = if (safeTitle.endsWith(".mp4", ignoreCase = true)) safeTitle else "$safeTitle.mp4"

                        val probedBytes = UrlInspector.probeContentLength(videoUrl, "https://x.com/")
                        val videoBytes = if (probedBytes > 0) probedBytes else 14_889_799L
                        val videoSizeText = if (probedBytes > 0) NotificationHelper.formatFileSize(probedBytes) else "~14.2 MB"
                        val audioBytes = (videoBytes * 0.20).toLong()
                        val audioSizeText = "~${NotificationHelper.formatFileSize(audioBytes)}"

                        val qualities = listOf(
                            MediaQualityOption(
                                id = "x_video_hd",
                                title = "Yüksek Kalite Video (MP4)",
                                resolution = "HD Video",
                                format = "MP4 Video",
                                estimatedSize = videoSizeText,
                                estimatedBytes = videoBytes,
                                directDownloadUrl = videoUrl
                            ),
                            MediaQualityOption(
                                id = "x_video_720p",
                                title = "Optimize Edilmiş Video (720p MP4)",
                                resolution = "720p HD",
                                format = "MP4 Video",
                                estimatedSize = "~${NotificationHelper.formatFileSize((videoBytes * 0.6).toLong())}",
                                estimatedBytes = (videoBytes * 0.6).toLong(),
                                directDownloadUrl = videoUrl,
                                targetHeight = 720
                            ),
                            MediaQualityOption(
                                id = "x_audio_mp3",
                                title = "Sadece Ses (M4A / AAC)",
                                resolution = "Stereo Audio",
                                format = "M4A Audio",
                                estimatedSize = audioSizeText,
                                estimatedBytes = audioBytes,
                                isAudioOnly = true,
                                directDownloadUrl = videoUrl
                            )
                        )

                        return@withContext MediaInspectResult(
                            title = fileName,
                            originalUrl = cleanUrl,
                            hostPlatform = "X / Twitter ($userName)",
                            fileType = FileType.VIDEO,
                            totalSizeText = videoSizeText,
                            totalSizeBytes = videoBytes,
                            mimeType = "video/mp4",
                            supportsMultiThread = true,
                            qualityOptions = qualities,
                            author = userName,
                            durationText = "X Video",
                            thumbnailUrl = thumbUrl
                        )
                    }
                }
            }
        } catch (_: Exception) {
            // vxtwitter failed
        }

        // Strategy 2: Twitter Syndication API (exposes multiple MP4 bitrate variants)
        try {
            val syndicationUrl = "https://cdn.syndication.twimg.com/tweet-result?id=$tweetId&lang=en"
            val request = Request.Builder()
                .url(syndicationUrl)
                .header("User-Agent", BROWSER_USER_AGENT)
                .build()

            httpClient.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    val body = response.body?.string() ?: return@use
                    val json = JSONObject(body)
                    val text = json.optString("text", "X Video").take(60)
                    val userObj = json.optJSONObject("user")
                    val authorName = userObj?.optString("name") ?: "X Creator"

                    val mediaDetails = json.optJSONArray("mediaDetails")
                    if (mediaDetails != null && mediaDetails.length() > 0) {
                        val media = mediaDetails.optJSONObject(0)
                        val videoInfo = media?.optJSONObject("video_info")
                        val variants = videoInfo?.optJSONArray("variants")

                        data class Mp4Variant(val bitrate: Long, val url: String, val resLabel: String)
                        val mp4Variants = mutableListOf<Mp4Variant>()

                        if (variants != null) {
                            for (i in 0 until variants.length()) {
                                val v = variants.optJSONObject(i) ?: continue
                                val ct = v.optString("content_type")
                                val bitrate = v.optLong("bitrate", 0L)
                                val vUrl = v.optString("url")

                                if (ct == "video/mp4" && vUrl.isNotBlank()) {
                                    val resMatch = Regex("""/(\d+x\d+)/""").find(vUrl)?.groupValues?.getOrNull(1) ?: "${bitrate / 1000} kbps"
                                    mp4Variants.add(Mp4Variant(bitrate, vUrl, resMatch))
                                }
                            }
                        }

                        mp4Variants.sortByDescending { it.bitrate }

                        if (mp4Variants.isNotEmpty()) {
                            val safeTitle = text.replace(Regex("""[\\/:*?"<>|\r\n\t]"""), " ").trim().ifBlank { "X_Video_$tweetId" }
                            val fileName = if (safeTitle.endsWith(".mp4", ignoreCase = true)) safeTitle else "$safeTitle.mp4"

                            val bestVariant = mp4Variants.first()
                            val bestProbedBytes = UrlInspector.probeContentLength(bestVariant.url, "https://x.com/")
                            val primaryBytes = if (bestProbedBytes > 0) bestProbedBytes else 16_777_216L
                            val primarySizeText = if (bestProbedBytes > 0) NotificationHelper.formatFileSize(bestProbedBytes) else "~16.0 MB"

                            val qualities = mutableListOf<MediaQualityOption>()
                            mp4Variants.take(3).forEachIndexed { idx, variant ->
                                val vBytes = if (idx == 0) primaryBytes else {
                                    val probed = UrlInspector.probeContentLength(variant.url, "https://x.com/")
                                    if (probed > 0) probed else (primaryBytes * (idx + 1) / mp4Variants.size)
                                }
                                qualities.add(
                                    MediaQualityOption(
                                        id = "x_synd_$idx",
                                        title = if (idx == 0) "En Yüksek Kalite MP4 (${variant.resLabel})" else "Alternatif Kalite MP4 (${variant.resLabel})",
                                        resolution = variant.resLabel,
                                        format = "MP4 Video",
                                        estimatedSize = NotificationHelper.formatFileSize(vBytes),
                                        estimatedBytes = vBytes,
                                        directDownloadUrl = variant.url
                                    )
                                )
                            }

                            qualities.add(
                                MediaQualityOption(
                                    id = "x_synd_audio",
                                    title = "Sadece Ses İndir (M4A / AAC)",
                                    resolution = "Stereo Audio",
                                    format = "M4A Audio",
                                    estimatedSize = "~${NotificationHelper.formatFileSize((primaryBytes * 0.20).toLong())}",
                                    estimatedBytes = (primaryBytes * 0.20).toLong(),
                                    isAudioOnly = true,
                                    directDownloadUrl = bestVariant.url
                                )
                            )

                            return@withContext MediaInspectResult(
                                title = fileName,
                                originalUrl = cleanUrl,
                                hostPlatform = "X / Twitter ($authorName)",
                                fileType = FileType.VIDEO,
                                totalSizeText = primarySizeText,
                                totalSizeBytes = primaryBytes,
                                mimeType = "video/mp4",
                                supportsMultiThread = true,
                                qualityOptions = qualities,
                                author = authorName,
                                durationText = "X Video",
                                thumbnailUrl = media?.optString("media_url_https")
                            )
                        }
                    }
                }
            }
        } catch (_: Exception) {
            // syndication failed
        }

        return@withContext null
    }
}
