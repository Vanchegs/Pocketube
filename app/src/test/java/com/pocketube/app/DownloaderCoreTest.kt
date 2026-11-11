package com.pocketube.app

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.security.MessageDigest
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class DownloaderCoreTest {
    @get:Rule val temporary = TemporaryFolder()

    private fun archive(version: String): ByteArray = ByteArrayOutputStream().use { bytes ->
        ZipOutputStream(bytes).use { zip ->
            zip.putNextEntry(ZipEntry("yt_dlp/version.py"))
            zip.write("__version__ = '$version'\n".toByteArray())
            zip.closeEntry()
        }
        bytes.toByteArray()
    }

    private fun sha256(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256").digest(bytes)
        .joinToString("") { "%02x".format(it) }

    private fun installed(version: String): File = temporary.newFile("yt-dlp").apply { writeBytes(archive(version)) }

    @Test fun unixExecutablePreambleIsReadWithoutChangingTheChecksummedFile() {
        val target = temporary.newFile("unix-yt-dlp")
        val bytes = "#!/usr/bin/env python3\n".toByteArray() + archive("2026.10.26")
        target.writeBytes(bytes)
        assertFalse(bytes.copyOfRange(0, 4).contentEquals(byteArrayOf(0x50, 0x4b, 3, 4)))
        assertEquals("2026.10.26", DownloaderCore.version(target))
        assertArrayEquals(bytes, target.readBytes())
    }

    @Test fun executableWithPreambleCanBeVerifiedAndInstalled() {
        val target = installed("2025.11.12")
        val executable = "#!/usr/bin/env python3\n".toByteArray() + archive("2026.10.26")
        DownloaderCore.installVerified(target, "2026.10.26", sha256(executable)) { ByteArrayInputStream(executable) }
        assertEquals("2026.10.26", DownloaderCore.version(target))
        assertEquals(sha256(executable), DownloaderCore.sha256(target))
    }

    @Test fun malformedOrUnreasonablyLargePreambleIsRejected() {
        val target = temporary.newFile("not-an-archive")
        target.writeText("not a ZIP file")
        assertNull(DownloaderCore.version(target))
        target.writeBytes(ByteArray(5000) { 65 } + archive("2026.10.26"))
        assertNull(DownloaderCore.version(target))
    }

    @Test fun bundledOfficialArchiveUpgradesExisting2025InstallationAndIsNotInstalledAgain() {
        val target = installed("2025.11.12")
        val asset = listOf(File("src/main/assets/yt-dlp"), File("app/src/main/assets/yt-dlp")).first { it.isFile }
        assertTrue(DownloaderCore.installBundled(target) { asset.inputStream() })
        assertEquals(DownloaderCore.BUNDLED_VERSION, DownloaderCore.version(target))
        assertEquals(DownloaderCore.BUNDLED_SHA256, DownloaderCore.sha256(target))
        assertFalse(DownloaderCore.installBundled(target) { error("An already installed engine must not be overwritten") })
    }

    @Test fun bundledArchiveDoesNotDowngradeANewerDownloadedEngine() {
        val target = installed("2026.11.10")
        assertFalse(DownloaderCore.installBundled(target) { error("Must preserve newer version") })
        assertEquals("2026.11.10", DownloaderCore.version(target))
    }

    @Test fun corruptDownloadLeavesWorkingEngineAndNoTemporaryFileBehind() {
        val target = installed("2026.10.26")
        val original = target.readBytes()
        val replacement = archive("2026.11.01")
        assertThrows(IllegalStateException::class.java) {
            DownloaderCore.installVerified(target, "2026.11.01", sha256(replacement)) {
                ByteArrayInputStream("truncated download".toByteArray())
            }
        }
        assertArrayEquals(original, target.readBytes())
        assertEquals(listOf("yt-dlp"), target.parentFile!!.listFiles()!!.map { it.name })
    }

    @Test fun correctHashWithUnexpectedArchiveVersionAlsoPreservesWorkingEngine() {
        val target = installed("2026.10.26")
        val oldArchive = archive("2025.11.12")
        assertThrows(IllegalStateException::class.java) {
            DownloaderCore.installVerified(target, "2026.11.01", sha256(oldArchive)) { ByteArrayInputStream(oldArchive) }
        }
        assertEquals("2026.10.26", DownloaderCore.version(target))
    }

    @Test fun updaterVerifiesAndInstallsOfficialReleaseAsset() {
        val target = installed("2025.11.12")
        val replacement = archive("2026.10.26")
        val assetUrl = "https://github.com/yt-dlp/yt-dlp/releases/download/2026.10.26/yt-dlp"
        val metadata = JSONObject().put("tag_name", "2026.10.26").put("assets", JSONArray().put(
            JSONObject().put("name", "yt-dlp").put("browser_download_url", assetUrl)
                .put("digest", "sha256:${sha256(replacement)}")
        )).toString().toByteArray()
        val fetched = mutableListOf<String>()
        val updater = DownloaderUpdater { address ->
            fetched += address
            when (address) {
                DownloaderUpdater.RELEASE_URL -> metadata
                assetUrl -> replacement
                else -> error("Unexpected destination")
            }
        }
        assertEquals("2026.10.26", updater.update(target))
        assertEquals("2026.10.26", DownloaderCore.version(target))
        assertEquals(listOf(DownloaderUpdater.RELEASE_URL, assetUrl), fetched)
    }

    @Test fun updaterDoesNotDownloadAgainWhenVersionIsCurrent() {
        val target = installed("2026.10.26")
        val updater = DownloaderUpdater { address ->
            assertEquals(DownloaderUpdater.RELEASE_URL, address)
            """{"tag_name":"2026.10.26"}""".toByteArray()
        }
        assertEquals("2026.10.26", updater.update(target))
    }

    @Test fun updaterRejectsAssetFromAnUnexpectedDestination() {
        val target = installed("2025.11.12")
        val updater = DownloaderUpdater { address ->
            assertEquals(DownloaderUpdater.RELEASE_URL, address)
            """{"tag_name":"2026.10.26","assets":[{"name":"yt-dlp","browser_download_url":"https://example.com/file"}]}""".toByteArray()
        }
        assertThrows(IllegalStateException::class.java) { updater.update(target) }
        assertEquals("2025.11.12", DownloaderCore.version(target))
    }

    @Test fun numericVersionComparisonPreservesNightlyBuildsAndHandlesMonthBoundaries() {
        assertTrue(DownloaderCore.compareVersions("2026.11.01", "2026.10.26") > 0)
        assertTrue(DownloaderCore.compareVersions("2026.10.26.123456", "2026.10.26") > 0)
        assertFalse(DownloaderCore.validVersion("../../yt-dlp"))
    }

    @Test fun forbiddenErrorIsRetryableAndOldVersionWarningsAreNotShownAsTheFailureReason() {
        val raw = "WARNING: Your yt-dlp version (2025.11.12) is older than 90 days!\n" +
            "ERROR: unable to download video data: HTTP Error 403: Forbidden"
        assertTrue(DownloadErrors.shouldRefresh(raw))
        assertTrue(DownloadErrors.message(IllegalStateException(raw)).contains("403"))
        assertFalse(DownloadErrors.message(IllegalStateException(raw)).contains("2025.11.12"))
        assertFalse(DownloadErrors.shouldRefresh("No space left on device"))
    }
}
