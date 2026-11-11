package com.pocketube.app

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.util.Log
import com.yausername.youtubedl_android.YoutubeDL
import com.yausername.ffmpeg.FFmpeg
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runInterruptible
import java.io.File
import java.util.concurrent.locks.ReentrantReadWriteLock

class PocketubeApp : Application() {
    val downloads by lazy { DownloadRepository(this) }
    val playlists by lazy { PlaylistRepository(filesDir) }
    val engine = VideoEngine()

    override fun onCreate() {
        super.onCreate()
        getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel("downloads", "Загрузки видео и аудио", NotificationManager.IMPORTANCE_LOW)
        )
    }
}

class VideoEngine {
    private val lock = ReentrantReadWriteLock(true)
    @Volatile private var ready = false
    @Volatile private var currentVersion = DownloaderCore.BUNDLED_VERSION

    private fun binary(app: Application) = File(app.noBackupFilesDir, "youtubedl-android/yt-dlp/yt-dlp")
    private fun clearCache(app: Application) { File(app.cacheDir, "yt-dlp").deleteRecursively() }

    suspend fun initialize(app: Application) {
        if (ready) return
        runInterruptible(Dispatchers.IO) {
            val write = lock.writeLock()
            write.lockInterruptibly()
            try {
                if (!ready) {
                    if (DownloaderCore.installBundled(binary(app)) { app.assets.open("yt-dlp") }) clearCache(app)
                    currentVersion = DownloaderCore.version(binary(app)) ?: error("Не удалось проверить версию загрузчика")
                    YoutubeDL.getInstance().init(app)
                    FFmpeg.getInstance().init(app)
                    ready = true
                }
            } finally {
                write.unlock()
            }
        }
    }

    suspend fun update(app: Application, force: Boolean = true): String {
        initialize(app)
        return runInterruptible(Dispatchers.IO) {
            val preferences = app.getSharedPreferences("downloader_updates", Application.MODE_PRIVATE)
            fun checkedRecently(): Boolean {
                val checked = preferences.getLong("last_attempt", 0L)
                val now = System.currentTimeMillis()
                return now >= checked && now - checked < 24 * 60 * 60 * 1000L
            }
            // Fast path does not wait for another download to release its read lock.
            if (!force && checkedRecently()) return@runInterruptible currentVersion
            val write = lock.writeLock()
            write.lockInterruptibly()
            try {
                if (!force && checkedRecently()) return@runInterruptible currentVersion
                preferences.edit().putLong("last_attempt", System.currentTimeMillis()).apply()
                val version = DownloaderUpdater().update(binary(app))
                currentVersion = version
                clearCache(app)
                version
            } finally {
                write.unlock()
            }
        }
    }

    private suspend fun <T> read(app: Application, operation: () -> T): T {
        initialize(app)
        return runInterruptible(Dispatchers.IO) {
            val read = lock.readLock()
            read.lockInterruptibly()
            try { operation() } finally { read.unlock() }
        }
    }

    suspend fun <T> execute(app: Application, operation: () -> T): T {
        try {
            update(app, force = false)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // Offline or rate-limited updates keep the verified bundled engine usable.
            Log.w("Pocketube", "Could not check downloader update", e)
        }
        return try {
            read(app, operation)
        } catch (e: CancellationException) {
            throw e
        } catch (first: Exception) {
            if (!DownloadErrors.shouldRefresh(first.message.orEmpty())) throw first
            try {
                update(app)
            } catch (e: CancellationException) {
                throw e
            } catch (updateError: Exception) {
                first.addSuppressed(updateError)
                throw first
            }
            // Rebuild the request and obtain fresh playback URLs; retry at most once.
            read(app, operation)
        }
    }
}
