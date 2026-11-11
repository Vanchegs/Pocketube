package com.pocketube.app

import org.json.JSONObject
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

class DownloaderUpdater(private val fetch: (String) -> ByteArray = ::download) {
    fun update(destination: File): String {
        val release = JSONObject(fetch(RELEASE_URL).toString(Charsets.UTF_8))
        val version = release.getString("tag_name")
        check(DownloaderCore.validVersion(version)) { "GitHub вернул неизвестную версию загрузчика" }
        val installed = DownloaderCore.version(destination)
        if (installed != null && DownloaderCore.compareVersions(installed, version) >= 0) return installed
        val url = "https://github.com/yt-dlp/yt-dlp/releases/download/$version/yt-dlp"
        val assets = release.getJSONArray("assets")
        val asset = (0 until assets.length()).map { assets.getJSONObject(it) }
            .firstOrNull { it.optString("name") == "yt-dlp" }
            ?: error("В официальном релизе нет загрузчика")
        check(asset.getString("browser_download_url") == url) { "Неизвестный источник загрузчика" }
        val digest = asset.optString("digest").removePrefix("sha256:")
        val checksum = if (digest.matches(Regex("[a-f0-9]{64}"))) digest else {
            val sums = fetch("https://github.com/yt-dlp/yt-dlp/releases/download/$version/SHA2-256SUMS")
                .toString(Charsets.UTF_8)
            Regex("(?m)^([a-f0-9]{64})\\s+\\*?yt-dlp$").find(sums)?.groupValues?.get(1)
                ?: error("Не найдена контрольная сумма загрузчика")
        }
        DownloaderCore.installVerified(destination, version, checksum) { ByteArrayInputStream(fetch(url)) }
        return version
    }

    companion object {
        const val RELEASE_URL = "https://api.github.com/repos/yt-dlp/yt-dlp/releases/latest"
        private const val MAX_BYTES = 16 * 1024 * 1024

        private fun download(address: String): ByteArray {
            val url = URL(address)
            require(url.protocol == "https")
            val connection = url.openConnection() as HttpURLConnection
            connection.connectTimeout = 8_000
            connection.readTimeout = 15_000
            connection.setRequestProperty("User-Agent", "Pocketube-Android")
            try {
                check(connection.responseCode == HttpURLConnection.HTTP_OK) {
                    "Не удалось обновить загрузчик (HTTP ${connection.responseCode})"
                }
                check(connection.url.protocol == "https")
                check(connection.contentLengthLong <= MAX_BYTES) { "Файл загрузчика слишком велик" }
                return connection.inputStream.use { input ->
                    val output = ByteArrayOutputStream()
                    val buffer = ByteArray(8192)
                    while (true) {
                        if (Thread.currentThread().isInterrupted) throw InterruptedException()
                        val count = input.read(buffer)
                        if (count < 0) break
                        check(output.size() + count <= MAX_BYTES) { "Файл загрузчика слишком велик" }
                        output.write(buffer, 0, count)
                    }
                    output.toByteArray()
                }
            } finally {
                connection.disconnect()
            }
        }
    }
}
