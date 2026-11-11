package com.pocketube.app

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import java.io.File

internal val Lime = Color(0xFFC5F277)
internal val Background = Color(0xFF101112)
internal val Surface = Color(0xFF1C1E20)
internal val Muted = Color(0xFF9DA29B)

class MainActivity : ComponentActivity() {
    private val model by viewModels<MainViewModel>()
    private val playback by viewModels<PlaybackViewModel>()
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT)
        )
        if (savedInstanceState == null && intent.action == Intent.ACTION_SEND) {
            intent.getStringExtra(Intent.EXTRA_TEXT)?.let { shared ->
                model.link(Regex("https?://[^\\s]+").find(shared)?.value ?: shared)
            }
        }
        setContent { PocketubeTheme { PocketubeScreen(model, playback) } }
    }
}

@Composable
private fun PocketubeTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = darkColorScheme(primary = Lime, onPrimary = Background,
        background = Background, surface = Surface, onSurface = Color(0xFFF4F5F0),
        surfaceVariant = Color(0xFF272A2B), onSurfaceVariant = Muted,
        secondary = Lime, secondaryContainer = Color(0xFF344127), onSecondaryContainer = Lime,
        primaryContainer = Color(0xFF344127), onPrimaryContainer = Lime,
        outline = Color(0xFF383C37)), content = content)
}

@Composable
private fun PocketubeScreen(model: MainViewModel, playback: PlaybackViewModel) {
    var tab by rememberSaveable { mutableIntStateOf(0) }
    val state by model.state.collectAsStateWithLifecycle()
    val downloads by model.downloads.collectAsStateWithLifecycle()
    val busy by model.busy.collectAsStateWithLifecycle()
    val playlists by model.playlists.collectAsStateWithLifecycle()
    val playing by playback.state.collectAsStateWithLifecycle()
    var expanded by rememberSaveable { mutableStateOf(false) }
    var membershipItem by remember { mutableStateOf<DownloadItem?>(null) }
    var showPlaylistName by remember { mutableStateOf(false) }
    var editingPlaylist by remember { mutableStateOf<Playlist?>(null) }
    var deletingPlaylist by remember { mutableStateOf<Playlist?>(null) }
    val snack = remember { SnackbarHostState() }
    val notifications = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    var exportItem by remember { mutableStateOf<DownloadItem?>(null) }
    var deleteItem by remember { mutableStateOf<DownloadItem?>(null) }
    val finishExport: (Uri?) -> Unit = { uri ->
        val item = exportItem
        if (uri != null && item != null) model.export(item, uri)
        exportItem = null
    }
    val exportVideo = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("video/*"), finishExport)
    val exportAudio = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("audio/mpeg"), finishExport)
    LaunchedEffect(model) { model.messages.collect { snack.showSnackbar(it) } }
    LaunchedEffect(playback) { playback.messages.collect { snack.showSnackbar(it) } }
    LaunchedEffect(playing.currentId) { if (playing.currentId == null) expanded = false }

    Scaffold(containerColor = Background, snackbarHost = { SnackbarHost(snack) },
        bottomBar = {
            Column {
            if (playing.currentId != null) MiniPlayer(playing, playback, expanded) { expanded = true }
            NavigationBar(containerColor = Background, tonalElevation = 0.dp) {
                NavigationBarItem(selected = tab == 0, onClick = { tab = 0 },
                    icon = { Icon(Icons.Outlined.AddLink, null) }, label = { Text("Добавить") },
                    colors = NavigationBarItemDefaults.colors(indicatorColor = Lime.copy(alpha = .14f), selectedIconColor = Lime, selectedTextColor = Lime))
                NavigationBarItem(selected = tab == 1, onClick = { tab = 1 },
                    icon = { BadgedBox(badge = {
                        val active = downloads.count { it.status in listOf(DownloadStatus.DOWNLOADING, DownloadStatus.QUEUED) }
                        if (active > 0) Badge(containerColor = Lime, contentColor = Background) { Text("$active") }
                    }) { Icon(Icons.Outlined.Download, null) } }, label = { Text("Загрузки") },
                    colors = NavigationBarItemDefaults.colors(indicatorColor = Lime.copy(alpha = .14f), selectedIconColor = Lime, selectedTextColor = Lime))
            }
            }
        }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).imePadding()) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(36.dp).clip(RoundedCornerShape(12.dp)).background(Lime), contentAlignment = Alignment.Center) {
                    Icon(Icons.Outlined.PlayArrow, null, tint = Background)
                }
                Text("pocketube", Modifier.padding(start = 10.dp).weight(1f), fontSize = 23.sp, fontWeight = FontWeight.Bold, letterSpacing = (-1).sp)
                if (state.updating) CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp)
                else IconButton(onClick = model::updateEngine, enabled = !state.loading) {
                    Icon(Icons.Outlined.SystemUpdateAlt, "Обновить загрузчик", tint = Muted)
                }
            }
            if (tab == 0) AddScreen(state, model, onDownload = {
                if (Build.VERSION.SDK_INT >= 33) notifications.launch(Manifest.permission.POST_NOTIFICATIONS)
                model.download { tab = 1 }
            })
            else MediaLibraryScreen(downloads, playlists, busy, onAdd = { tab = 0 }, onControl = { model.control(it) },
                onDelete = { deleteItem = it }, onPlay = { item, queue -> expanded = false; playback.play(item, queue) },
                onAssign = { membershipItem = it },
                onCreatePlaylist = { editingPlaylist = null; showPlaylistName = true },
                onRenamePlaylist = { editingPlaylist = it; showPlaylistName = true },
                onDeletePlaylist = { deletingPlaylist = it }, onExport = {
                    exportItem = it
                    val extension = File(it.filePath).extension.ifBlank { if (it.mediaType == MediaType.AUDIO) "mp3" else "mp4" }
                    val fileName = it.title.replace(Regex("[\\\\/:*?\"<>|]"), "_").take(100) + ".$extension"
                    if (it.mediaType == MediaType.AUDIO) exportAudio.launch(fileName) else exportVideo.launch(fileName)
                })
        }
    }
    deleteItem?.let { item ->
        AlertDialog(onDismissRequest = { deleteItem = null }, title = { Text(if (item.mediaType == MediaType.AUDIO) "Удалить аудио?" else "Удалить видео?") },
            text = { Text("«${item.title}» и все его загруженные файлы будут удалены с телефона.") },
            confirmButton = { TextButton(onClick = { playback.remove(item.id); model.control(item, delete = true); deleteItem = null }) { Text("Удалить", color = MaterialTheme.colorScheme.error) } },
            dismissButton = { TextButton(onClick = { deleteItem = null }) { Text("Отмена") } })
    }
    membershipItem?.let { item -> PlaylistMembershipDialog(item, playlists,
        onDismiss = { membershipItem = null }, onCreate = model::createPlaylist,
        onSave = { model.assignPlaylists(item.id, it) }) }
    if (showPlaylistName) PlaylistNameDialog(editingPlaylist, onDismiss = { showPlaylistName = false }, onSave = {
        val current = editingPlaylist
        if (current == null) model.createPlaylist(it) else model.renamePlaylist(current.id, it)
    })
    deletingPlaylist?.let { playlist -> AlertDialog(onDismissRequest = { deletingPlaylist = null },
        title = { Text("Удалить плейлист?") }, text = { Text("«${playlist.name}» будет удалён. Скачанные файлы останутся в медиатеке.") },
        confirmButton = { TextButton(onClick = { model.deletePlaylist(playlist.id); deletingPlaylist = null }) { Text("Удалить") } },
        dismissButton = { TextButton(onClick = { deletingPlaylist = null }) { Text("Отмена") } }) }
    if (expanded && playing.currentId != null) FullPlayer(playing, playback) { expanded = false }
}

@Composable
private fun AddScreen(state: AddState, model: MainViewModel, onDownload: () -> Unit) {
    val clipboard = LocalClipboardManager.current
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp), verticalArrangement = Arrangement.spacedBy(22.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Icon(Icons.Outlined.OfflineBolt, null, Modifier.size(16.dp), tint = Lime)
            Text("СОХРАНИТЕ НА ПОТОМ", color = Lime, fontSize = 11.sp, letterSpacing = 2.sp)
        }
        Text("Видео и музыка.\nВсегда с вами.", fontSize = 36.sp, lineHeight = 40.sp, fontWeight = FontWeight.SemiBold, letterSpacing = (-1.3).sp)
        Text("Сохраните видео целиком или только звук.\nСлушайте и смотрите без интернета.", color = Muted, fontSize = 15.sp, lineHeight = 23.sp)
        Column(Modifier.clip(RoundedCornerShape(24.dp)).background(Surface).padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Text("Ссылка на видео", fontWeight = FontWeight.Medium, fontSize = 14.sp)
            OutlinedTextField(value = state.link, onValueChange = model::link, modifier = Modifier.fillMaxWidth(),
                enabled = !state.loading && !state.updating, singleLine = true, shape = RoundedCornerShape(14.dp),
                placeholder = { Text("youtube.com/watch?v=…", fontSize = 13.sp) },
                leadingIcon = { Icon(Icons.Outlined.Link, null) },
                trailingIcon = { IconButton(onClick = { clipboard.getText()?.text?.let(model::link) }, enabled = !state.loading) {
                    Icon(Icons.Outlined.ContentPaste, "Вставить из буфера")
                } },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Go),
                keyboardActions = KeyboardActions(onGo = { model.inspect() }))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(selected = state.mediaType == MediaType.VIDEO,
                    onClick = { model.mediaType(MediaType.VIDEO) },
                    label = { Text("Видео") }, leadingIcon = { Icon(Icons.Outlined.Videocam, null, Modifier.size(18.dp)) })
                FilterChip(selected = state.mediaType == MediaType.AUDIO,
                    onClick = { model.mediaType(MediaType.AUDIO) },
                    label = { Text("Только аудио") }, leadingIcon = { Icon(Icons.Outlined.Headphones, null, Modifier.size(18.dp)) })
            }
            Button(onClick = model::inspect, enabled = state.link.isNotBlank() && !state.loading && !state.updating,
                modifier = Modifier.fillMaxWidth().height(52.dp), shape = RoundedCornerShape(14.dp)) {
                if (state.loading) {
                    CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.width(10.dp)); Text("Получаем видео…")
                } else {
                    Text("Найти видео", fontWeight = FontWeight.Bold)
                    Spacer(Modifier.width(10.dp)); Icon(Icons.Outlined.ArrowForward, null, Modifier.size(18.dp))
                }
            }
        }
        state.error?.let { Text(it, color = MaterialTheme.colorScheme.error, fontSize = 13.sp) }
        state.preview?.let { video ->
            Column(Modifier.clip(RoundedCornerShape(24.dp)).background(Surface), verticalArrangement = Arrangement.spacedBy(0.dp)) {
                Thumbnail(video.thumbnail, video.duration, Modifier.fillMaxWidth().aspectRatio(16f / 9f))
                Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(video.title, fontSize = 19.sp, fontWeight = FontWeight.SemiBold)
                    Text(video.author, color = Muted, fontSize = 13.sp)
                    if (state.mediaType == MediaType.VIDEO) {
                        Text("Качество видео", fontSize = 13.sp, fontWeight = FontWeight.Medium)
                        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            video.qualities.forEach { quality ->
                                FilterChip(selected = state.quality == quality, onClick = { model.quality(quality) }, label = { Text("${quality}p") })
                            }
                        }
                    } else {
                        Text("MP3 · лучшее доступное качество звука", fontSize = 13.sp, color = Lime)
                    }
                    Button(onClick = onDownload, enabled = !state.updating, modifier = Modifier.fillMaxWidth().height(52.dp), shape = RoundedCornerShape(14.dp)) {
                        Icon(Icons.Outlined.Download, null, Modifier.size(20.dp)); Spacer(Modifier.width(8.dp))
                        Text(if (state.mediaType == MediaType.AUDIO) "Скачать аудио" else "Скачать видео")
                    }
                }
            }
        }
        if (state.preview == null && !state.loading) {
            Row(Modifier.fillMaxWidth().border(1.dp, Color(0xFF30352D), RoundedCornerShape(18.dp)).padding(18.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                Icon(Icons.Outlined.PermDeviceInformation, null, tint = Lime)
                Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    Text("Можно прямо из YouTube", fontSize = 14.sp, fontWeight = FontWeight.Medium)
                    Text("Нажмите «Поделиться» → Pocketube. Ссылка появится здесь автоматически.", fontSize = 12.sp, color = Muted, lineHeight = 18.sp)
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Outlined.Smartphone, null, Modifier.size(14.dp), tint = Muted)
            Text("Файлы хранятся только на вашем устройстве", fontSize = 11.sp, color = Muted)
        }
        Spacer(Modifier.height(8.dp))
    }
}

@Composable
internal fun DownloadCard(item: DownloadItem, busy: Boolean, onControl: (DownloadItem) -> Unit,
    onDelete: (DownloadItem) -> Unit, onPlay: (DownloadItem) -> Unit, onExport: (DownloadItem) -> Unit,
    onAssign: (DownloadItem) -> Unit, playlistNames: List<String>) {
    val complete = item.status == DownloadStatus.COMPLETED
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(Surface).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Thumbnail(item.thumbnail, item.duration, Modifier.width(112.dp).height(72.dp).clip(RoundedCornerShape(12.dp)))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(item.title, maxLines = 2, overflow = TextOverflow.Ellipsis, fontSize = 14.sp, lineHeight = 19.sp, fontWeight = FontWeight.Medium)
                Text(item.author, maxLines = 1, overflow = TextOverflow.Ellipsis, fontSize = 11.sp, color = Muted)
                val format = if (item.mediaType == MediaType.AUDIO) "Аудио · MP3" else "Видео · ${item.quality}p"
                Text("$format${if (complete) " · ${formatSize(item.size)}" else ""}", fontSize = 11.sp, color = Lime)
            }
        }
        if (playlistNames.isNotEmpty()) Text(playlistNames.joinToString(" · "), fontSize = 11.sp, color = Muted,
            maxLines = 1, overflow = TextOverflow.Ellipsis)
        if (!complete) {
            LinearProgressIndicator(progress = { item.progress / 100f }, modifier = Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(4.dp)), trackColor = Color(0xFF343834))
            val label = when (item.status) {
                DownloadStatus.QUEUED -> "В очереди · ожидание сети или свободного места"
                DownloadStatus.DOWNLOADING -> "${item.progress.toInt()}% · ${if (item.eta > 0) "осталось ${formatDuration(item.eta.toInt())}" else "загрузка и обработка…"}"
                DownloadStatus.PAUSED -> "Остановлено · можно продолжить"
                DownloadStatus.ERROR -> "Не удалось скачать"
                else -> ""
            }
            Text(label, fontSize = 11.sp, color = if (item.status == DownloadStatus.ERROR) MaterialTheme.colorScheme.error else Muted)
            if (item.status == DownloadStatus.ERROR && item.error.isNotBlank()) Text(item.error, fontSize = 11.sp, lineHeight = 16.sp, color = Muted)
        }
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            if (complete) {
                FilledTonalButton(onClick = { onPlay(item) }, enabled = !busy, contentPadding = PaddingValues(horizontal = 12.dp), shape = RoundedCornerShape(12.dp)) {
                    Icon(Icons.Outlined.PlayArrow, null, Modifier.size(18.dp))
                    Text(if (item.mediaType == MediaType.AUDIO) "Слушать" else "Смотреть", fontSize = 12.sp)
                }
                IconButton(onClick = { onAssign(item) }, enabled = !busy) { Icon(Icons.Outlined.PlaylistAdd, "Добавить в плейлист", Modifier.size(21.dp)) }
                IconButton(onClick = { onExport(item) }, enabled = !busy) { Icon(Icons.Outlined.SaveAlt, "Сохранить в папку", Modifier.size(21.dp)) }
            } else {
                val active = item.status in listOf(DownloadStatus.QUEUED, DownloadStatus.DOWNLOADING)
                TextButton(onClick = { onControl(item) }, enabled = !busy) {
                    Icon(if (active) Icons.Outlined.Pause else Icons.Outlined.PlayArrow, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(if (active) "Остановить" else if (item.status == DownloadStatus.ERROR) "Повторить" else "Продолжить", fontSize = 12.sp)
                }
            }
            Spacer(Modifier.weight(1f))
            IconButton(onClick = { onDelete(item) }, enabled = !busy) { Icon(Icons.Outlined.DeleteOutline, "Удалить файл", tint = Muted, modifier = Modifier.size(21.dp)) }
        }
    }
}

@Composable
private fun Thumbnail(url: String, duration: Int, modifier: Modifier) {
    Box(modifier.background(Color(0xFF2C302A))) {
        Icon(Icons.Outlined.PlayCircleOutline, null, Modifier.size(36.dp).align(Alignment.Center), tint = Muted)
        AsyncImage(model = url, contentDescription = "Обложка видео", contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        if (duration > 0) Text(formatDuration(duration), color = Color.White, fontSize = 10.sp,
            modifier = Modifier.align(Alignment.BottomEnd).padding(8.dp).clip(RoundedCornerShape(4.dp)).background(Color.Black.copy(alpha = .8f)).padding(horizontal = 5.dp, vertical = 2.dp))
    }
}
