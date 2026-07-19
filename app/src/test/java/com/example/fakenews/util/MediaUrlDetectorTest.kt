package com.example.fakenews.util

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MediaUrlDetectorTest {
    @Test
    fun mp4UrlIsPlayableVideoUrl() {
        assertTrue(MediaUrlDetector.isPlayableVideoUrl("https://example.com/video.mp4"))
    }

    @Test
    fun m3u8UrlIsPlayableVideoUrl() {
        assertTrue(MediaUrlDetector.isPlayableVideoUrl("https://example.com/live/playlist.m3u8?token=abc"))
    }

    @Test
    fun webmUrlIsPlayableVideoUrl() {
        assertTrue(MediaUrlDetector.isPlayableVideoUrl("https://example.com/video.webm"))
    }

    @Test
    fun youtubeWatchUrlIsNotPlayableVideoUrl() {
        assertFalse(MediaUrlDetector.isPlayableVideoUrl("https://www.youtube.com/watch?v=abc"))
    }

    @Test
    fun htmlArticleUrlIsNotPlayableVideoUrl() {
        assertFalse(MediaUrlDetector.isPlayableVideoUrl("https://example.com/news/article.html"))
    }

    @Test
    fun blankAndNullAreSafe() {
        assertFalse(MediaUrlDetector.isPlayableVideoUrl(""))
        assertFalse(MediaUrlDetector.isPlayableVideoUrl(null))
    }
}
