package com.example.engine.extractors

import com.example.data.model.FileType
import com.example.data.model.MediaInspectResult
import com.example.data.model.MediaQualityOption
import com.example.service.NotificationHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject

object YouTubeExtractor {

    private const val BROWSER_USER_AGENT =
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/128.0.0.0 Safari/537.36"

    private val COBALT_INSTANCES = listOf(
        "https://api.cobalt.tools",
        "https://co.wuk.sh",
        "https://cobalt.hyonsu.com",
        "https://api.wuk.sh"
    )

    private val PIPED_INSTANCES = listOf(
        "https://api.piped.private.coffee",
        "https://pipedapi.adminforge.de",
        "https://pipedapi.kavin.rocks",
        "https://pipedapi.drgns.space"
    )

    private val INVIDIOUS_INSTANCES = listOf(
        "https://inv.thepixora.com",
        "https://invidious.nerdvpn.de",
        "https://yewtu.be",
        "https://vid.puffyan.us"
    )

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

        // Strategy 1: Cobalt Engine (Direct tunnel/stream, bypasses YouTube IP bans)
        val cobaltResult = extractFromCobalt(cleanUrl, videoId, httpClient)
        if (cobaltResult != null) return@withContext cobaltResult

        // Strategy 2: Piped API
        val pipedResult = extractFromPiped(videoId, cleanUrl, httpClient)
        if (pipedResult != null) return@withContext pipedResult

        // Strategy 3: Invidious API
        val invidiousResult = extractFromInvidious(videoId, cleanUrl, httpClient)
        if (invidiousResult != null) return@withContext invidiousResult

        return@withContext null
    }

    private fun extractFromCobalt(
        cleanUrl: String,
        videoId: String,
        httpClient: OkHttpClient
    ): MediaInspectResult? {
        val jsonPayload = JSONObject().apply {
            put("url", cleanUrl)
            put("videoQuality", "1080")
            put("downloadMode", "auto")
            put("youtubeVideoCodec", "h264")
        }.toString()

        val mediaType = "application/json; charset=utf-8".toMediaType()

        for (instance in COBALT_INSTANCES) {
            try {
                val request = Request.Builder()
                    .url(instance)
                    .header("User-Agent", BROWSER_USER_AGENT)
                    .header("Accept", "application/json")
                    .header("Content-Type", "application/json")
                    .post(jsonPayload.toRequestBody(mediaType))
                    .build()

                httpClient.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) return@use
                    val body = response.body?.string() ?: return@use
                    val json = JSONObject(body)

                    val status = json.optString("status")
                    var directUrl = json.optString("url")
                    var rawTitle = json.optString("filename")

                    if (status == "picker") {
                        val picker = json.optJSONArray("picker")
                        if (picker != null && picker.length() > 0) {
                            val first = picker.optJSONObject(0)
                            directUrl = first?.optString("url") ?: ""
                        }
                    }

                    if (directUrl.isNotBlank() && directUrl.startsWith("http")) {
                        val title = if (rawTitle.isNotBlank()) {
                            rawTitle.substringBeforeLast('.')
                        } else {
                            "YouTube_Video_$videoId"
                        }.replace(Regex("""[\\/:*?"<>|\r\n\t]"""), " ").trim()

                        val fileName = if (title.endsWith(".mp4", true)) title else "$title.mp4"
                        val thumb = "https://i.ytimg.com/vi/$videoId/hqdefault.jpg"

                        val qualities = listOf(
                            MediaQualityOption(
                                id = "yt_cobalt_1080p",
                                title = "Full HD Video (1080p MP4)",
                                resolution = "1080p Full HD",
                                format = "MP4 Video",
                                estimatedSize = "~45.0 MB",
                                estimatedBytes = 47_185_920L,
                                directDownloadUrl = directUrl
                            ),
                            MediaQualityOption(
                                id = "yt_cobalt_720p",
                                title = "Optimize HD Video (720p MP4)",
                                resolution = "720p HD",
                                format = "MP4 Video",
                                estimatedSize = "~26.0 MB",
                                estimatedBytes = 27_262_976L,
                                directDownloadUrl = directUrl,
                                targetHeight = 720
                            ),
                            MediaQualityOption(
                                id = "yt_cobalt_audio",
                                title = "Sadece Ses İndir (M4A / AAC)",
                                resolution = "Stereo Audio",
                                format = "M4A Audio",
                                estimatedSize = "~8.5 MB",
                                estimatedBytes = 8_912_896L,
                                isAudioOnly = true,
                                directDownloadUrl = directUrl
                            )
                        )

                        return MediaInspectResult(
                            title = fileName,
                            originalUrl = cleanUrl,
                            hostPlatform = "YouTube Video",
                            fileType = FileType.VIDEO,
                            totalSizeText = "~45.0 MB",
                            totalSizeBytes = 47_185_920L,
                            mimeType = "video/mp4",
                            supportsMultiThread = true,
                            qualityOptions = qualities,
                            author = "YouTube",
                            durationText = "HD Video",
                            thumbnailUrl = thumb
                        )
                    }
                }
            } catch (_: Exception) {
                // try next cobalt instance
            }
        }
        return null
    }

    private fun extractFromPiped(
        videoId: String,
        cleanUrl: String,
        httpClient: OkHttpClient
    ): MediaInspectResult? {
        for (instance in PIPED_INSTANCES) {
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

                    for (i in 0 until videoStreams.length()) {
                        val stream = videoStreams.optJSONObject(i) ?: continue
                        val streamUrl = stream.optString("url")
                        val quality = stream.optString("quality", "1080p")
                        val format = stream.optString("format", "MP4")
                        val cl = stream.optLong("contentLength", -1L)
                        val clFormatted = if (cl > 0) NotificationHelper.formatFileSize(cl) else "~45 MB"

                        if (streamUrl.isNotBlank() && streamUrl.startsWith("http")) {
                            qualities.add(
                                MediaQualityOption(
                                    id = "yt_piped_${quality}_$i",
                                    title = "Video ($quality - $format)",
                                    resolution = quality,
                                    format = "$format Video",
                                    estimatedSize = clFormatted,
                                    estimatedBytes = if (cl > 0) cl else 45_000_000L,
                                    directDownloadUrl = streamUrl
                                )
                            )
                            if (qualities.size >= 2) break
                        }
                    }

                    if (audioStreams.length() > 0) {
                        val audioStream = audioStreams.optJSONObject(0)
                        val audioUrl = audioStream?.optString("url") ?: ""
                        val audioSize = audioStream?.optLong("contentLength", -1L) ?: -1L
                        if (audioUrl.isNotBlank()) {
                            qualities.add(
                                MediaQualityOption(
                                    id = "yt_audio_hq",
                                    title = "Sadece Ses (HQ Audio)",
                                    resolution = "Stereo Audio",
                                    format = "M4A Audio",
                                    estimatedSize = if (audioSize > 0) NotificationHelper.formatFileSize(audioSize) else "~8.5 MB",
                                    estimatedBytes = if (audioSize > 0) audioSize else 8_900_000L,
                                    isAudioOnly = true,
                                    directDownloadUrl = audioUrl
                                )
                            )
                        }
                    }

                    if (qualities.isNotEmpty()) {
                        val safeTitle = title.replace(Regex("""[\\/:*?"<>|\r\n\t]"""), " ").trim()
                        val fileName = if (safeTitle.endsWith(".mp4", true)) safeTitle else "$safeTitle.mp4"

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
            } catch (_: Exception) {
                // try next piped instance
            }
        }
        return null
    }

    private fun extractFromInvidious(
        videoId: String,
        cleanUrl: String,
        httpClient: OkHttpClient
    ): MediaInspectResult? {
        for (instance in INVIDIOUS_INSTANCES) {
            try {
                val request = Request.Builder()
                    .url("$instance/api/v1/videos/$videoId")
                    .header("User-Agent", BROWSER_USER_AGENT)
                    .header("Accept", "application/json")
                    .build()

                httpClient.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) return@use
                    val body = response.body?.string() ?: return@use
                    val json = JSONObject(body)

                    val title = json.optString("title", "YouTube Video")
                    val author = json.optString("author", "YouTube")
                    val formatStreams = json.optJSONArray("formatStreams") ?: JSONArray()
                    val qualities = mutableListOf<MediaQualityOption>()

                    for (i in 0 until formatStreams.length()) {
                        val fs = formatStreams.optJSONObject(i) ?: continue
                        val streamUrl = fs.optString("url")
                        val resolution = fs.optString("resolution", "720p")
                        val container = fs.optString("container", "mp4").uppercase()
                        val size = fs.optLong("size", -1L)

                        if (streamUrl.isNotBlank() && streamUrl.startsWith("http")) {
                            qualities.add(
                                MediaQualityOption(
                                    id = "yt_inv_$i",
                                    title = "Video ($resolution - $container)",
                                    resolution = resolution,
                                    format = "$container Video",
                                    estimatedSize = if (size > 0) NotificationHelper.formatFileSize(size) else "~35 MB",
                                    estimatedBytes = if (size > 0) size else 36_700_160L,
                                    directDownloadUrl = streamUrl
                                )
                            )
                        }
                    }

                    if (qualities.isNotEmpty()) {
                        val safeTitle = title.replace(Regex("""[\\/:*?"<>|\r\n\t]"""), " ").trim()
                        val fileName = if (safeTitle.endsWith(".mp4", true)) safeTitle else "$safeTitle.mp4"

                        return MediaInspectResult(
                            title = fileName,
                            originalUrl = cleanUrl,
                            hostPlatform = "YouTube ($author)",
                            fileType = FileType.VIDEO,
                            totalSizeText = qualities.first().estimatedSize,
                            totalSizeBytes = qualities.first().estimatedBytes,
                            mimeType = "video/mp4",
                            supportsMultiThread = true,
                            qualityOptions = qualities,
                            author = author,
                            durationText = "HD Video",
                            thumbnailUrl = "https://i.ytimg.com/vi/$videoId/hqdefault.jpg"
                        )
                    }
                }
            } catch (_: Exception) {
                // try next invidious instance
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
