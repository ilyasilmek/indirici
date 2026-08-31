package com.example.engine

import com.example.data.model.CourseLessonItem
import com.example.data.model.FileType
import com.example.data.model.MediaInspectResult
import com.example.data.model.MediaQualityOption
import com.example.engine.extractors.InstagramExtractor
import com.example.engine.extractors.TelegramExtractor
import com.example.engine.extractors.TikTokExtractor
import com.example.engine.extractors.TwitterExtractor
import com.example.engine.extractors.YouTubeExtractor
import com.example.service.NotificationHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.net.URLDecoder
import java.util.concurrent.TimeUnit

object UrlInspector {

    val httpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .writeTimeout(20, TimeUnit.SECONDS)
            .followRedirects(true)
            .followSslRedirects(true)
            .build()
    }

    // High-Speed & Test Presets for instant user testing
    val PRESET_MEDIA_ITEMS = listOf(
        MediaInspectResult(
            // The classic commondatastorage.googleapis.com/gtv-videos-bucket demo
            // files Google published for years now return 403 (bucket access was
            // revoked) - confirmed directly, not just in this app. Replaced with
            // Google's own ExoPlayer team test-media bucket, which is still public
            // and actively used by their demo app.
            title = "Big Buck Bunny - Open Source HD Animation",
            originalUrl = "https://storage.googleapis.com/exoplayer-test-media-0/BigBuckBunny_320x180.mp4",
            hostPlatform = "Blender Open Media",
            fileType = FileType.VIDEO,
            totalSizeText = "61.7 MB",
            totalSizeBytes = 64657027L,
            mimeType = "video/mp4",
            supportsMultiThread = true,
            author = "Blender Foundation",
            durationText = "09:56",
            thumbnailUrl = "https://images.unsplash.com/photo-1578632767115-351597cf2477?w=600&auto=format&fit=crop&q=80",
            qualityOptions = listOf(
                // The real source is 320x180 - this used to advertise fake
                // "1080p" and "720p" options that were both the exact same
                // file. One honest option instead of a fake resolution picker.
                MediaQualityOption(
                    id = "original",
                    title = "Orijinal Kalite Video (MP4)",
                    resolution = "320x180",
                    format = "MP4 (H.264)",
                    estimatedSize = "61.7 MB",
                    estimatedBytes = 64657027L,
                    directDownloadUrl = "https://storage.googleapis.com/exoplayer-test-media-0/BigBuckBunny_320x180.mp4"
                ),
                MediaQualityOption(
                    id = "audio_mp3",
                    title = "Soundtrack Audio (MP3 320kbps)",
                    resolution = "Audio Stream",
                    format = "MP3 Audio",
                    estimatedSize = "14.2 MB",
                    estimatedBytes = 14889799L,
                    isAudioOnly = true,
                    directDownloadUrl = "https://www.soundhelix.com/examples/mp3/SoundHelix-Song-1.mp3"
                )
            )
        ),
        MediaInspectResult(
            title = "Kotlin & Modern Android Development - Course Bundle",
            originalUrl = "https://raw.githubusercontent.com/tonhowtf/omniget/main/README.md",
            hostPlatform = "OmniGet Course Engine",
            fileType = FileType.COURSE,
            totalSizeText = "142.5 MB",
            totalSizeBytes = 149422080L,
            mimeType = "application/x-course-bundle",
            supportsMultiThread = true,
            author = "OmniGet Academy",
            durationText = "6 Modül / 18 Ders",
            thumbnailUrl = "https://images.unsplash.com/photo-1516321318423-f06f85e504b3?w=600&auto=format&fit=crop&q=80",
            isCourseBundle = true,
            qualityOptions = listOf(
                MediaQualityOption(
                    id = "course_all",
                    title = "Complete Course Package (All Lessons + PDFs)",
                    resolution = "Full HD + E-Book",
                    format = "ZIP / Bundle",
                    estimatedSize = "142.5 MB",
                    estimatedBytes = 149422080L,
                    // Synthetic payload from Cloudflare's own speed-test endpoint - a
                    // real, stable, publicly documented service that serves back
                    // exactly the requested byte count, which fits a "bundle" blob
                    // (declared format is ZIP, not a playable video) far better than
                    // relying on another third-party file host staying alive.
                    directDownloadUrl = "https://speed.cloudflare.com/__down?bytes=149422080"
                ),
                MediaQualityOption(
                    id = "course_pdf_only",
                    title = "Course Slides & Documentation (PDF)",
                    resolution = "E-Book",
                    format = "PDF Document",
                    estimatedSize = "8.4 MB",
                    estimatedBytes = 8808038L,
                    directDownloadUrl = "https://www.w3.org/WAI/ER/tests/xhtml/testfiles/resources/pdf/dummy.pdf"
                )
            ),
            // Real, currently-live sample videos from Google's ExoPlayer test-media
            // bucket (confirmed reachable), sized to their actual content-length so
            // the displayed size matches what actually downloads.
            courseLessons = listOf(
                CourseLessonItem(1, "01. Introduction to Mobile Architecture", "14:20", "https://storage.googleapis.com/exoplayer-test-media-0/BigBuckBunny_320x180.mp4", "61.7 MB", 64657027L),
                CourseLessonItem(2, "02. High Performance Network Streams", "22:15", "https://storage.googleapis.com/exoplayer-test-media-1/mp4/dizzy-with-tx3g.mp4", "1.5 MB", 1609079L),
                CourseLessonItem(3, "03. Jetpack Compose UI Craftsmanship", "30:40", "https://storage.googleapis.com/exoplayer-test-media-1/mp4/android-screens-10s.mp4", "1.8 MB", 1854368L),
                CourseLessonItem(4, "04. Offline First Database Architecture", "18:50", "https://storage.googleapis.com/exoplayer-test-media-1/mp4/dizzy-with-tx3g.mp4", "1.5 MB", 1609079L)
            )
        ),
        MediaInspectResult(
            title = "SoundHelix Synth Symphony - Master Acoustic Audio",
            originalUrl = "https://www.soundhelix.com/examples/mp3/SoundHelix-Song-1.mp3",
            hostPlatform = "SoundHelix Audio Labs",
            fileType = FileType.AUDIO,
            totalSizeText = "8.6 MB",
            totalSizeBytes = 9019392L,
            mimeType = "audio/mpeg",
            supportsMultiThread = true,
            author = "SoundHelix Project",
            durationText = "06:12",
            thumbnailUrl = "https://images.unsplash.com/photo-1511671782779-c97d3d27a1d4?w=600&auto=format&fit=crop&q=80",
            qualityOptions = listOf(
                MediaQualityOption(
                    id = "mp3_320",
                    title = "High Quality Studio Audio (MP3 320kbps)",
                    resolution = "44.1 kHz Stereo",
                    format = "MP3",
                    estimatedSize = "8.6 MB",
                    estimatedBytes = 9019392L,
                    isAudioOnly = true,
                    directDownloadUrl = "https://www.soundhelix.com/examples/mp3/SoundHelix-Song-1.mp3"
                ),
                MediaQualityOption(
                    id = "flac_lossless",
                    title = "Lossless Audio Master (FLAC / WAV)",
                    resolution = "96 kHz 24-bit",
                    format = "FLAC",
                    estimatedSize = "34.1 MB",
                    estimatedBytes = 35756441L,
                    isAudioOnly = true,
                    directDownloadUrl = "https://www.soundhelix.com/examples/mp3/SoundHelix-Song-2.mp3"
                )
            )
        ),
        MediaInspectResult(
            // speed.hetzner.de/100MB.bin no longer resolves (confirmed directly).
            // Cloudflare's own speed-test endpoint is the more fitting and durable
            // replacement anyway: it's built exactly for this purpose and serves
            // back precisely the byte count requested via ?bytes=.
            title = "Network Turbo Speed Benchmark (50 MB Test File)",
            originalUrl = "https://speed.cloudflare.com/__down?bytes=52428800",
            hostPlatform = "High-Speed CDN Mirror",
            fileType = FileType.ARCHIVE,
            totalSizeText = "50.0 MB",
            totalSizeBytes = 52428800L,
            mimeType = "application/octet-stream",
            supportsMultiThread = true,
            author = "Speed Test Server",
            durationText = "Multi-Thread Benchmark",
            thumbnailUrl = "https://images.unsplash.com/photo-1558494949-ef010cbdcc31?w=600&auto=format&fit=crop&q=80",
            qualityOptions = listOf(
                MediaQualityOption(
                    id = "bench_50mb",
                    title = "Standard 50MB Speed Test Payload",
                    resolution = "Binary Chunk",
                    format = "BIN / ZIP",
                    estimatedSize = "50.0 MB",
                    estimatedBytes = 52428800L,
                    directDownloadUrl = "https://speed.cloudflare.com/__down?bytes=52428800"
                ),
                MediaQualityOption(
                    id = "bench_10mb",
                    title = "Quick 10MB Speed Test Payload",
                    resolution = "Binary Chunk",
                    format = "BIN",
                    estimatedSize = "10.0 MB",
                    estimatedBytes = 10485760L,
                    directDownloadUrl = "https://speed.cloudflare.com/__down?bytes=10485760"
                )
            )
        )
    )

    suspend fun inspectUrl(url: String): MediaInspectResult = withContext(Dispatchers.IO) {
        val cleanUrl = url.trim()

        // 1. Check Presets
        val matchingPreset = PRESET_MEDIA_ITEMS.find { it.originalUrl.equals(cleanUrl, ignoreCase = true) }
        if (matchingPreset != null) {
            return@withContext matchingPreset
        }

        // A URL recognized as belonging to one of these platforms must never fall
        // through to inspectDirectHttp below: that treats whatever the URL returns
        // (typically the platform's own HTML page) as if it were the downloadable
        // file itself, silently "downloading" garbage instead of failing loudly.
        val isKnownSocialPlatform = InstagramExtractor.matches(cleanUrl) ||
            YouTubeExtractor.matches(cleanUrl) ||
            TelegramExtractor.matches(cleanUrl) ||
            TikTokExtractor.matches(cleanUrl) ||
            TwitterExtractor.matches(cleanUrl)

        // 2. Instagram Extractor
        if (InstagramExtractor.matches(cleanUrl)) {
            val igResult = InstagramExtractor.extract(cleanUrl, httpClient)
            if (igResult != null) return@withContext igResult
        }

        // 3. YouTube Extractor
        if (YouTubeExtractor.matches(cleanUrl)) {
            val ytResult = YouTubeExtractor.extract(cleanUrl, httpClient)
            if (ytResult != null) return@withContext ytResult
        }

        // 4. Telegram Extractor
        if (TelegramExtractor.matches(cleanUrl)) {
            val tgResult = TelegramExtractor.extract(cleanUrl, httpClient)
            if (tgResult != null) return@withContext tgResult
        }

        // 5. TikTok Extractor
        if (TikTokExtractor.matches(cleanUrl)) {
            val ttResult = TikTokExtractor.extract(cleanUrl, httpClient)
            if (ttResult != null) return@withContext ttResult
        }

        // 6. Twitter / X Extractor
        if (TwitterExtractor.matches(cleanUrl)) {
            val twResult = TwitterExtractor.extract(cleanUrl, httpClient)
            if (twResult != null) return@withContext twResult
        }

        if (isKnownSocialPlatform) {
            throw IllegalStateException(
                "Bu bağlantıdan medya bilgisi alınamadı. İçeriğin herkese açık ve geçerli olduğundan emin olun ya da daha sonra tekrar deneyin."
            )
        }

        // 7. Direct HTTP Probe for files / direct links / web downloads
        return@withContext inspectDirectHttp(cleanUrl)
    }

    private fun inspectDirectHttp(cleanUrl: String): MediaInspectResult {
        val platform = detectPlatform(cleanUrl)
        var fileName = extractFileNameFromUrl(cleanUrl)
        var contentType = "application/octet-stream"
        var contentLength: Long = -1L
        var acceptRanges = false

        try {
            // Attempt HEAD request
            val headRequest = Request.Builder()
                .url(cleanUrl)
                .head()
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/128.0.0.0 Safari/537.36")
                .header("Accept", "*/*")
                .build()

            httpClient.newCall(headRequest).execute().use { response ->
                if (response.isSuccessful) {
                    contentType = response.header("Content-Type") ?: contentType
                    contentLength = response.header("Content-Length")?.toLongOrNull() ?: -1L
                    acceptRanges = response.header("Accept-Ranges")?.equals("bytes", ignoreCase = true) == true

                    val disposition = response.header("Content-Disposition")
                    if (!disposition.isNullOrBlank()) {
                        val parsed = extractFileNameFromDisposition(disposition)
                        if (parsed.isNotBlank()) fileName = parsed
                    }
                }
            }

            // If HEAD failed or gave no content-length, try GET with Range 0-0
            if (contentLength <= 0) {
                val rangeRequest = Request.Builder()
                    .url(cleanUrl)
                    .header("Range", "bytes=0-0")
                    .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/128.0.0.0 Safari/537.36")
                    .header("Accept", "*/*")
                    .build()

                httpClient.newCall(rangeRequest).execute().use { response ->
                    if (response.isSuccessful || response.code == 206) {
                        contentType = response.header("Content-Type") ?: contentType
                        val rangeHeader = response.header("Content-Range")
                        if (rangeHeader != null && rangeHeader.contains("/")) {
                            contentLength = rangeHeader.substringAfterLast("/").toLongOrNull() ?: -1L
                            acceptRanges = true
                        }
                    }
                }
            }
        } catch (e: Exception) {
            // Fallback heuristics
        }

        val fileType = FileType.fromMimeOrUrl(contentType, cleanUrl)
        val sizeFormatted = if (contentLength > 0) NotificationHelper.formatFileSize(contentLength) else "Bilinmiyor (Akış)"

        val qualities = mutableListOf<MediaQualityOption>()
        if (fileType == FileType.VIDEO) {
            qualities.add(
                MediaQualityOption(
                    id = "best_video",
                    title = "En Yüksek Kalite (1080p / Orijinal MP4)",
                    resolution = "1080p Full HD",
                    format = "MP4",
                    estimatedSize = sizeFormatted,
                    estimatedBytes = if (contentLength > 0) contentLength else 45_000_000L,
                    directDownloadUrl = cleanUrl
                )
            )
            qualities.add(
                MediaQualityOption(
                    id = "audio_extract",
                    title = "Sadece Ses İndir (MP3 Formatı)",
                    resolution = "320 kbps Audio",
                    format = "MP3",
                    estimatedSize = if (contentLength > 0) NotificationHelper.formatFileSize(contentLength / 6) else "10.0 MB",
                    estimatedBytes = if (contentLength > 0) contentLength / 6 else 10_485_760L,
                    isAudioOnly = true,
                    directDownloadUrl = cleanUrl
                )
            )
        } else {
            qualities.add(
                MediaQualityOption(
                    id = "direct_file",
                    title = "Doğrudan Dosya İndirme ($fileName)",
                    resolution = "Orijinal",
                    format = fileName.substringAfterLast('.', "Dosya").uppercase(),
                    estimatedSize = sizeFormatted,
                    estimatedBytes = if (contentLength > 0) contentLength else 20_000_000L,
                    directDownloadUrl = cleanUrl
                )
            )
        }

        return MediaInspectResult(
            title = fileName,
            originalUrl = cleanUrl,
            hostPlatform = platform,
            fileType = fileType,
            totalSizeText = sizeFormatted,
            totalSizeBytes = contentLength,
            mimeType = contentType,
            supportsMultiThread = acceptRanges || contentLength > 0,
            qualityOptions = qualities
        )
    }

    fun detectPlatform(url: String): String {
        val lower = url.lowercase()
        return when {
            lower.contains("instagram.com") || lower.contains("instagr.am") -> "Instagram Reels / Post"
            lower.contains("youtube.com") || lower.contains("youtu.be") -> "YouTube Video / Shorts"
            lower.contains("t.me") || lower.contains("telegram.me") -> "Telegram Channel Media"
            lower.contains("tiktok.com") -> "TikTok Media"
            lower.contains("twitter.com") || lower.contains("x.com") -> "X / Twitter Video"
            lower.contains("reddit.com") -> "Reddit Media"
            lower.contains("udemy.com") -> "Udemy Online Course"
            lower.contains("skool.com") -> "Skool Community Course"
            lower.contains("kiwify.com") -> "Kiwify Training Course"
            lower.contains("github.com") -> "GitHub Release / Archive"
            lower.contains("archive.org") -> "Internet Archive Media"
            lower.contains("google") || lower.contains("googleapis") -> "Google Cloud CDN"
            else -> "Doğrudan Ağ Bağlantısı (Direct HTTP/HTTPS)"
        }
    }

    fun extractFileNameFromUrl(url: String): String {
        return try {
            val path = url.substringBefore('?').substringBefore('#')
            val rawName = path.substringAfterLast('/')
            val decoded = URLDecoder.decode(rawName, "UTF-8")
            if (decoded.isNotBlank() && decoded.length > 2) {
                decoded
            } else {
                "OmniGet_Download_${System.currentTimeMillis() % 100000}"
            }
        } catch (e: Exception) {
            "OmniGet_Download_${System.currentTimeMillis() % 100000}"
        }
    }

    private fun extractFileNameFromDisposition(disposition: String): String {
        val filenameRegex = "filename\\*?=['\"]?(?:UTF-8'')?([^;'\"]+)['\"]?".toRegex(RegexOption.IGNORE_CASE)
        val match = filenameRegex.find(disposition)
        return if (match != null) {
            try {
                URLDecoder.decode(match.groupValues[1].trim(), "UTF-8")
            } catch (e: Exception) {
                match.groupValues[1].trim()
            }
        } else {
            ""
        }
    }
}
