package com.example.engine.extractors

import com.example.data.model.FileType
import com.example.data.model.MediaInspectResult
import com.example.data.model.MediaQualityOption
import com.example.service.NotificationHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.net.URLEncoder

object TikTokExtractor {

    private val BROWSER_USER_AGENT =
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/128.0.0.0 Safari/537.36"

    fun matches(url: String): Boolean {
        val lower = url.lowercase()
        return lower.contains("tiktok.com") || lower.contains("douyin.com")
    }

    suspend fun extract(
        url: String,
        httpClient: OkHttpClient
    ): MediaInspectResult? = withContext(Dispatchers.IO) {
        val cleanUrl = url.trim()

        try {
            val tikwmUrl = "https://www.tikwm.com/api/?url=${URLEncoder.encode(cleanUrl, "UTF-8")}"
            val request = Request.Builder()
                .url(tikwmUrl)
                .header("User-Agent", BROWSER_USER_AGENT)
                .header("Accept", "application/json")
                .build()

            httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@use
                val body = response.body?.string() ?: return@use
                val json = JSONObject(body)

                if (json.optInt("code", -1) == 0) {
                    val data = json.optJSONObject("data") ?: return@use
                    val noWatermarkUrl = data.optString("play")
                    val watermarkUrl = data.optString("wmplay")
                    val musicUrl = data.optString("music")
                    val title = data.optString("title", "TikTok Video").take(60)
                    val cover = data.optString("cover")
                    val authorObj = data.optJSONObject("author")
                    val author = authorObj?.optString("nickname") ?: "TikTok Creator"
                    val sizeBytes = data.optLong("size", 14_000_000L)
                    val sizeFormatted = NotificationHelper.formatFileSize(sizeBytes)
                    val duration = data.optInt("duration", 0)

                    val directPlayUrl = if (noWatermarkUrl.startsWith("http")) {
                        noWatermarkUrl
                    } else {
                        "https://www.tikwm.com$noWatermarkUrl"
                    }

                    val qualities = mutableListOf<MediaQualityOption>()
                    qualities.add(
                        MediaQualityOption(
                            id = "tt_nowm_hd",
                            title = "Filigransız HD Video (No Watermark MP4)",
                            resolution = "1080x1920 HD",
                            format = "MP4 Video",
                            estimatedSize = sizeFormatted,
                            estimatedBytes = sizeBytes,
                            directDownloadUrl = directPlayUrl
                        )
                    )

                    if (musicUrl.isNotBlank()) {
                        val fullMusicUrl = if (musicUrl.startsWith("http")) musicUrl else "https://www.tikwm.com$musicUrl"
                        qualities.add(
                            MediaQualityOption(
                                id = "tt_audio_mp3",
                                title = "Orijinal Arka Plan Müziği (MP3)",
                                resolution = "Stereo Audio",
                                format = "MP3 Audio",
                                estimatedSize = "3.2 MB",
                                estimatedBytes = 3_355_443L,
                                isAudioOnly = true,
                                directDownloadUrl = fullMusicUrl
                            )
                        )
                    }

                    val safeTitle = title.replace(Regex("""[\\/:*?"<>|\r\n\t]"""), " ").trim().ifBlank { "TikTok_Video" }
                    val fileName = if (safeTitle.endsWith(".mp4", ignoreCase = true)) safeTitle else "$safeTitle.mp4"

                    return@withContext MediaInspectResult(
                        title = fileName,
                        originalUrl = cleanUrl,
                        hostPlatform = "TikTok ($author)",
                        fileType = FileType.VIDEO,
                        totalSizeText = sizeFormatted,
                        totalSizeBytes = sizeBytes,
                        mimeType = "video/mp4",
                        supportsMultiThread = true,
                        qualityOptions = qualities,
                        author = author,
                        durationText = if (duration > 0) "${duration}s" else "TikTok Video",
                        thumbnailUrl = if (cover.startsWith("http")) cover else "https://www.tikwm.com$cover"
                    )
                }
            }
        } catch (e: Exception) {
            // tikwm failed
        }
        return@withContext null
    }
}
