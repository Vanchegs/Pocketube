package com.pocketube.app

import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class PlaylistsTest {
    @get:Rule val temporary = TemporaryFolder()

    private fun item(id: String, title: String, status: DownloadStatus = DownloadStatus.COMPLETED) =
        DownloadItem(id, "run-$id", "https://youtu.be/dQw4w9WgXcQ", title, "Author", "", 120, 720, status)

    @Test fun namesMembershipAndMultiplePlaylistsSurviveRestart() {
        val directory = temporary.newFolder()
        val store = PlaylistRepository(directory)
        val music = store.create("  Музыка  ")
        val favorites = store.create("Избранное")
        store.setMembership("track", setOf(music, favorites))
        store.rename(music, "На прогулку")
        val restored = PlaylistRepository(directory).playlists.value
        assertEquals(listOf("На прогулку", "Избранное"), restored.map { it.name })
        assertTrue(restored.all { it.itemIds == listOf("track") })
    }

    @Test fun changingMembershipDoesNotDuplicateOrRemoveOtherFiles() {
        val store = PlaylistRepository(temporary.newFolder())
        val a = store.create("A"); val b = store.create("B")
        store.setMembership("one", setOf(a, b))
        store.setMembership("two", setOf(a))
        store.setMembership("one", setOf(b))
        store.setMembership("one", setOf(b))
        assertEquals(listOf("two"), store.playlists.value.first { it.id == a }.itemIds)
        assertEquals(listOf("one"), store.playlists.value.first { it.id == b }.itemIds)
        store.removeItem("one")
        assertTrue(store.playlists.value.first { it.id == b }.itemIds.isEmpty())
        assertEquals(listOf("two"), store.playlists.value.first { it.id == a }.itemIds)
    }

    @Test fun deletingAPlaylistPreservesDownloadedFile() {
        val directory = temporary.newFolder()
        val file = File(directory, "saved.mp3").apply { writeText("media bytes") }
        val store = PlaylistRepository(directory)
        val id = store.create("Подборка")
        store.setMembership("track", setOf(id))
        store.delete(id)
        assertTrue(PlaylistRepository(directory).playlists.value.isEmpty())
        assertEquals("media bytes", file.readText())
    }

    @Test fun invalidNamesDoNotChangeStoredPlaylists() {
        val directory = temporary.newFolder()
        val store = PlaylistRepository(directory)
        val id = store.create("Музыка")
        assertThrows(IllegalArgumentException::class.java) { store.create("  ") }
        assertThrows(IllegalArgumentException::class.java) { store.create(" музыка ") }
        assertThrows(IllegalArgumentException::class.java) { store.rename(id, "x".repeat(61)) }
        assertEquals(listOf("Музыка"), PlaylistRepository(directory).playlists.value.map { it.name })
    }

    @Test fun playlistFilterUsesOnlyCompletedMembersAndIgnoresDeletedFiles() {
        val items = listOf(item("b", "Beta"), item("a", "Alpha"), item("pending", "Pending", DownloadStatus.DOWNLOADING))
        val lists = listOf(Playlist("p", "Playlist", listOf("a", "pending", "deleted")))
        assertEquals(listOf("a"), libraryItems(items, lists, "p", 2, LibrarySort.NEWEST).map { it.id })
        assertEquals(listOf("b"), libraryItems(items, lists, UNASSIGNED_PLAYLIST, 2, LibrarySort.NEWEST).map { it.id })
        assertEquals(listOf("a", "b"), libraryItems(items, lists, null, 2, LibrarySort.TITLE).map { it.id })
        assertEquals(listOf("pending"), libraryItems(items, lists, null, 1, LibrarySort.NEWEST).map { it.id })
    }
}
