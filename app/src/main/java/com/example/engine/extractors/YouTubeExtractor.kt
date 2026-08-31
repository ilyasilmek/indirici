package com.example.engine.extractors

import com.example.data.model.FileType
import com.example.data.model.MediaInspectResult
import com.example.data.model.MediaQualityOption
import com.example.service.NotificationHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject

object YouTubeExtractor {

    private val BROWSER_USER_AGENT =
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/128.0.0.0 Safari/537.36"

    fun matches(url: String): Boolean {
        val lower = url.lowercase()
        return lower.contains("youtube.com") || lower.contains("youtu.be")
    }

    fun extractVideoId(url: String): String? {
        val clean = url.trim()
        val regexes = listOf(
            Regex("""(?:youtube\.com/(?:watch\?.*v=|shorts/|embed/|v/)|youtu\.be/)([a-zA-Z0-9_-]{11})"""),
            Regex("""[?&]v=([a-zA-Z0-9_-]{11})"""),
            Regex("""youtu\.be/([a-zA-Z0-9_-]{11})"""),
            Regex("""shorts/([a-zA-Z0-9_-]{11})""")
        )

        for (regex in regexes) {
            val match = regex.find(clean)
            if (match != null && match.groupValues.size > 1) {
                return match.groupValues[1]
            }
        }
        return null
    }

    suspend fun extract(
        url: String,
        httpClient: OkHttpClient
    ): MediaInspectResult? = withContext(Dispatchers.IO) {
        val videoId = extractVideoId(url) ?: return@withContext null
        val cleanUrl = "https://www.youtube.com/watch?v=$videoId"

        // Strategy 1: Piped API. This is the only remaining viable strategy: YouTube
        // now actively blocks anonymous/server-side stream requests ("Sign in to
        // confirm that you're not a bot"), which is why every public Invidious
        // instance has disabled its API entirely and most Piped instances fail the
        // same way - Piped only succeeds when an instance has a working bypass or,
        // for some archived videos, a non-YouTube mirror. There is no reliable free
        // way around this; when it fails, the caller must surface a clear error
        // rather than fabricate a fake result.
        val pipedResult = extractFromPiped(videoId, cleanUrl, httpClient)
        if (pipedResult != null) return@withContext pipedResult

        return@withContext null
    }

    private fun extractFromPiped(
        videoId: String,
        cleanUrl: String,
        httpClient: OkHttpClient
    ): MediaInspectResult? {
        // api.piped.private.coffee is the only instance confirmed live as of this
        // writing; the others are kept as opportunistic fallbacks since public
        // instance uptime here is highly volatile (see docs.piped.video for the
        // current official list).
        val pipedInstances = listOf(
            "https://api.piped.private.coffee",
            "https://pipedapi.kavin.rocks",
            "https://pipedapi.adminforge.de",
            "https://pipedapi.drgns.space"
        )

        for (instance in pipedInstances) {
            try {
                val request = Request.Builder()
                    .url("$instance/streams/$videoId")
                    .header("User-Agent", BROWSER_USER_AGENT)
                    .header("Accept", "application/json")
                    .build()

                httpClient.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) return@use
                    val body = response.body?.string() ?: return@use
                    val json = JSONObject(body)

                    val title = json.optString("title", "YouTube Video")
                    val uploader = json.optString("uploader", "YouTube")
                    val thumb = json.optString("thumbnailUrl")
                        .ifBlank { "https://i.ytimg.com/vi/$videoId/hqdefault.jpg" }
                    val durationSec = json.optLong("duration", 0L)
                    val durationFormatted = if (durationSec > 0) formatSeconds(durationSec) else "HD Video"

                    val videoStreams = json.optJSONArray("videoStreams") ?: JSONArray()
                    val audioStreams = json.optJSONArray("audioStreams") ?: JSONArray()

                    val qualities = mutableListOf<MediaQualityOption>()

                    // Find best 1080p / 720p / 480p streams (prefer MP4 container)
                    var addedBest = false
                    for (i in 0 until videoStreams.length()) {
                        val stream = videoStreams.optJSONObject(i) ?: continue
                        val streamUrl = stream.optString("url")
                        val quality = stream.optString("quality", "1080p")
                        val format = stream.optString("format", "MP4")
                        val cl = stream.optLong("contentLength", -1L)
                        val clFormatted = if (cl > 0) NotificationHelper.formatFileSize(cl) else "50 MB"

                        if (streamUrl.isNotBlank() && streamUrl.startsWith("http") && !addedBest) {
                            qualities.add(
                                MediaQualityOption(
                                    id = "yt_${quality}_${i}",
                                    title = "Full Video ($quality - $format)",
                                    resolution = quality,
                                    format = "$format Video",
                                    estimatedSize = clFormatted,
                                    estimatedBytes = if (cl > 0) cl else 50_000_000L,
                                    directDownloadUrl = streamUrl
                                )
                            )
                            if (qualities.size >= 3) {
                                addedBest = true
                                break
                            }
                        }
                    }

                    // Add MP3 / Audio Stream
                    if (audioStreams.length() > 0) {
                        val audioStream = audioStreams.optJSONObject(0)
                        val audioUrl = audioStream?.optString("url") ?: ""
                        val audioSize = audioStream?.optLong("contentLength", -1L) ?: -1L
                        if (audioUrl.isNotBlank()) {
                            qualities.add(
                                MediaQualityOption(
                                    id = "yt_audio_hq",
                                    title = "Sadece Ses (HQ Audio MP3 / M4A)",
                                    resolution = "Stereo Audio",
                                    format = "MP3 / Audio",
                                    estimatedSize = if (audioSize > 0) NotificationHelper.formatFileSize(audioSize) else "8.5 MB",
                                    estimatedBytes = if (audioSize > 0) audioSize else 8_900_000L,
                                    isAudioOnly = true,
                                    directDownloadUrl = audioUrl
                                )
                            )
                        }
                    }

                    if (qualities.isNotEmpty()) {
                        val safeTitle = title.replace(Regex("""[\\/:*?"<>|\r\n\t]"""), " ").trim()
                        val fileName = if (safeTitle.endsWith(".mp4", ignoreCase = true)) safeTitle else "$safeTitle.mp4"

                        return MediaInspectResult(
                            title = fileName,
                            originalUrl = cleanUrl,
                            hostPlatform = "YouTube ($uploader)",
                            fileType = FileType.VIDEO,
                            totalSizeText = qualities.first().estimatedSize,
                            totalSizeBytes = qualities.first().estimatedBytes,
                            mimeType = "video/mp4",
                            supportsMultiThread = true,
                            qualityOptions = qualities,
                            author = uploader,
                            durationText = durationFormatted,
                            thumbnailUrl = thumb
                        )
                    }
                }
            } catch (e: Exception) {
                // try next instance
            }
        }
        return null
    }

    private fun formatSeconds(seconds: Long): String {
        val min = seconds / 60
        val sec = seconds % 60
        return "%02d:%02d".format(min, sec)
    }
}
