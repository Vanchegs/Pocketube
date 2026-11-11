package com.pocketube.app

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.widget.Toast
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import java.io.File

private val Lime = Color(0xFFC5F277)
private val Background = Color(0xFF101112)
private val Surface = Color(0xFF1C1E20)
private val Muted = Color(0xFF9DA29B)

class MainActivity : ComponentActivity() {
    private val model by viewModels<MainViewModel>()
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
        setContent { PocketubeTheme { PocketubeScreen(model) } }
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
private fun PocketubeScreen(model: MainViewModel) {
    var tab by rememberSaveable { mutableIntStateOf(0) }
    val state by model.state.collectAsStateWithLifecycle()
    val downloads by model.downloads.collectAsStateWithLifecycle()
    val busy by model.busy.collectAsStateWithLifecycle()
    val snack = remember { SnackbarHostState() }
    val context = LocalContext.current
    val notifications = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    var exportItem by remember { mutableStateOf<DownloadItem?>(null) }
    var deleteItem by remember { mutableStateOf<DownloadItem?>(null) }
    val export = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("video/*")) { uri ->
        val item = exportItem
        if (uri != null && item != null) model.export(item, uri)
        exportItem = null
    }
    LaunchedEffect(model) { model.messages.collect { snack.showSnackbar(it) } }

    Scaffold(containerColor = Background, snackbarHost = { SnackbarHost(snack) },
        bottomBar = {
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
            else LibraryScreen(downloads, busy, onAdd = { tab = 0 }, onControl = { model.control(it) },
                onDelete = { deleteItem = it }, onPlay = { item ->
                    try {
                        val file = File(item.filePath)
                        check(file.isFile) { "Файл удалён с устройства" }
                        val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", file)
                        context.startActivity(Intent(Intent.ACTION_VIEW).setDataAndType(uri, "video/*")
                            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION))
                    } catch (e: Exception) {
                        val message = if (e is ActivityNotFoundException) "Установите видеоплеер, например VLC" else e.message
                        Toast.makeText(context, message, Toast.LENGTH_LONG).show()
                    }
                }, onExport = {
                    exportItem = it
                    val extension = File(it.filePath).extension.ifBlank { "mp4" }
                    export.launch(it.title.replace(Regex("[\\\\/:*?\"<>|]"), "_").take(100) + ".$extension")
                })
        }
    }
    deleteItem?.let { item ->
        AlertDialog(onDismissRequest = { deleteItem = null }, title = { Text("Удалить видео?") },
            text = { Text("«${item.title}» и все его загруженные файлы будут удалены с телефона.") },
            confirmButton = { TextButton(onClick = { model.control(item, delete = true); deleteItem = null }) { Text("Удалить", color = MaterialTheme.colorScheme.error) } },
            dismissButton = { TextButton(onClick = { deleteItem = null }) { Text("Отмена") } })
    }
}

@Composable
private fun AddScreen(state: AddState, model: MainViewModel, onDownload: () -> Unit) {
    val clipboard = LocalClipboardManager.current
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp), verticalArrangement = Arrangement.spacedBy(22.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Icon(Icons.Outlined.OfflineBolt, null, Modifier.size(16.dp), tint = Lime)
            Text("СОХРАНИТЕ НА ПОТОМ", color = Lime, fontSize = 11.sp, letterSpacing = 2.sp)
        }
        Text("Любимые видео.\nВсегда с вами.", fontSize = 36.sp, lineHeight = 40.sp, fontWeight = FontWeight.SemiBold, letterSpacing = (-1.3).sp)
        Text("Ссылка с YouTube — и видео в вашем\nкармане. Смотрите даже без интернета.", color = Muted, fontSize = 15.sp, lineHeight = 23.sp)
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
                    Text("Качество видео", fontSize = 13.sp, fontWeight = FontWeight.Medium)
                    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        video.qualities.forEach { quality ->
                            FilterChip(selected = state.quality == quality, onClick = { model.quality(quality) }, label = { Text("${quality}p") })
                        }
                    }
                    Button(onClick = onDownload, enabled = !state.updating, modifier = Modifier.fillMaxWidth().height(52.dp), shape = RoundedCornerShape(14.dp)) {
                        Icon(Icons.Outlined.Download, null, Modifier.size(20.dp)); Spacer(Modifier.width(8.dp)); Text("Скачать видео")
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
            Text("Видео хранятся только на вашем устройстве", fontSize = 11.sp, color = Muted)
        }
        Spacer(Modifier.height(8.dp))
    }
}

@Composable
private fun LibraryScreen(downloads: List<DownloadItem>, busy: Set<String>, onAdd: () -> Unit,
    onControl: (DownloadItem) -> Unit, onDelete: (DownloadItem) -> Unit,
    onPlay: (DownloadItem) -> Unit, onExport: (DownloadItem) -> Unit) {
    var filter by rememberSaveable { mutableIntStateOf(0) }
    val completed = downloads.filter { it.status == DownloadStatus.COMPLETED }
    Column(Modifier.fillMaxSize()) {
        Column(Modifier.padding(horizontal = 24.dp, vertical = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Ваша видеотека", fontSize = 29.sp, fontWeight = FontWeight.SemiBold, letterSpacing = (-.8).sp)
            Text("${completed.size} сохранено · ${formatSize(completed.sumOf { it.size })} на устройстве", color = Muted, fontSize = 13.sp)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("Все", "Загружаются", "Скачанные").forEachIndexed { index, text ->
                    FilterChip(selected = filter == index, onClick = { filter = index }, label = { Text(text, fontSize = 12.sp) })
                }
            }
        }
        val visible = downloads.filter { filter == 0 || if (filter == 2) it.status == DownloadStatus.COMPLETED else it.status != DownloadStatus.COMPLETED }
        if (visible.isEmpty()) {
            Column(Modifier.fillMaxSize().padding(32.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
                Box(Modifier.size(92.dp).clip(RoundedCornerShape(28.dp)).background(Surface), contentAlignment = Alignment.Center) {
                    Icon(Icons.Outlined.VideoLibrary, null, Modifier.size(42.dp), tint = Lime)
                }
                Spacer(Modifier.height(24.dp))
                Text(if (downloads.isEmpty()) "Здесь будут ваши видео" else "Пока ничего нет", fontSize = 21.sp, fontWeight = FontWeight.Medium)
                Spacer(Modifier.height(10.dp))
                Text("Добавьте ссылку и сохраните первое видео\nдля просмотра без интернета.", color = Muted, fontSize = 13.sp, lineHeight = 21.sp)
                Spacer(Modifier.height(24.dp))
                Button(onClick = onAdd, shape = RoundedCornerShape(14.dp)) { Text("Добавить видео") }
            }
        } else LazyColumn(contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            items(visible, key = { it.id }) { item ->
                DownloadCard(item, item.id in busy, onControl, onDelete, onPlay, onExport)
            }
        }
    }
}

@Composable
private fun DownloadCard(item: DownloadItem, busy: Boolean, onControl: (DownloadItem) -> Unit,
    onDelete: (DownloadItem) -> Unit, onPlay: (DownloadItem) -> Unit, onExport: (DownloadItem) -> Unit) {
    val complete = item.status == DownloadStatus.COMPLETED
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(Surface).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Thumbnail(item.thumbnail, item.duration, Modifier.width(112.dp).height(72.dp).clip(RoundedCornerShape(12.dp)))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(item.title, maxLines = 2, overflow = TextOverflow.Ellipsis, fontSize = 14.sp, lineHeight = 19.sp, fontWeight = FontWeight.Medium)
                Text(item.author, maxLines = 1, overflow = TextOverflow.Ellipsis, fontSize = 11.sp, color = Muted)
                Text("${item.quality}p${if (complete) " · ${formatSize(item.size)}" else ""}", fontSize = 11.sp, color = Lime)
            }
        }
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
                    Icon(Icons.Outlined.PlayArrow, null, Modifier.size(18.dp)); Text("Смотреть", fontSize = 12.sp)
                }
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
            IconButton(onClick = { onDelete(item) }, enabled = !busy) { Icon(Icons.Outlined.DeleteOutline, "Удалить видео", tint = Muted, modifier = Modifier.size(21.dp)) }
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
