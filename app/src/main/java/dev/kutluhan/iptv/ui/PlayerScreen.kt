package dev.kutluhan.iptv.ui

import android.content.Context
import android.view.ViewGroup
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.extractor.DefaultExtractorsFactory
import androidx.media3.extractor.ts.DefaultTsPayloadReaderFactory
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import androidx.tv.material3.Text
import coil.compose.AsyncImage
import dev.kutluhan.iptv.data.Http
import dev.kutluhan.iptv.data.PlayItem
import kotlinx.coroutines.delay
import java.util.Locale

private fun buildPlayer(context: Context): ExoPlayer {
    val http = OkHttpDataSource.Factory(Http.client)
    val extractors = DefaultExtractorsFactory()
        .setTsExtractorFlags(
            DefaultTsPayloadReaderFactory.FLAG_ALLOW_NON_IDR_KEYFRAMES or
                DefaultTsPayloadReaderFactory.FLAG_DETECT_ACCESS_UNITS
        )
        .setConstantBitrateSeekingEnabled(true)
    val mediaSources = DefaultMediaSourceFactory(DefaultDataSource.Factory(context, http), extractors)
    val renderers = DefaultRenderersFactory(context)
        .setEnableDecoderFallback(true)
        .setExtensionRendererMode(DefaultRenderersFactory.EXTENSION_RENDERER_MODE_PREFER)
    return ExoPlayer.Builder(context, renderers)
        .setMediaSourceFactory(mediaSources)
        .build()
        .apply {
            setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(C.USAGE_MEDIA)
                    .setContentType(C.AUDIO_CONTENT_TYPE_MOVIE)
                    .build(),
                true,
            )
        }
}

private fun mediaItemFor(item: PlayItem): MediaItem {
    val path = item.url.lowercase(Locale.ROOT).substringBefore('?')
    return MediaItem.Builder()
        .setUri(item.url)
        .apply { if (path.endsWith(".m3u8") || path.contains("/hls/")) setMimeType(MimeTypes.APPLICATION_M3U8) }
        .build()
}

private fun describe(e: PlaybackException): String = when (e.errorCode) {
    PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS -> "Sunucu yayını reddetti (HTTP hatası). Hesabınızda aynı anda izleme sınırı dolmuş olabilir."
    PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED,
    PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT -> "Bağlantı kurulamadı. İnternetinizi kontrol edin."
    PlaybackException.ERROR_CODE_PARSING_CONTAINER_UNSUPPORTED,
    PlaybackException.ERROR_CODE_PARSING_CONTAINER_MALFORMED -> "Yayın formatı okunamadı. Ayarlardan canlı yayın formatını değiştirmeyi deneyin."
    PlaybackException.ERROR_CODE_DECODER_INIT_FAILED,
    PlaybackException.ERROR_CODE_DECODING_FORMAT_UNSUPPORTED -> "Bu TV yayının kodekini desteklemiyor."
    else -> "Oynatılamadı: ${e.errorCodeName}"
}

private fun formatTime(ms: Long): String {
    if (ms <= 0 || ms == C.TIME_UNSET) return "0:00"
    val total = ms / 1000
    val h = total / 3600
    val m = (total % 3600) / 60
    val s = total % 60
    return if (h > 0) String.format(Locale.US, "%d:%02d:%02d", h, m, s) else String.format(Locale.US, "%d:%02d", m, s)
}

@Composable
fun PlayerScreen(vm: AppViewModel, items: List<PlayItem>, startIndex: Int, onIndexChange: (Int) -> Unit) {
    val context = LocalContext.current
    val player = remember { buildPlayer(context) }
    var index by remember { mutableIntStateOf(startIndex.coerceIn(0, items.lastIndex)) }
    val item = items[index]
    val currentItem by rememberUpdatedState(item)
    val currentIndex by rememberUpdatedState(index)

    var error by remember { mutableStateOf<String?>(null) }
    var buffering by remember { mutableStateOf(true) }
    var playing by remember { mutableStateOf(false) }
    var overlay by remember { mutableStateOf(true) }
    var overlayTick by remember { mutableIntStateOf(0) }
    var position by remember { mutableLongStateOf(0L) }
    var duration by remember { mutableLongStateOf(0L) }
    var retries by remember { mutableIntStateOf(0) }
    var digits by remember { mutableStateOf("") }
    val focus = remember { FocusRequester() }

    fun showOverlay() {
        overlay = true
        overlayTick++
    }

    fun goTo(newIndex: Int) {
        if (items.isEmpty()) return
        index = ((newIndex % items.size) + items.size) % items.size
        onIndexChange(index)
    }

    DisposableEffect(player) {
        val listener = object : Player.Listener {
            override fun onPlaybackStateChanged(state: Int) {
                buffering = state == Player.STATE_BUFFERING
                if (state == Player.STATE_READY) error = null
                if (state == Player.STATE_ENDED && !currentItem.isLive && currentIndex < items.lastIndex) {
                    goTo(currentIndex + 1)
                }
            }

            override fun onIsPlayingChanged(isPlaying: Boolean) {
                playing = isPlaying
                if (isPlaying) retries = 0
            }

            override fun onPlayerError(e: PlaybackException) {
                if (e.errorCode == PlaybackException.ERROR_CODE_BEHIND_LIVE_WINDOW) {
                    player.seekToDefaultPosition()
                    player.prepare()
                    return
                }
                error = describe(e)
            }
        }
        player.addListener(listener)
        onDispose {
            player.removeListener(listener)
            player.release()
        }
    }

    LaunchedEffect(index) {
        error = null
        retries = 0
        player.setMediaItem(mediaItemFor(item))
        player.prepare()
        player.playWhenReady = true
        item.channel?.let { vm.loadEpg(it) }
        showOverlay()
    }

    // Live streams drop now and then; quietly reconnect a few times before giving up.
    LaunchedEffect(error) {
        if (error != null && currentItem.isLive && retries < 3) {
            delay(2000)
            retries++
            error = null
            player.prepare()
            player.playWhenReady = true
        }
    }

    LaunchedEffect(overlayTick, playing, error) {
        if (playing && error == null) {
            delay(5000)
            overlay = false
        }
    }

    LaunchedEffect(overlay, index) {
        while (overlay) {
            position = player.currentPosition
            duration = player.duration
            delay(500)
        }
    }

    // Typing a channel number with the remote jumps to it.
    LaunchedEffect(digits) {
        if (digits.isEmpty()) return@LaunchedEffect
        delay(1500)
        val number = digits.toIntOrNull()
        val target = items.indexOfFirst { it.channel?.number == number }
        if (target >= 0) goTo(target)
        digits = ""
    }

    LaunchedEffect(Unit) {
        delay(50)
        runCatching { focus.requestFocus() }
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black)
            .focusRequester(focus)
            .onKeyEvent { event ->
                if (event.type != KeyEventType.KeyDown) return@onKeyEvent false
                val live = currentItem.isLive
                when (event.key) {
                    Key.DirectionCenter, Key.Enter, Key.NumPadEnter -> {
                        when {
                            error != null -> {
                                error = null
                                retries = 0
                                player.prepare()
                                player.playWhenReady = true
                            }
                            live -> if (overlay) overlay = false else showOverlay()
                            else -> {
                                if (player.isPlaying) player.pause() else player.play()
                                showOverlay()
                            }
                        }
                        true
                    }
                    Key.MediaPlayPause, Key.MediaPlay, Key.MediaPause -> {
                        if (player.isPlaying) player.pause() else player.play()
                        showOverlay()
                        true
                    }
                    Key.DirectionUp -> {
                        if (live) goTo(currentIndex - 1) else showOverlay()
                        true
                    }
                    Key.DirectionDown -> {
                        if (live) goTo(currentIndex + 1) else showOverlay()
                        true
                    }
                    Key.ChannelUp, Key.MediaNext -> {
                        goTo(currentIndex + 1)
                        true
                    }
                    Key.ChannelDown, Key.MediaPrevious -> {
                        goTo(currentIndex - 1)
                        true
                    }
                    Key.DirectionLeft, Key.MediaRewind -> {
                        if (!live) player.seekTo((player.currentPosition - if (event.key == Key.MediaRewind) 60_000 else 10_000).coerceAtLeast(0))
                        showOverlay()
                        true
                    }
                    Key.DirectionRight, Key.MediaFastForward -> {
                        if (!live) player.seekTo(player.currentPosition + if (event.key == Key.MediaFastForward) 60_000 else 10_000)
                        showOverlay()
                        true
                    }
                    else -> {
                        val digit = digitOf(event.key)
                        if (digit != null && live) {
                            digits = (digits + digit).takeLast(5)
                            true
                        } else {
                            false
                        }
                    }
                }
            }
            .focusable(),
    ) {
        AndroidView(
            factory = { ctx ->
                PlayerView(ctx).apply {
                    layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
                    useController = false
                    isFocusable = false
                    isFocusableInTouchMode = false
                    descendantFocusability = ViewGroup.FOCUS_BLOCK_DESCENDANTS
                    keepScreenOn = true
                    resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                    setShowBuffering(PlayerView.SHOW_BUFFERING_NEVER)
                    this.player = player
                }
            },
            modifier = Modifier.fillMaxSize(),
        )

        if (buffering && error == null) {
            Text(
                "Yükleniyor...",
                color = Color.White,
                fontSize = 18.sp,
                modifier = Modifier
                    .align(Alignment.Center)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color.Black.copy(alpha = 0.6f))
                    .padding(horizontal = 18.dp, vertical = 10.dp),
            )
        }

        if (digits.isNotEmpty()) {
            Text(
                digits,
                color = Color.White,
                fontSize = 40.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(32.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color.Black.copy(alpha = 0.6f))
                    .padding(horizontal = 18.dp, vertical = 6.dp),
            )
        }

        if (error != null) {
            Column(
                Modifier
                    .align(Alignment.Center)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color.Black.copy(alpha = 0.8f))
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(item.title, color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(8.dp))
                Text(error!!, color = Palette.error, fontSize = 15.sp)
                Spacer(Modifier.height(8.dp))
                Text("OK: tekrar dene   ·   Geri: çık", color = Palette.textDim, fontSize = 13.sp)
            }
        }

        if (overlay || !playing) {
            InfoOverlay(vm, item, index, items.size, position, duration, playing, Modifier.align(Alignment.BottomCenter))
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

@Composable
private fun InfoOverlay(
    vm: AppViewModel,
    item: PlayItem,
    index: Int,
    count: Int,
    position: Long,
    duration: Long,
    playing: Boolean,
    modifier: Modifier,
) {
    Column(
        modifier
            .fillMaxWidth()
            .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.92f))))
            .padding(start = 40.dp, end = 40.dp, top = 60.dp, bottom = 28.dp),
    ) {
        val channel = item.channel
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (channel?.logo != null) {
                AsyncImage(
                    model = channel.logo,
                    contentDescription = null,
                    modifier = Modifier.size(width = 90.dp, height = 52.dp),
                    contentScale = ContentScale.Fit,
                )
                Spacer(Modifier.width(16.dp))
            }
            Column(Modifier.weight(1f)) {
                val number = channel?.number?.takeIf { it > 0 }?.let { "$it  " } ?: ""
                Text(number + item.title, color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
                if (count > 1) {
                    Text("${index + 1} / $count", color = Palette.textDim, fontSize = 13.sp)
                }
            }
            if (!playing) {
                Text("Duraklatıldı", color = Palette.textDim, fontSize = 16.sp)
            }
        }
        Spacer(Modifier.height(12.dp))
        if (item.isLive && channel != null) {
            val programs = vm.epg[channel.id].orEmpty()
            val now = programs.firstOrNull { it.isNow() }
            val next = programs.firstOrNull { it.start >= (now?.end ?: System.currentTimeMillis()) }
            if (now != null) {
                Text("${now.timeRange()}  ${now.title}", color = Color.White, fontSize = 16.sp, maxLines = 1)
                val progress = ((System.currentTimeMillis() - now.start).toFloat() / (now.end - now.start)).coerceIn(0f, 1f)
                ProgressLine(progress, Modifier.padding(vertical = 6.dp))
            }
            if (next != null) {
                Text("Sonra: ${next.timeRange()}  ${next.title}", color = Palette.textDim, fontSize = 14.sp, maxLines = 1)
            }
            Text("▲▼ kanal değiştir   ·   rakamlar: kanal numarası   ·   OK: bilgiyi gizle", color = Palette.textDim, fontSize = 12.sp, modifier = Modifier.padding(top = 8.dp))
        } else if (!item.isLive) {
            val progress = if (duration > 0) (position.toFloat() / duration).coerceIn(0f, 1f) else 0f
            ProgressLine(progress)
            Row(Modifier.fillMaxWidth().padding(top = 6.dp)) {
                Text(formatTime(position), color = Color.White, fontSize = 14.sp)
                Spacer(Modifier.weight(1f))
                Text(formatTime(duration), color = Color.White, fontSize = 14.sp)
            }
            Text("OK: oynat/duraklat   ·   ◀▶ 10 sn   ·   ⏪⏩ 1 dk", color = Palette.textDim, fontSize = 12.sp, modifier = Modifier.padding(top = 8.dp))
        }
    }
}
