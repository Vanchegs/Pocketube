package com.pocketube.app

import java.net.URI
import java.net.URLDecoder

object YouTubeUrl {
    fun normalize(input: String): String {
        val raw = input.trim().let { if (it.startsWith("http")) it else "https://$it" }
        val uri = runCatching { URI(raw) }.getOrNull()
            ?: throw IllegalArgumentException("Вставьте корректную ссылку YouTube")
        require(uri.scheme in listOf("https", "http") && uri.userInfo == null && uri.port == -1) {
            "Вставьте ссылку YouTube или youtu.be"
        }
        val host = uri.host?.lowercase()
        val segments = uri.path.orEmpty().split('/').filter { it.isNotEmpty() }
        val id = when (host) {
            "youtu.be", "www.youtu.be" -> segments.firstOrNull()
            "youtube.com", "www.youtube.com", "m.youtube.com", "music.youtube.com" -> {
                if (segments.firstOrNull() in listOf("shorts", "live", "embed")) segments.getOrNull(1)
                else if (uri.path == "/watch") uri.rawQuery.orEmpty().split('&')
                    .firstOrNull { it.startsWith("v=") }?.substringAfter('=')?.let { URLDecoder.decode(it, "UTF-8") }
                else null
            }
            else -> null
        }
        require(id != null && id.matches(Regex("[A-Za-z0-9_-]{11}"))) {
            "Нужна ссылка на одно видео YouTube, Shorts или трансляцию"
        }
        return "https://www.youtube.com/watch?v=$id"
    }
}
