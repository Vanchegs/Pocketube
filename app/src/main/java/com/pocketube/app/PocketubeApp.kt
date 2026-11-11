package com.pocketube.app

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import com.yausername.youtubedl_android.YoutubeDL
import com.yausername.ffmpeg.FFmpeg
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

class PocketubeApp : Application() {
    val downloads by lazy { DownloadRepository(this) }
    val engine = VideoEngine()

    override fun onCreate() {
        super.onCreate()
        getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel("downloads", "Загрузка видео", NotificationManager.IMPORTANCE_LOW)
        )
    }
}

class VideoEngine {
    private val mutex = Mutex()
    private var ready = false

    suspend fun initialize(app: Application) = withContext(Dispatchers.IO) {
        mutex.withLock {
            if (!ready) {
                YoutubeDL.getInstance().init(app)
                FFmpeg.getInstance().init(app)
                ready = true
            }
        }
    }

    suspend fun update(app: Application) = withContext(Dispatchers.IO) {
        initialize(app)
        mutex.withLock {
            YoutubeDL.getInstance().updateYoutubeDL(app, YoutubeDL.UpdateChannel.STABLE)
        }
    }
}
