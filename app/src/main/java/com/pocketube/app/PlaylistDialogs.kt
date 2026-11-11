package com.pocketube.app

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun PlaylistNameDialog(playlist: Playlist?, onDismiss: () -> Unit, onSave: (String) -> Unit) {
    var name by remember(playlist?.id) { mutableStateOf(playlist?.name.orEmpty()) }
    AlertDialog(onDismissRequest = onDismiss,
        title = { Text(if (playlist == null) "Новый плейлист" else "Переименовать плейлист") },
        text = { OutlinedTextField(name, { if (it.length <= 60) name = it }, singleLine = true, label = { Text("Название") }) },
        confirmButton = { TextButton(onClick = { onSave(name); onDismiss() }, enabled = name.isNotBlank()) { Text("Сохранить") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Отмена") } })
}

@Composable
fun PlaylistMembershipDialog(item: DownloadItem, playlists: List<Playlist>, onDismiss: () -> Unit,
    onCreate: (String, (String) -> Unit) -> Unit, onSave: (Set<String>) -> Unit) {
    var selected by remember(item.id) { mutableStateOf(playlists.filter { item.id in it.itemIds }.map { it.id }.toSet()) }
    var newName by remember { mutableStateOf("") }
    AlertDialog(onDismissRequest = onDismiss, title = { Text("Добавить в плейлисты") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(item.title, fontSize = 13.sp, color = Muted, maxLines = 2)
                Column(Modifier.fillMaxWidth().heightIn(max = 240.dp).verticalScroll(rememberScrollState())) {
                    if (playlists.isEmpty()) Text("Создайте первый плейлист ниже", fontSize = 13.sp)
                    playlists.forEach { playlist ->
                        Row(Modifier.fillMaxWidth().clickable {
                            selected = if (playlist.id in selected) selected - playlist.id else selected + playlist.id
                        }, verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(checked = playlist.id in selected, onCheckedChange = {
                                selected = if (it) selected + playlist.id else selected - playlist.id
                            })
                            Text(playlist.name, Modifier.weight(1f), fontSize = 14.sp)
                        }
                    }
                }
                OutlinedTextField(newName, { if (it.length <= 60) newName = it }, singleLine = true,
                    modifier = Modifier.fillMaxWidth(), label = { Text("Новый плейлист") })
                TextButton(enabled = newName.isNotBlank(), onClick = {
                    onCreate(newName) { id -> selected = selected + id; newName = "" }
                }) { Text("Создать и выбрать") }
            }
        },
        confirmButton = { TextButton(onClick = { onSave(selected.intersect(playlists.map { it.id }.toSet())); onDismiss() }) { Text("Готово") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Отмена") } })
}
