package com.pocketube.app

import com.yausername.youtubedl_android.YoutubeDLRequest
import java.io.File

object DownloadRequestFactory {
    fun create(item: DownloadItem, directory: File, cache: File): YoutubeDLRequest =
        YoutubeDLRequest(item.url).apply {
            addOption("--no-playlist")
            addOption("--continue")
            addOption("--newline")
            addOption("--socket-timeout", 25)
            addOption("--retries", 3)
            addOption("--fragment-retries", 3)
            addOption("--cache-dir", cache.absolutePath)
            if (item.mediaType == MediaType.AUDIO) {
                addOption("-f", "bestaudio/best")
                addOption("--extract-audio")
                addOption("--audio-format", "mp3")
                addOption("--audio-quality", "0")
                addOption("-o", File(directory, "audio.%(ext)s").absolutePath)
            } else {
                addOption("-f", "bv*[height<=${item.quality}][ext=mp4]+ba[ext=m4a]/b[height<=${item.quality}][ext=mp4]/bv*[height<=${item.quality}]+ba/b[height<=${item.quality}]")
                addOption("--merge-output-format", "mp4/mkv")
                addOption("-o", File(directory, "video.%(ext)s").absolutePath)
            }
        }

    fun completedFile(item: DownloadItem, directory: File): File? = directory.listFiles()?.firstOrNull {
        val names = if (item.mediaType == MediaType.AUDIO) listOf("audio.mp3")
            else listOf("video.mp4", "video.mkv", "video.webm", "video.mov")
        it.isFile && it.name in names && it.length() > 0
    }
}
