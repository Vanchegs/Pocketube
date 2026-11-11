package com.pocketube.app

import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class AudioDownloadsTest {
    @get:Rule val temporary = TemporaryFolder()

    private fun item(type: MediaType = MediaType.AUDIO) = DownloadItem(
        id = "download", runId = "run", url = "https://www.youtube.com/watch?v=dQw4w9WgXcQ",
        title = "Sample", author = "Author", thumbnail = "", duration = 10, quality = 720,
        mediaType = type
    )

    @Test fun audioSelectsAudioStreamAndProducesMp3InsteadOfVideo() {
        val directory = temporary.newFolder()
        val request = DownloadRequestFactory.create(item(), directory, temporary.newFolder())
        assertEquals("bestaudio/best", request.getOption("-f"))
        assertTrue(request.hasOption("--extract-audio"))
        assertEquals("mp3", request.getOption("--audio-format"))
        assertEquals(File(directory, "audio.%(ext)s").absolutePath, request.getOption("-o"))
        assertFalse(request.hasOption("--merge-output-format"))
        assertEquals("audio/mpeg", MediaType.AUDIO.mimeType)
    }

    @Test fun videoStillUsesSelectedResolutionAndVideoOutput() {
        val directory = temporary.newFolder()
        val request = DownloadRequestFactory.create(item(MediaType.VIDEO), directory, temporary.newFolder())
        assertTrue(request.getOption("-f").orEmpty().contains("height<=720"))
        assertFalse(request.hasOption("--extract-audio"))
        assertEquals("mp4/mkv", request.getOption("--merge-output-format"))
        assertEquals(File(directory, "video.%(ext)s").absolutePath, request.getOption("-o"))
    }

    @Test fun partialAudioOrIntermediateContainerIsNotReportedAsCompletedMp3() {
        val directory = temporary.newFolder()
        File(directory, "audio.webm").writeText("intermediate")
        File(directory, "audio.mp3.part").writeText("partial")
        File(directory, "audio.mp3").writeBytes(byteArrayOf())
        assertNull(DownloadRequestFactory.completedFile(item(), directory))
        val completed = File(directory, "audio.mp3").apply { writeText("completed") }
        assertEquals(completed, DownloadRequestFactory.completedFile(item(), directory))
    }

    @Test fun audioModeAndPausedStateSurvivePersistenceWithExistingVideoDownloads() {
        val audio = item().copy(status = DownloadStatus.PAUSED, progress = 43f, quality = 0)
        val video = item(MediaType.VIDEO).copy(id = "other", status = DownloadStatus.COMPLETED,
            filePath = "/storage/video.mp4", size = 1000)
        assertEquals(listOf(audio, video), DownloadJson.decode(DownloadJson.encode(listOf(audio, video))))
    }

    @Test fun historyFromVersionOneRemainsVideoWithoutLosingFileOrStatus() {
        val legacy = """[{"id":"old","runId":"old-run","url":"https://youtu.be/dQw4w9WgXcQ",
            "title":"Saved video","author":"Author","thumbnail":"","duration":10,"quality":720,
            "status":"COMPLETED","filePath":"/storage/video.mp4","size":1234,"progress":100}]"""
        val restored = DownloadJson.decode(legacy).single()
        assertEquals(MediaType.VIDEO, restored.mediaType)
        assertEquals(DownloadStatus.COMPLETED, restored.status)
        assertEquals("/storage/video.mp4", restored.filePath)
        assertEquals(1234L, restored.size)
    }

    @Test fun sameSourceMayHaveVideoAndAudioButAudioIsNotDuplicatedForVideoResolutions() {
        val audio = item().copy(quality = 0)
        assertTrue(audio.matchesSelection(audio.url, 1080, MediaType.AUDIO))
        assertFalse(audio.matchesSelection(audio.url, 720, MediaType.VIDEO))
        val video = item(MediaType.VIDEO)
        assertFalse(video.matchesSelection(video.url, 720, MediaType.AUDIO))
        assertTrue(video.matchesSelection(video.url, 720, MediaType.VIDEO))
        assertFalse(video.matchesSelection(video.url, 1080, MediaType.VIDEO))
    }
}
