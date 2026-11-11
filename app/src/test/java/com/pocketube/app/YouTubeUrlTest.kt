package com.pocketube.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class YouTubeUrlTest {
    private val canonical = "https://www.youtube.com/watch?v=dQw4w9WgXcQ"

    @Test fun acceptsSupportedLinksAndStripsTrackingAndPlaylists() {
        listOf(
            "https://youtu.be/dQw4w9WgXcQ?si=abc",
            "https://www.youtube.com/watch?v=dQw4w9WgXcQ&list=PL123&t=4",
            "https://m.youtube.com/shorts/dQw4w9WgXcQ",
            "youtube.com/live/dQw4w9WgXcQ",
            "https://music.youtube.com/watch?v=dQw4w9WgXcQ"
        ).forEach { assertEquals(canonical, YouTubeUrl.normalize(it)) }
    }

    @Test fun rejectsUntrustedHostsMalformedIdsAndNonVideoLinks() {
        listOf(
            "https://youtube.com.evil.test/watch?v=dQw4w9WgXcQ",
            "https://youtube.com@evil.test/watch?v=dQw4w9WgXcQ",
            "https://www.youtube.com/playlist?list=123",
            "https://youtu.be/short",
            "file:///etc/passwd",
            "https://www.youtube.com:8080/watch?v=dQw4w9WgXcQ",
            "", "not a URL"
        ).forEach { input -> assertThrows(IllegalArgumentException::class.java) { YouTubeUrl.normalize(input) } }
    }
}
