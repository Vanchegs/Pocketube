package com.pocketube.app

object DownloadErrors {
    fun shouldRefresh(message: String): Boolean =
        message.contains("HTTP Error 403", ignoreCase = true) ||
        message.contains("HTTP Error 410", ignoreCase = true) ||
        message.contains("nsig extraction failed", ignoreCase = true) ||
        message.contains("Signature extraction failed", ignoreCase = true)

    fun message(error: Exception): String {
        val raw = error.message.orEmpty()
        if (raw.contains("HTTP Error 403", ignoreCase = true)) {
            return "YouTube отклонил загрузку (HTTP 403). Проверьте, открывается ли это видео в YouTube через ту же сеть, и повторите попытку. Если ошибка остаётся, нажмите обновление загрузчика вверху."
        }
        val detail = raw.lineSequence().map { it.trim() }
            .lastOrNull { it.startsWith("ERROR:") }?.removePrefix("ERROR:")?.trim()
            ?: raw.lineSequence().lastOrNull { it.isNotBlank() }.orEmpty()
        return detail.take(450).ifBlank { "Не удалось скачать файл. Проверьте интернет и повторите попытку." }
    }
}
