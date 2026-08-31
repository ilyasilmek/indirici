package com.example

import com.example.data.model.FileType
import com.example.engine.UrlInspector
import com.example.engine.extractors.InstagramExtractor
import com.example.engine.extractors.TelegramExtractor
import com.example.engine.extractors.TikTokExtractor
import com.example.engine.extractors.TwitterExtractor
import com.example.engine.extractors.YouTubeExtractor
import com.example.service.NotificationHelper
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ExampleUnitTest {
    @Test
    fun testFileSizeFormatting() {
        assertEquals("500 B", NotificationHelper.formatFileSize(500L))
        assertEquals("1.0 KB", NotificationHelper.formatFileSize(1024L))
        assertEquals("1.5 KB", NotificationHelper.formatFileSize(1536L))
        assertEquals("10.00 MB", NotificationHelper.formatFileSize(10 * 1024 * 1024L))
        assertEquals("1.50 GB", NotificationHelper.formatFileSize((1.5 * 1024 * 1024 * 1024).toLong()))
    }

    @Test
    fun testSpeedFormatting() {
        assertEquals("0 KB/s", NotificationHelper.formatSpeed(0L))
        assertEquals("500 KB/s", NotificationHelper.formatSpeed(500 * 1024L))
        assertEquals("4.20 MB/s", NotificationHelper.formatSpeed((4.2 * 1024 * 1024).toLong()))
    }

    @Test
    fun testInstagramReelExtraction() {
        val userSampleUrl = "https://www.instagram.com/reel/Dcq4IEPo3go/?utm_source=ig_web_copy_link&igsi=MzRlODBiNWFlZA=="
        assertTrue(InstagramExtractor.matches(userSampleUrl))
        val shortcode = InstagramExtractor.extractShortcode(userSampleUrl)
        assertEquals("Dcq4IEPo3go", shortcode)

        val postUrl = "https://www.instagram.com/p/C6t3Yy8s9xK/"
        assertTrue(InstagramExtractor.matches(postUrl))
        assertEquals("C6t3Yy8s9xK", InstagramExtractor.extractShortcode(postUrl))
    }

    @Test
    fun testYouTubeIdExtraction() {
        val watchUrl = "https://www.youtube.com/watch?v=dQw4w9WgXcQ"
        assertTrue(YouTubeExtractor.matches(watchUrl))
        assertEquals("dQw4w9WgXcQ", YouTubeExtractor.extractVideoId(watchUrl))

        val shortUrl = "https://youtu.be/dQw4w9WgXcQ"
        assertTrue(YouTubeExtractor.matches(shortUrl))
        assertEquals("dQw4w9WgXcQ", YouTubeExtractor.extractVideoId(shortUrl))

        val shortsUrl = "https://www.youtube.com/shorts/dQw4w9WgXcQ"
        assertTrue(YouTubeExtractor.matches(shortsUrl))
        assertEquals("dQw4w9WgXcQ", YouTubeExtractor.extractVideoId(shortsUrl))
    }

    @Test
    fun testTelegramAndSocialMatching() {
        val tgUrl = "https://t.me/durov/248"
        assertTrue(TelegramExtractor.matches(tgUrl))

        val ttUrl = "https://www.tiktok.com/@creator/video/7123456789012345678"
        assertTrue(TikTokExtractor.matches(ttUrl))

        val xUrl = "https://x.com/Android/status/1785000000000000000"
        assertTrue(TwitterExtractor.matches(xUrl))
        assertEquals("1785000000000000000", TwitterExtractor.extractTweetId(xUrl))
    }

    @Test
    fun testPresetMediaItemsInspection() {
        val presets = UrlInspector.PRESET_MEDIA_ITEMS
        assertTrue(presets.isNotEmpty())

        val videoPreset = presets.find { it.fileType == FileType.VIDEO }
        assertNotNull(videoPreset)
        assertTrue(videoPreset!!.qualityOptions.isNotEmpty())

        val coursePreset = presets.find { it.fileType == FileType.COURSE }
        assertNotNull(coursePreset)
        assertTrue(coursePreset!!.isCourseBundle)
        assertTrue(coursePreset.courseLessons.isNotEmpty())
    }
}
