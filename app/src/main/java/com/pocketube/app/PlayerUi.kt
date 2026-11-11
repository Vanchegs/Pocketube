package com.pocketube.app

import android.view.LayoutInflater
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.media3.common.Player
import androidx.media3.ui.PlayerView
import coil.compose.AsyncImage

@Composable
private fun VideoSurface(player: Player?, modifier: Modifier = Modifier, keepAwake: Boolean = false) {
    AndroidView(modifier = modifier.background(Color.Black), factory = { context ->
        (LayoutInflater.from(context).inflate(R.layout.player_surface, null) as PlayerView).apply {
            this.player = player
            keepScreenOn = keepAwake
        }
    }, update = {
        if (it.player !== player) it.player = player
        it.keepScreenOn = keepAwake
    }, onRelease = { it.player = null })
}

@Composable
fun MiniPlayer(state: PlaybackState, model: PlaybackViewModel, expanded: Boolean, onExpand: () -> Unit) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp)
        .clip(RoundedCornerShape(18.dp)).background(Surface).clickable(onClick = onExpand)) {
        Row(Modifier.fillMaxWidth().padding(start = 8.dp, top = 8.dp, bottom = 8.dp, end = 2.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Box(Modifier.width(92.dp).height(54.dp).clip(RoundedCornerShape(12.dp)).background(Color(0xFF2A352D)),
                contentAlignment = Alignment.Center) {
                if (!state.isAudio && !expanded) VideoSurface(model.player, Modifier.fillMaxSize(), state.playing || state.buffering)
                else {
                    Icon(Icons.Outlined.Headphones, null, Modifier.size(28.dp), tint = Lime)
                    if (state.thumbnail.isNotBlank()) AsyncImage(state.thumbnail, "Обложка", Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                }
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(state.title, maxLines = 1, overflow = TextOverflow.Ellipsis, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                Text(if (state.error != null) "Ошибка воспроизведения" else if (state.isAudio) "Аудио · ${playbackTime(state.positionMs)}" else "Видео · ${playbackTime(state.positionMs)}",
                    color = Muted, fontSize = 10.sp, maxLines = 1)
            }
            IconButton(onClick = model::toggle, modifier = Modifier.size(40.dp)) {
                if (state.buffering) CircularProgressIndicator(Modifier.size(19.dp), strokeWidth = 2.dp)
                else Icon(if (state.playing) Icons.Outlined.Pause else Icons.Outlined.PlayArrow,
                    if (state.playing) "Пауза" else "Воспроизвести", tint = Lime)
            }
            IconButton(onClick = model::stop, modifier = Modifier.size(36.dp)) { Icon(Icons.Outlined.Close, "Остановить плеер", Modifier.size(18.dp), tint = Muted) }
        }
        LinearProgressIndicator(progress = { if (state.durationMs > 0) (state.positionMs.toFloat() / state.durationMs).coerceIn(0f, 1f) else 0f },
            modifier = Modifier.fillMaxWidth().height(2.dp), trackColor = Color(0xFF343B33))
    }
}

@Composable
fun FullPlayer(state: PlaybackState, model: PlaybackViewModel, onCollapse: () -> Unit) {
    Dialog(onDismissRequest = onCollapse, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        Box(Modifier.fillMaxSize().background(Color.Black)) {
            if (state.isAudio) AnimatedAudioBackground()
            Column(Modifier.fillMaxSize().systemBarsPadding()) {
                Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onCollapse) { Icon(Icons.Outlined.KeyboardArrowDown, "Свернуть плеер", tint = Color.White) }
                    Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("СЕЙЧАС ИГРАЕТ", color = Color.White.copy(alpha = .6f), fontSize = 10.sp, letterSpacing = 2.sp)
                        Text(if (state.isAudio) "Только аудио" else "Видео", fontSize = 13.sp, color = Color.White)
                    }
                    IconButton(onClick = { model.stop(); onCollapse() }) { Icon(Icons.Outlined.Close, "Остановить плеер", tint = Color.White) }
                }
                if (state.isAudio) {
                    Column(Modifier.fillMaxWidth().weight(1f).padding(24.dp), verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally) {
                        Box(Modifier.size(190.dp).clip(RoundedCornerShape(34.dp)).background(Color.White.copy(alpha = .1f)), contentAlignment = Alignment.Center) {
                            Icon(Icons.Outlined.Headphones, null, Modifier.size(82.dp), tint = Lime)
                            if (state.thumbnail.isNotBlank()) AsyncImage(state.thumbnail, "Обложка аудио", Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                        }
                        Spacer(Modifier.height(28.dp))
                        Text("MP3 · ОФЛАЙН", fontSize = 10.sp, color = Color.White.copy(alpha = .6f), letterSpacing = 2.sp)
                    }
                } else VideoSurface(model.player, Modifier.fillMaxWidth().weight(1f), state.playing || state.buffering)
                Column(Modifier.fillMaxWidth().padding(horizontal = 28.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(state.title, fontSize = 23.sp, lineHeight = 28.sp, color = Color.White,
                        fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Text(state.author, color = Color.White.copy(alpha = .6f), fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    state.error?.let { Text(it, color = MaterialTheme.colorScheme.error, fontSize = 12.sp) }
                    PlaybackControls(state, model)
                }
                Spacer(Modifier.height(16.dp))
            }
        }
    }
}

@Composable
private fun PlaybackControls(state: PlaybackState, model: PlaybackViewModel) {
    var dragging by remember(state.currentId) { mutableStateOf<Float?>(null) }
    val progress = if (state.durationMs > 0) (state.positionMs.toFloat() / state.durationMs).coerceIn(0f, 1f) else 0f
    Slider(value = dragging ?: progress, onValueChange = { dragging = it }, enabled = state.durationMs > 0,
        onValueChangeFinished = {
            dragging?.let { model.seekTo((it * state.durationMs).toLong()) }
            dragging = null
        }, modifier = Modifier.fillMaxWidth())
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(playbackTime(dragging?.let { (it * state.durationMs).toLong() } ?: state.positionMs), color = Color.White.copy(alpha = .6f), fontSize = 11.sp)
        Text(playbackTime(state.durationMs), color = Color.White.copy(alpha = .6f), fontSize = 11.sp)
    }
    Row(Modifier.fillMaxWidth().padding(top = 12.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceEvenly) {
        IconButton(onClick = { model.seekTo(state.positionMs - 10_000) }, enabled = state.durationMs > 0) { Icon(Icons.Outlined.Replay10, "Назад 10 секунд", tint = Color.White) }
        IconButton(onClick = model::previous, enabled = state.hasPrevious) { Icon(Icons.Outlined.SkipPrevious, "Предыдущий", tint = if (state.hasPrevious) Color.White else Muted) }
        FilledIconButton(onClick = model::toggle, modifier = Modifier.size(70.dp), shape = CircleShape) {
            if (state.buffering) CircularProgressIndicator(Modifier.size(26.dp), color = Background, strokeWidth = 2.dp)
            else Icon(if (state.playing) Icons.Outlined.Pause else Icons.Outlined.PlayArrow,
                if (state.playing) "Пауза" else "Воспроизвести", Modifier.size(34.dp))
        }
        IconButton(onClick = model::next, enabled = state.hasNext) { Icon(Icons.Outlined.SkipNext, "Следующий", tint = if (state.hasNext) Color.White else Muted) }
        IconButton(onClick = { model.seekTo(state.positionMs + 10_000) }, enabled = state.durationMs > 0) { Icon(Icons.Outlined.Forward10, "Вперёд 10 секунд", tint = Color.White) }
    }
}

@Composable
private fun AnimatedAudioBackground() {
    val animation = rememberInfiniteTransition(label = "audio-gradient")
    val phase by animation.animateFloat(0f, 1f, infiniteRepeatable(tween(9_000, easing = LinearEasing), RepeatMode.Reverse), label = "color-flow")
    Canvas(Modifier.fillMaxSize()) {
        drawRect(Brush.linearGradient(listOf(lerp(Color(0xFF123B40), Color(0xFF4C245B), phase),
            Color(0xFF102828), lerp(Color(0xFF331E46), Color(0xFF285746), phase)),
            start = Offset(size.width * phase, 0f), end = Offset(size.width * (1f - phase), size.height)))
        drawRect(Brush.radialGradient(listOf(Color(0xFF9270C9).copy(alpha = .4f), Color.Transparent),
            center = Offset(size.width * (.1f + .8f * phase), size.height * .3f), radius = size.width * 1.15f))
        drawRect(Brush.radialGradient(listOf(Lime.copy(alpha = .22f), Color.Transparent),
            center = Offset(size.width * (1f - phase), size.height * .75f), radius = size.width * .9f))
        drawRect(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = .55f))))
    }
}

private fun playbackTime(milliseconds: Long) = formatDuration((milliseconds.coerceAtLeast(0) / 1000).coerceAtMost(Int.MAX_VALUE.toLong()).toInt())
