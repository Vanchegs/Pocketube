package com.pocketube.app

data class VideoPreview(
    val url: String,
    val title: String,
    val author: String,
    val thumbnail: String,
    val duration: Int,
    val qualities: List<Int>
)

enum class DownloadStatus { QUEUED, DOWNLOADING, PAUSED, COMPLETED, ERROR }

data class DownloadItem(
    val id: String,
    val runId: String,
    val url: String,
    val title: String,
    val author: String,
    val thumbnail: String,
    val duration: Int,
    val quality: Int,
    val status: DownloadStatus = DownloadStatus.QUEUED,
    val progress: Float = 0f,
    val eta: Long = 0,
    val filePath: String = "",
    val size: Long = 0,
    val error: String = ""
)

fun formatDuration(seconds: Int): String = if (seconds >= 3600)
    "%d:%02d:%02d".format(seconds / 3600, seconds / 60 % 60, seconds % 60)
else "%d:%02d".format(seconds / 60, seconds % 60)

fun formatSize(bytes: Long): String = when {
    bytes >= 1024L * 1024 * 1024 -> "%.1f ГБ".format(bytes / (1024.0 * 1024 * 1024))
    else -> "%.1f МБ".format(bytes / (1024.0 * 1024))
}
