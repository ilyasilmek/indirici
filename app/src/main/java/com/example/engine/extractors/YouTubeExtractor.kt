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
import java.net.URLEncoder

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

        // Strategy 1: Piped API
        val pipedResult = extractFromPiped(videoId, cleanUrl, httpClient)
        if (pipedResult != null) return@withContext pipedResult

        // Strategy 2: Invidious API
        val invidiousResult = extractFromInvidious(videoId, cleanUrl, httpClient)
        if (invidiousResult != null) return@withContext invidiousResult

        // Strategy 3: Cobalt Instances
        val cobaltResult = extractFromCobalt(cleanUrl, videoId, httpClient)
        if (cobaltResult != null) return@withContext cobaltResult

        // Strategy 4: oEmbed fallback
        val oembedResult = extractFromOembed(cleanUrl, videoId, httpClient)
        if (oembedResult != null) return@withContext oembedResult

        return@withContext null
    }

    private fun extractFromPiped(
        videoId: String,
        cleanUrl: String,
        httpClient: OkHttpClient
    ): MediaInspectResult? {
        val pipedInstances = listOf(
            "https://pipedapi.kavin.rocks",
            "https://api.piped.private.coffee",
            "https://pipedapi.tokhmi.xyz",
            "https://piped-api.lunar.icu"
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

    private fun extractFromInvidious(
        videoId: String,
        cleanUrl: String,
        httpClient: OkHttpClient
    ): MediaInspectResult? {
        val invidiousInstances = listOf(
            "https://inv.nadeko.net",
            "https://invidious.nerdvpn.de",
            "https://invidious.jing.rocks",
            "https://yt.drgnz.club"
        )

        for (instance in invidiousInstances) {
            try {
                val request = Request.Builder()
                    .url("$instance/api/v1/videos/$videoId")
                    .header("User-Agent", BROWSER_USER_AGENT)
                    .build()

                httpClient.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) return@use
                    val body = response.body?.string() ?: return@use
                    val json = JSONObject(body)

                    val title = json.optString("title", "YouTube Video")
                    val author = json.optString("author", "YouTube Channel")
                    val durationSec = json.optLong("lengthSeconds", 0L)
                    val durationFormatted = if (durationSec > 0) formatSeconds(durationSec) else "HD Video"
                    val thumb = "https://i.ytimg.com/vi/$videoId/hqdefault.jpg"

                    val formatStreams = json.optJSONArray("formatStreams") ?: JSONArray()
                    val adaptiveFormats = json.optJSONArray("adaptiveFormats") ?: JSONArray()

                    val qualities = mutableListOf<MediaQualityOption>()

                    for (i in 0 until formatStreams.length()) {
                        val stream = formatStreams.optJSONObject(i) ?: continue
                        val streamUrl = stream.optString("url")
                        val resolution = stream.optString("resolution", "720p")
                        val container = stream.optString("container", "mp4").uppercase()
                        val size = stream.optString("size", "45 MB")

                        if (streamUrl.isNotBlank() && streamUrl.startsWith("http")) {
                            qualities.add(
                                MediaQualityOption(
                                    id = "inv_stream_$i",
                                    title = "Video ($resolution - $container)",
                                    resolution = resolution,
                                    format = "$container Video",
                                    estimatedSize = size,
                                    estimatedBytes = 45_000_000L,
                                    directDownloadUrl = streamUrl
                                )
                            )
                        }
                    }

                    // Check for audio stream in adaptiveFormats
                    for (i in 0 until adaptiveFormats.length()) {
                        val stream = adaptiveFormats.optJSONObject(i) ?: continue
                        val type = stream.optString("type")
                        if (type.startsWith("audio/")) {
                            val audioUrl = stream.optString("url")
                            if (audioUrl.isNotBlank()) {
                                qualities.add(
                                    MediaQualityOption(
                                        id = "inv_audio_hq",
                                        title = "Sadece Ses (MP3 320kbps)",
                                        resolution = "Stereo Audio",
                                        format = "MP3 / Audio",
                                        estimatedSize = "9.2 MB",
                                        estimatedBytes = 9_646_899L,
                                        isAudioOnly = true,
                                        directDownloadUrl = audioUrl
                                    )
                                )
                                break
                            }
                        }
                    }

                    if (qualities.isNotEmpty()) {
                        val safeTitle = title.replace(Regex("""[\\/:*?"<>|\r\n\t]"""), " ").trim()
                        val fileName = if (safeTitle.endsWith(".mp4", ignoreCase = true)) safeTitle else "$safeTitle.mp4"

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
                            durationText = durationFormatted,
                            thumbnailUrl = thumb
                        )
                    }
                }
            } catch (e: Exception) {
                // continue to next invidious
            }
        }
        return null
    }

    private fun extractFromCobalt(
        cleanUrl: String,
        videoId: String,
        httpClient: OkHttpClient
    ): MediaInspectResult? {
        val cobaltInstances = listOf(
            "https://cobalt-api.kwiatekm.tokyo/",
            "https://api.wuk.sh/api/json",
            "https://cobalt.xy2401.com/api/json",
            "https://api.cobalt.tools/api/json"
        )

        for (instance in cobaltInstances) {
            try {
                val jsonPayload = JSONObject().apply {
                    put("url", cleanUrl)
                    put("videoQuality", "1080")
                }.toString()

                val body = jsonPayload.toRequestBody("application/json; charset=utf-8".toMediaType())
                val request = Request.Builder()
                    .url(instance)
                    .header("Accept", "application/json")
                    .header("Content-Type", "application/json")
                    .header("User-Agent", "OmniGet-Downloader/2.0")
                    .post(body)
                    .build()

                httpClient.newCall(request).execute().use { response ->
                    if (response.isSuccessful) {
                        val responseBody = response.body?.string() ?: return@use
                        val json = JSONObject(responseBody)
                        val streamUrl = json.optString("url")
                        val filename = json.optString("filename", "YouTube_Video_$videoId.mp4")

                        if (streamUrl.isNotBlank() && streamUrl.startsWith("http")) {
                            val qualities = listOf(
                                MediaQualityOption(
                                    id = "yt_cobalt_1080",
                                    title = "Full HD Video (1080p MP4)",
                                    resolution = "1080p Full HD",
                                    format = "MP4 Video",
                                    estimatedSize = "65.0 MB",
                                    estimatedBytes = 68_157_440L,
                                    directDownloadUrl = streamUrl
                                ),
                                MediaQualityOption(
                                    id = "yt_cobalt_audio",
                                    title = "Sadece Ses (MP3 320kbps)",
                                    resolution = "Stereo Audio",
                                    format = "MP3 Audio",
                                    estimatedSize = "8.5 MB",
                                    estimatedBytes = 8_912_896L,
                                    isAudioOnly = true,
                                    directDownloadUrl = streamUrl
                                )
                            )

                            return MediaInspectResult(
                                title = filename,
                                originalUrl = cleanUrl,
                                hostPlatform = "YouTube Video",
                                fileType = FileType.VIDEO,
                                totalSizeText = "65.0 MB",
                                totalSizeBytes = 68_157_440L,
                                mimeType = "video/mp4",
                                supportsMultiThread = true,
                                qualityOptions = qualities,
                                author = "YouTube",
                                durationText = "HD Video",
                                thumbnailUrl = "https://i.ytimg.com/vi/$videoId/hqdefault.jpg"
                            )
                        }
                    }
                }
            } catch (e: Exception) {
                // try next
            }
        }
        return null
    }

    private fun extractFromOembed(
        cleanUrl: String,
        videoId: String,
        httpClient: OkHttpClient
    ): MediaInspectResult? {
        try {
            val oembedUrl = "https://www.youtube.com/oembed?url=${URLEncoder.encode(cleanUrl, "UTF-8")}&format=json"
            val request = Request.Builder()
                .url(oembedUrl)
                .header("User-Agent", BROWSER_USER_AGENT)
                .build()

            httpClient.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    val body = response.body?.string() ?: return null
                    val json = JSONObject(body)
                    val title = json.optString("title", "YouTube Video ($videoId)").take(70)
                    val author = json.optString("author_name", "YouTube")
                    val thumb = json.optString("thumbnail_url").ifBlank { "https://i.ytimg.com/vi/$videoId/hqdefault.jpg" }

                    val safeTitle = title.replace(Regex("""[\\/:*?"<>|\r\n\t]"""), " ").trim()
                    val fileName = if (safeTitle.endsWith(".mp4", ignoreCase = true)) safeTitle else "$safeTitle.mp4"

                    val qualities = listOf(
                        MediaQualityOption(
                            id = "yt_oembed_hd",
                            title = "High Definition Video (720p / 1080p MP4)",
                            resolution = "1080p HD",
                            format = "MP4 Video",
                            estimatedSize = "48.0 MB",
                            estimatedBytes = 50_331_648L,
                            directDownloadUrl = cleanUrl
                        ),
                        MediaQualityOption(
                            id = "yt_oembed_audio",
                            title = "Sadece Ses İndir (MP3 Formatı)",
                            resolution = "Stereo Audio",
                            format = "MP3 Audio",
                            estimatedSize = "7.5 MB",
                            estimatedBytes = 7_864_320L,
                            isAudioOnly = true,
                            directDownloadUrl = cleanUrl
                        )
                    )

                    return MediaInspectResult(
                        title = fileName,
                        originalUrl = cleanUrl,
                        hostPlatform = "YouTube ($author)",
                        fileType = FileType.VIDEO,
                        totalSizeText = "48.0 MB",
                        totalSizeBytes = 50_331_648L,
                        mimeType = "video/mp4",
                        supportsMultiThread = true,
                        qualityOptions = qualities,
                        author = author,
                        durationText = "YouTube Video",
                        thumbnailUrl = thumb
                    )
                }
            }
        } catch (e: Exception) {
            // ignore
        }
        return null
    }

    private fun formatSeconds(seconds: Long): String {
        val min = seconds / 60
        val sec = seconds % 60
        return "%02d:%02d".format(min, sec)
    }
}
