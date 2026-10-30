package dev.kutluhan.iptv.desktop

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Forward10
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.FullscreenExit
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Replay10
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.Subtitles
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.onPointerEvent
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.WindowPlacement
import dev.kutluhan.iptv.data.FavKeys
import dev.kutluhan.iptv.data.Http
import dev.kutluhan.iptv.data.PlayItem
import kotlinx.coroutines.delay
import java.awt.Point
import java.awt.Toolkit
import java.awt.image.BufferedImage

private const val OVERLAY_MS = 3500L

private fun formatTime(ms: Long): String {
    val total = (ms / 1000).coerceAtLeast(0)
    val h = total / 3600
    val m = (total % 3600) / 60
    val s = total % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%02d:%02d".format(m, s)
}

private val blankCursor: PointerIcon by lazy {
    PointerIcon(
        Toolkit.getDefaultToolkit().createCustomCursor(BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB), Point(0, 0), "blank"),
    )
}

@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun PlayerScreen(
    app: AppState,
    player: VideoPlayer?,
    items: List<PlayItem>,
    startIndex: Int,
    onIndexChange: (PlayItem) -> Unit,
    onClose: (keepPlaying: Boolean) -> Unit,
) {
    val window = LocalWindowState.current
    val wasFullscreen = remember { window.placement == WindowPlacement.Fullscreen }
    var index by remember { mutableIntStateOf(startIndex.coerceIn(0, items.lastIndex)) }
    val item = items[index]
    var overlayUntil by remember { mutableLongStateOf(System.currentTimeMillis() + OVERLAY_MS) }
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var menuOpen by remember { mutableStateOf(false) }
    var digits by remember { mutableStateOf("") }
    var diagnosis by remember { mutableStateOf<String?>(null) }
    var debug by remember { mutableStateOf(false) }
    val focus = remember { FocusRequester() }

    fun showOverlay() {
        overlayUntil = System.currentTimeMillis() + OVERLAY_MS
    }

    fun close() {
        if (!wasFullscreen && window.placement == WindowPlacement.Fullscreen) window.placement = WindowPlacement.Floating
        onClose(item.isLive)
    }

    fun toggleFullscreen() {
        window.placement = if (window.placement == WindowPlacement.Fullscreen) WindowPlacement.Floating else WindowPlacement.Fullscreen
    }

    fun goTo(newIndex: Int) {
        if (items.isEmpty()) return
        index = ((newIndex % items.size) + items.size) % items.size
        showOverlay()
    }

    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
    LaunchedEffect(index) {
        diagnosis = null
        onIndexChange(item)
        item.channel?.let { app.loadEpg(it) }
        if (player != null && (player.url != item.url || player.state == PlayState.ERROR || player.state == PlayState.IDLE)) {
            player.play(item.url, app.userAgent, item.isLive)
        }
    }
    LaunchedEffect(Unit) {
        while (true) {
            delay(500)
            now = System.currentTimeMillis()
        }
    }
    // Typed channel numbers jump once typing pauses.
    LaunchedEffect(digits) {
        if (digits.isEmpty()) return@LaunchedEffect
        delay(1500)
        val n = digits.toIntOrNull()
        digits = ""
        val target = items.indexOfFirst { it.channel?.number == n }
        if (target >= 0) goTo(target)
    }
    val state = player?.state
    LaunchedEffect(state) {
        when (state) {
            PlayState.ENDED -> if (!item.isLive && index < items.lastIndex) goTo(index + 1)
            PlayState.ERROR -> diagnosis = Http.probe(item.url, ua = app.userAgent)
            else -> Unit
        }
    }
    DisposableEffect(Unit) { onDispose { app.storage.volume = player?.volume ?: 100 } }

    val overlay = menuOpen || now < overlayUntil || state == PlayState.PAUSED || state == PlayState.ERROR
    val isFavorite = item.channel?.let { FavKeys.live(it.id) in app.favorites }

    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black)
            .blockPointer()
            .focusRequester(focus)
            .focusable()
            .pointerHoverIcon(if (overlay) PointerIcon.Default else blankCursor)
            .onPointerEvent(PointerEventType.Move) { showOverlay() }
            .onPointerEvent(PointerEventType.Scroll) { e ->
                val dy = e.changes.firstOrNull()?.scrollDelta?.y ?: 0f
                if (dy != 0f) {
                    player?.changeVolume(player.volume + if (dy < 0) 5 else -5)
                    showOverlay()
                }
            }
            .pointerInput(Unit) {
                detectTapGestures(
                    onDoubleTap = { toggleFullscreen() },
                    onTap = {
                        focus.requestFocus()
                        showOverlay()
                    },
                )
            }
            .onPreviewKeyEvent { e ->
                if (e.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                showOverlay()
                val p = player
                when (e.key) {
                    Key.Escape -> {
                        if (menuOpen) menuOpen = false
                        else if (window.placement == WindowPlacement.Fullscreen && !wasFullscreen) window.placement = WindowPlacement.Floating
                        else close()
                    }
                    Key.Backspace -> close()
                    Key.Spacebar, Key.K -> p?.togglePause()
                    Key.F, Key.Enter -> toggleFullscreen()
                    Key.M -> p?.toggleMute()
                    Key.D -> debug = !debug
                    Key.A, Key.S -> menuOpen = !menuOpen
                    Key.DirectionLeft -> p?.seekBy(if (e.isShiftPressed) -60_000 else -10_000)
                    Key.DirectionRight -> p?.seekBy(if (e.isShiftPressed) 60_000 else 10_000)
                    Key.DirectionUp -> if (item.isLive) goTo(index - 1) else p?.changeVolume(p.volume + 5)
                    Key.DirectionDown -> if (item.isLive) goTo(index + 1) else p?.changeVolume(p.volume - 5)
                    Key.PageUp -> goTo(index - 1)
                    Key.PageDown -> goTo(index + 1)
                    Key.Plus, Key.NumPadAdd, Key.Equals -> p?.changeVolume(p.volume + 5)
                    Key.Minus, Key.NumPadSubtract -> p?.changeVolume(p.volume - 5)
                    else -> {
                        val d = digitOf(e.key)
                        if (d != null && item.isLive) digits = (digits + d).takeLast(4) else return@onPreviewKeyEvent false
                    }
                }
                true
            },
        contentAlignment = Alignment.Center,
    ) {
        if (player == null) {
            VlcMissing()
        } else {
            val frame = player.frame
            if (frame != null && player.url == item.url) {
                Image(frame, null, contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize())
            }
            if (state == PlayState.OPENING || (state == PlayState.PLAYING && frame == null)) {
                CircularProgressIndicator(Modifier.size(48.dp), color = Palette.accent)
            }
            if (state == PlayState.ERROR) {
                Column(
                    Modifier.background(Color.Black.copy(alpha = 0.7f)).padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(if (item.isLive) "Kanal açılamadı" else "Video açılamadı", color = Color.White, fontSize = 20.sp)
                    Text(
                        diagnosis ?: "Sunucu yanıtı kontrol ediliyor...",
                        color = Palette.textDim,
                        fontSize = 13.sp,
                        modifier = Modifier.padding(top = 6.dp, bottom = 12.dp),
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Button(onClick = { player.play(item.url, app.userAgent, item.isLive) }) { Text("Tekrar dene") }
                        OutlinedButton(onClick = { close() }) { Text("Geri") }
                    }
                }
            }
        }

        if (debug && player != null) {
            // Re-read on every clock tick (now) so the numbers stay current.
            val info = remember(now / 1000) { player.describeVideo() }
            Text(
                "durum: ${player.state} · kare: ${player.renderedFrames} · tampon: ${(player.buffering).toInt()}%\n" +
                    "video: $info\nses parçası: ${player.audioTracks.size} · zaman: ${formatTime(player.time)}",
                color = Color.Yellow,
                fontSize = 13.sp,
                modifier = Modifier.align(Alignment.TopStart).padding(top = 70.dp, start = 24.dp).background(Color.Black.copy(alpha = 0.6f)).padding(8.dp),
            )
        }

        if (digits.isNotEmpty()) {
            Text(
                digits,
                color = Color.White,
                fontSize = 40.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.align(Alignment.TopEnd).padding(28.dp),
            )
        }

        if (overlay) {
            // Top: back, title, favorite.
            Row(
                Modifier
                    .align(Alignment.TopCenter)
                    .fillMaxWidth()
                    .background(topShade)
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = { close() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Geri", tint = Color.White) }
                val ch = item.channel
                Text(
                    (if (ch != null && ch.number > 0) "${ch.number}  " else "") + item.title,
                    color = Color.White,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f).padding(start = 6.dp),
                )
                if (items.size > 1) Text("${index + 1} / ${items.size}", color = Palette.textDim, fontSize = 13.sp)
                if (ch != null && isFavorite != null) {
                    Spacer(Modifier.width(10.dp))
                    FavoriteStar(isFavorite, true, { app.toggleFavorite(FavKeys.live(ch.id)) })
                }
            }

            // Bottom: guide or timeline, then the controls.
            Column(
                Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .background(bottomShade)
                    .padding(start = 24.dp, end = 24.dp, top = 40.dp, bottom = 14.dp),
            ) {
                val ch = item.channel
                if (ch != null) {
                    val programs = app.epg[ch.id].orEmpty()
                    val current = programs.firstOrNull { it.isNow(now) }
                    val next = programs.firstOrNull { it.start >= (current?.end ?: now) }
                    if (current != null) {
                        Text("${current.timeRange()}  ${current.title}", color = Color.White, fontSize = 16.sp, maxLines = 1)
                        ProgressLine(
                            (now - current.start).toFloat() / (current.end - current.start).coerceAtLeast(1),
                            Modifier.padding(vertical = 6.dp),
                        )
                    }
                    if (next != null) {
                        Text("Sonra: ${next.timeRange()}  ${next.title}", color = Palette.textDim, fontSize = 13.sp, maxLines = 1)
                    }
                } else if (player != null && player.length > 0) {
                    var dragging by remember { mutableStateOf<Float?>(null) }
                    Slider(
                        value = dragging ?: (player.time.toFloat() / player.length).coerceIn(0f, 1f),
                        onValueChange = {
                            dragging = it
                            showOverlay()
                        },
                        onValueChangeFinished = {
                            dragging?.let { f -> player.seekTo((f * player.length).toLong()) }
                            dragging = null
                        },
                        colors = SliderDefaults.colors(thumbColor = Color.White, activeTrackColor = Palette.accent),
                    )
                }
                if (player != null) {
                    Controls(
                        player = player,
                        item = item,
                        hasList = items.size > 1,
                        fullscreen = window.placement == WindowPlacement.Fullscreen,
                        menuOpen = menuOpen,
                        onMenu = { menuOpen = it },
                        onPrev = { goTo(index - 1) },
                        onNext = { goTo(index + 1) },
                        onFullscreen = { toggleFullscreen() },
                    )
                }
                Text(
                    if (item.isLive) "Boşluk: duraklat  ·  ↑↓: kanal  ·  rakamlar: kanal no  ·  F / çift tık: tam ekran  ·  M: ses kapat  ·  A: ses/altyazı  ·  D: bilgi  ·  Esc: geri"
                    else "Boşluk: duraklat  ·  ←→: 10 sn  ·  Shift+←→: 1 dk  ·  ↑↓: ses  ·  F / çift tık: tam ekran  ·  A: ses/altyazı  ·  Esc: geri",
                    color = Palette.textDim,
                    fontSize = 11.sp,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
        }
    }
}

@Composable
private fun Controls(
    player: VideoPlayer,
    item: PlayItem,
    hasList: Boolean,
    fullscreen: Boolean,
    menuOpen: Boolean,
    onMenu: (Boolean) -> Unit,
    onPrev: () -> Unit,
    onNext: () -> Unit,
    onFullscreen: () -> Unit,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        if (hasList) IconButton(onClick = onPrev) { Icon(Icons.Filled.SkipPrevious, "Önceki", tint = Color.White) }
        if (!item.isLive) IconButton(onClick = { player.seekBy(-10_000) }) { Icon(Icons.Filled.Replay10, "10 sn geri", tint = Color.White) }
        IconButton(onClick = { player.togglePause() }) {
            Icon(
                if (player.state == PlayState.PAUSED) Icons.Filled.PlayArrow else Icons.Filled.Pause,
                if (player.state == PlayState.PAUSED) "Oynat" else "Duraklat",
                tint = Color.White,
                modifier = Modifier.size(32.dp),
            )
        }
        if (!item.isLive) IconButton(onClick = { player.seekBy(10_000) }) { Icon(Icons.Filled.Forward10, "10 sn ileri", tint = Color.White) }
        if (hasList) IconButton(onClick = onNext) { Icon(Icons.Filled.SkipNext, "Sonraki", tint = Color.White) }
        if (!item.isLive && player.length > 0) {
            Text("${formatTime(player.time)} / ${formatTime(player.length)}", color = Color.White, fontSize = 13.sp, modifier = Modifier.padding(start = 8.dp))
        }
        Spacer(Modifier.weight(1f))
        IconButton(onClick = { player.toggleMute() }) {
            Icon(
                if (player.muted || player.volume == 0) Icons.AutoMirrored.Filled.VolumeOff else Icons.AutoMirrored.Filled.VolumeUp,
                "Ses",
                tint = Color.White,
            )
        }
        Slider(
            value = player.volume.toFloat(),
            onValueChange = { player.changeVolume(it.toInt()) },
            valueRange = 0f..150f,
            modifier = Modifier.width(140.dp),
            colors = SliderDefaults.colors(thumbColor = Color.White, activeTrackColor = Palette.accent),
        )
        Box {
            IconButton(onClick = { onMenu(!menuOpen) }) { Icon(Icons.Filled.Subtitles, "Ses ve altyazı", tint = Color.White) }
            DropdownMenu(expanded = menuOpen, onDismissRequest = { onMenu(false) }) {
                Text("Ses", color = Palette.textDim, fontSize = 12.sp, modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp))
                if (player.audioTracks.isEmpty()) {
                    DropdownMenuItem(text = { Text("Ses parçası bilgisi yok") }, onClick = {}, enabled = false)
                }
                player.audioTracks.forEach { t ->
                    DropdownMenuItem(
                        text = { Text((if (t.id == player.audioTrack) "✓ " else "    ") + t.label) },
                        onClick = { player.selectAudio(t.id) },
                    )
                }
                HorizontalDivider()
                Text("Altyazı", color = Palette.textDim, fontSize = 12.sp, modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp))
                DropdownMenuItem(
                    text = { Text((if (player.subtitleTrack < 0) "✓ " else "    ") + "Kapalı") },
                    onClick = { player.selectSubtitle(-1) },
                )
                player.subtitleTracks.forEach { t ->
                    DropdownMenuItem(
                        text = { Text((if (t.id == player.subtitleTrack) "✓ " else "    ") + t.label) },
                        onClick = { player.selectSubtitle(t.id) },
                    )
                }
            }
        }
        IconButton(onClick = onFullscreen) {
            Icon(if (fullscreen) Icons.Filled.FullscreenExit else Icons.Filled.Fullscreen, "Tam ekran", tint = Color.White)
        }
    }
}

private fun digitOf(key: Key): Int? = when (key) {
    Key.Zero, Key.NumPad0 -> 0
    Key.One, Key.NumPad1 -> 1
    Key.Two, Key.NumPad2 -> 2
    Key.Three, Key.NumPad3 -> 3
    Key.Four, Key.NumPad4 -> 4
    Key.Five, Key.NumPad5 -> 5
    Key.Six, Key.NumPad6 -> 6
    Key.Seven, Key.NumPad7 -> 7
    Key.Eight, Key.NumPad8 -> 8
    Key.Nine, Key.NumPad9 -> 9
    else -> null
}
