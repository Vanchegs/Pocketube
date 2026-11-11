package com.pocketube.app

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.Locale
import java.util.UUID

data class Playlist(val id: String, val name: String, val itemIds: List<String> = emptyList())
enum class LibrarySort { NEWEST, TITLE }
const val UNASSIGNED_PLAYLIST = "unassigned"

fun libraryItems(downloads: List<DownloadItem>, playlists: List<Playlist>, playlistId: String?,
    statusFilter: Int, sort: LibrarySort): List<DownloadItem> {
    val assigned = playlists.flatMap { it.itemIds }.toSet()
    val selected = playlists.find { it.id == playlistId }?.itemIds.orEmpty().toSet()
    val result = downloads.filter { item ->
        val matchesPlaylist = when (playlistId) {
            null -> true
            UNASSIGNED_PLAYLIST -> item.status == DownloadStatus.COMPLETED && item.id !in assigned
            else -> item.status == DownloadStatus.COMPLETED && item.id in selected
        }
        matchesPlaylist && (statusFilter == 0 || if (statusFilter == 2)
            item.status == DownloadStatus.COMPLETED else item.status != DownloadStatus.COMPLETED)
    }
    return if (sort == LibrarySort.TITLE) result.sortedWith(compareBy({ it.title.lowercase(Locale.ROOT) }, { it.id })) else result
}

class PlaylistRepository(directory: File) {
    private val file = File(directory, "playlists.json")
    private val mutablePlaylists = MutableStateFlow(read())
    val playlists = mutablePlaylists.asStateFlow()

    private fun read(): List<Playlist> {
        if (!file.exists()) return emptyList()
        val json = JSONArray(file.readText())
        return (0 until json.length()).map { index ->
            val o = json.getJSONObject(index)
            val ids = o.optJSONArray("itemIds") ?: JSONArray()
            Playlist(o.getString("id"), o.getString("name"),
                (0 until ids.length()).map { ids.getString(it) }.distinct())
        }
    }

    private fun save(values: List<Playlist>) {
        val json = JSONArray()
        values.forEach { playlist -> json.put(JSONObject().put("id", playlist.id).put("name", playlist.name)
            .put("itemIds", JSONArray(playlist.itemIds))) }
        file.parentFile!!.mkdirs()
        val temporary = File.createTempFile("playlists-", ".tmp", file.parentFile)
        try {
            FileOutputStream(temporary).use { output ->
                output.write(json.toString().toByteArray(Charsets.UTF_8))
                output.fd.sync()
            }
            Files.move(temporary.toPath(), file.toPath(), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
            mutablePlaylists.value = values
        } finally { temporary.delete() }
    }

    private fun validatedName(value: String, excludingId: String? = null): String {
        val name = value.trim()
        require(name.isNotEmpty()) { "Введите название плейлиста" }
        require(name.length <= 60) { "Название должно быть не длиннее 60 символов" }
        require(mutablePlaylists.value.none { it.id != excludingId && it.name.equals(name, ignoreCase = true) }) {
            "Плейлист с таким названием уже есть"
        }
        return name
    }

    @Synchronized fun create(name: String): String {
        val playlist = Playlist(UUID.randomUUID().toString(), validatedName(name))
        save(mutablePlaylists.value + playlist)
        return playlist.id
    }

    @Synchronized fun rename(id: String, name: String) {
        require(mutablePlaylists.value.any { it.id == id }) { "Плейлист не найден" }
        val title = validatedName(name, id)
        save(mutablePlaylists.value.map { if (it.id == id) it.copy(name = title) else it })
    }

    @Synchronized fun delete(id: String) { save(mutablePlaylists.value.filterNot { it.id == id }) }

    @Synchronized fun setMembership(itemId: String, selectedIds: Set<String>) {
        require(mutablePlaylists.value.map { it.id }.containsAll(selectedIds)) { "Один из плейлистов уже удалён" }
        save(mutablePlaylists.value.map { playlist ->
            playlist.copy(itemIds = if (playlist.id in selectedIds) (playlist.itemIds + itemId).distinct()
                else playlist.itemIds.filterNot { it == itemId })
        })
    }

    @Synchronized fun removeItem(itemId: String) {
        save(mutablePlaylists.value.map { it.copy(itemIds = it.itemIds.filterNot { id -> id == itemId }) })
    }
}
