package com.pocketube.app

import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStream
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.security.MessageDigest
import java.util.zip.ZipFile

object DownloaderCore {
    const val BUNDLED_VERSION = "2026.10.26"
    const val BUNDLED_SHA256 = "e51e69e89489f199530f13d32a4342da3ddb2cf34842f364603a066db05afbf3"
    private val versionPattern = Regex("\\d{4}\\.\\d{2}\\.\\d{2}(?:\\.\\d+)?")

    fun validVersion(value: String): Boolean = versionPattern.matches(value)

    fun compareVersions(left: String, right: String): Int {
        require(validVersion(left) && validVersion(right)) { "Некорректная версия загрузчика" }
        val a = left.split('.').map(String::toLong)
        val b = right.split('.').map(String::toLong)
        for (index in 0 until maxOf(a.size, b.size)) {
            val result = (a.getOrElse(index) { 0L }).compareTo(b.getOrElse(index) { 0L })
            if (result != 0) return result
        }
        return 0
    }

    fun version(file: File): String? = runCatching {
        ZipFile(file).use { zip ->
            val entry = zip.getEntry("yt_dlp/version.py") ?: return@use null
            val text = zip.getInputStream(entry).bufferedReader().use { it.readText() }
            Regex("(?m)^__version__\\s*=\\s*['\"]([^'\"]+)['\"]").find(text)
                ?.groupValues?.get(1)?.takeIf(::validVersion)
        }
    }.getOrNull()

    fun sha256(file: File): String = FileInputStream(file).use { input ->
        val digest = MessageDigest.getInstance("SHA-256")
        val buffer = ByteArray(8192)
        while (true) {
            val count = input.read(buffer)
            if (count < 0) break
            digest.update(buffer, 0, count)
        }
        digest.digest().joinToString("") { "%02x".format(it) }
    }

    fun installBundled(destination: File, openAsset: () -> InputStream): Boolean {
        val installed = version(destination)
        if (installed != null && compareVersions(installed, BUNDLED_VERSION) > 0) return false
        if (installed == BUNDLED_VERSION && sha256(destination) == BUNDLED_SHA256) return false
        installVerified(destination, BUNDLED_VERSION, BUNDLED_SHA256, openAsset)
        return true
    }

    fun installVerified(destination: File, expectedVersion: String, expectedSha256: String, open: () -> InputStream) {
        require(validVersion(expectedVersion) && expectedSha256.matches(Regex("[a-f0-9]{64}")))
        val parent = destination.parentFile ?: error("Нет папки загрузчика")
        check(parent.isDirectory || parent.mkdirs()) { "Не удалось создать папку загрузчика" }
        val temporary = File.createTempFile("yt-dlp-", ".tmp", parent)
        try {
            open().use { input ->
                FileOutputStream(temporary).use { output ->
                    input.copyTo(output)
                    output.fd.sync()
                }
            }
            check(sha256(temporary) == expectedSha256) { "Контрольная сумма загрузчика не совпала" }
            check(version(temporary) == expectedVersion) { "Версия загрузчика не совпала" }
            // Readers hold the engine's read lock; installation holds its write lock.
            // A failed download or validation leaves the working file intact.
            Files.move(temporary.toPath(), destination.toPath(),
                StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
        } finally {
            temporary.delete()
        }
    }
}
