package com.pocketube.app

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun MediaLibraryScreen(downloads: List<DownloadItem>, playlists: List<Playlist>, busy: Set<String>, onAdd: () -> Unit,
    onControl: (DownloadItem) -> Unit, onDelete: (DownloadItem) -> Unit,
    onPlay: (DownloadItem, List<DownloadItem>) -> Unit, onExport: (DownloadItem) -> Unit,
    onAssign: (DownloadItem) -> Unit, onCreatePlaylist: () -> Unit,
    onRenamePlaylist: (Playlist) -> Unit, onDeletePlaylist: (Playlist) -> Unit) {
    var filter by rememberSaveable { mutableIntStateOf(0) }
    var selectedId by rememberSaveable { mutableStateOf<String?>(null) }
    var sortName by rememberSaveable { mutableStateOf(LibrarySort.NEWEST.name) }
    var menu by remember { mutableStateOf(false) }
    val completed = downloads.filter { it.status == DownloadStatus.COMPLETED }
    val selectedPlaylist = playlists.find { it.id == selectedId }
    val visible = libraryItems(downloads, playlists, selectedId, filter, LibrarySort.valueOf(sortName))
    LaunchedEffect(playlists) {
        if (selectedId != null && selectedId != UNASSIGNED_PLAYLIST && playlists.none { it.id == selectedId }) selectedId = null
    }
    Column(Modifier.fillMaxSize()) {
        Column(Modifier.padding(start = 22.dp, end = 22.dp, top = 12.dp, bottom = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Ваша медиатека", fontSize = 29.sp, fontWeight = FontWeight.SemiBold, letterSpacing = (-.8).sp)
            Text("${completed.size} сохранено · ${formatSize(completed.sumOf { it.size })} на устройстве", color = Muted, fontSize = 12.sp)
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("Все", "Загружаются", "Скачанные").forEachIndexed { index, title ->
                    FilterChip(selected = filter == index, onClick = { filter = index; if (index != 2) selectedId = null }, label = { Text(title, fontSize = 12.sp) })
                }
            }
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("ПЛЕЙЛИСТЫ", Modifier.weight(1f), fontSize = 10.sp, color = Muted, letterSpacing = 1.6.sp)
                IconButton(onClick = onCreatePlaylist, modifier = Modifier.size(34.dp)) { Icon(Icons.Outlined.Add, "Создать плейлист", Modifier.size(21.dp), tint = Lime) }
                Box {
                    IconButton(onClick = { menu = true }, modifier = Modifier.size(34.dp)) { Icon(Icons.Outlined.Sort, "Сортировка и плейлист", Modifier.size(21.dp)) }
                    DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                        DropdownMenuItem(text = { Text("Сначала новые") }, onClick = { sortName = LibrarySort.NEWEST.name; menu = false })
                        DropdownMenuItem(text = { Text("По названию") }, onClick = { sortName = LibrarySort.TITLE.name; menu = false })
                        selectedPlaylist?.let { playlist ->
                            HorizontalDivider()
                            DropdownMenuItem(text = { Text("Переименовать плейлист") }, onClick = { menu = false; onRenamePlaylist(playlist) })
                            DropdownMenuItem(text = { Text("Удалить плейлист") }, onClick = { menu = false; onDeletePlaylist(playlist) })
                        }
                    }
                }
            }
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(selected = selectedId == null, onClick = { selectedId = null }, label = { Text("Вся медиатека", fontSize = 12.sp) })
                FilterChip(selected = selectedId == UNASSIGNED_PLAYLIST, onClick = { selectedId = UNASSIGNED_PLAYLIST; filter = 2 }, label = { Text("Без плейлиста", fontSize = 12.sp) })
                playlists.forEach { playlist ->
                    val count = completed.count { it.id in playlist.itemIds }
                    FilterChip(selected = selectedId == playlist.id, onClick = { selectedId = playlist.id; filter = 2 },
                        label = { Text("${playlist.name} · $count", Modifier.widthIn(max = 180.dp), fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis) })
                }
            }
        }
        if (visible.isEmpty()) {
            Column(Modifier.fillMaxSize().padding(horizontal = 28.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
                Box(Modifier.size(72.dp).clip(RoundedCornerShape(24.dp)).background(Surface), contentAlignment = Alignment.Center) {
                    Icon(Icons.Outlined.PlaylistPlay, null, Modifier.size(36.dp), tint = Lime)
                }
                Spacer(Modifier.height(18.dp))
                Text(if (selectedId != null) "В плейлисте пока пусто" else "Здесь будут ваши загрузки", fontSize = 20.sp, fontWeight = FontWeight.Medium)
                Spacer(Modifier.height(8.dp))
                Text(if (selectedId != null) "Добавляйте скачанные файлы кнопкой\nплейлиста в карточке видео или аудио."
                    else "Сохраните видео или звук, чтобы смотреть\nи слушать прямо в приложении.", color = Muted, fontSize = 12.sp, lineHeight = 20.sp)
                Spacer(Modifier.height(20.dp))
                TextButton(onClick = if (selectedId != null) ({ selectedId = null; filter = 2 }) else onAdd) {
                    Text(if (selectedId != null) "Открыть скачанные" else "Добавить ссылку")
                }
            }
        } else LazyColumn(contentPadding = PaddingValues(start = 18.dp, end = 18.dp, bottom = 20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            items(visible, key = { it.id }) { item ->
                DownloadCard(item, item.id in busy, onControl, onDelete,
                    onPlay = { onPlay(it, visible.filter { entry -> entry.status == DownloadStatus.COMPLETED }) }, onExport = onExport,
                    onAssign = onAssign, playlistNames = playlists.filter { item.id in it.itemIds }.map { it.name })
            }
        }
    }
}
