package com.example.engine.extractors

import com.example.data.model.FileType
import com.example.data.model.MediaInspectResult
import com.example.data.model.MediaQualityOption
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject

object CobaltExtractor {

    private val COBALT_INSTANCES = listOf(
        "https://cobalt-api.kwiatekm.tokyo/",
        "https://api.wuk.sh/api/json",
        "https://cobalt.xy2401.com/api/json",
        "https://api.cobalt.tools/api/json"
    )

    suspend fun extract(
        url: String,
        httpClient: OkHttpClient
    ): MediaInspectResult? = withContext(Dispatchers.IO) {
        val cleanUrl = url.trim()

        for (instance in COBALT_INSTANCES) {
            try {
                val jsonPayload = JSONObject().apply {
                    put("url", cleanUrl)
                    put("videoQuality", "1080")
                    put("filenamePattern", "basic")
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
                        val status = json.optString("status")

                        val streamUrl = when (status) {
                            "stream", "redirect", "tunnel" -> json.optString("url")
                            "picker" -> {
                                val picker = json.optJSONArray("picker")
                                picker?.optJSONObject(0)?.optString("url") ?: ""
                            }
                            else -> json.optString("url")
                        }

                        val filename = json.optString("filename", "OmniGet_Media_${System.currentTimeMillis() % 100000}.mp4")

                        if (streamUrl.isNotBlank() && streamUrl.startsWith("http")) {
                            val isAudio = filename.endsWith(".mp3", ignoreCase = true) || filename.endsWith(".m4a", ignoreCase = true)
                            val fileType = if (isAudio) FileType.AUDIO else FileType.VIDEO

                            val qualities = listOf(
                                MediaQualityOption(
                                    id = "cobalt_hq",
                                    title = if (isAudio) "Orijinal Yüksek Kalite Ses (MP3)" else "Orijinal Yüksek Çözünürlüklü Video (MP4)",
                                    resolution = if (isAudio) "HQ Audio" else "1080p Full HD",
                                    format = if (isAudio) "MP3 Audio" else "MP4 Video",
                                    estimatedSize = if (isAudio) "9.5 MB" else "38.0 MB",
                                    estimatedBytes = if (isAudio) 9_961_472L else 39_845_888L,
                                    isAudioOnly = isAudio,
                                    directDownloadUrl = streamUrl
                                ),
                                MediaQualityOption(
                                    id = "cobalt_audio",
                                    title = "Sadece Ses İndir (MP3)",
                                    resolution = "Stereo Audio",
                                    format = "MP3 Audio",
                                    estimatedSize = "6.2 MB",
                                    estimatedBytes = 6_501_171L,
                                    isAudioOnly = true,
                                    directDownloadUrl = streamUrl
                                )
                            )

                            return@withContext MediaInspectResult(
                                title = filename,
                                originalUrl = cleanUrl,
                                hostPlatform = "OmniGet Medya Ayrıştırıcı",
                                fileType = fileType,
                                totalSizeText = if (isAudio) "9.5 MB" else "38.0 MB",
                                totalSizeBytes = if (isAudio) 9_961_472L else 39_845_888L,
                                mimeType = if (isAudio) "audio/mpeg" else "video/mp4",
                                supportsMultiThread = true,
                                qualityOptions = qualities,
                                author = "Medya Servisi",
                                durationText = "Akış Medyası",
                                thumbnailUrl = null
                            )
                        }
                    }
                }
            } catch (e: Exception) {
                // try next cobalt instance
            }
        }
        return@withContext null
    }
}
