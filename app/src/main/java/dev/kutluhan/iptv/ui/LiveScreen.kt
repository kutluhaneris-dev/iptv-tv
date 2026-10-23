package dev.kutluhan.iptv.ui

import android.view.ViewGroup
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.currentStateAsState
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import dev.kutluhan.iptv.data.PlayerMode
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Text
import coil.compose.AsyncImage
import dev.kutluhan.iptv.data.Catalog
import dev.kutluhan.iptv.data.EpgProgram
import dev.kutluhan.iptv.data.FavKeys
import dev.kutluhan.iptv.data.LiveChannel
import dev.kutluhan.iptv.data.PlayItem
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

fun LiveChannel.toPlayItem() = PlayItem(name, url, isLive = true, channel = this)

@Composable
fun LiveScreen(vm: AppViewModel, catalog: Catalog, state: HomeState, nav: Navigator) {
    val categories = remember(catalog) {
        buildList {
            add(Triple(ALL_CATEGORIES, "Tüm kanallar", catalog.live.size))
            add(Triple(FAVORITE_CATEGORY, "Favoriler", -1))
            catalog.liveCategories.forEach { c ->
                val n = catalog.liveByCategory[c.id]?.size ?: 0
                if (n > 0) add(Triple(c.id, c.name, n))
            }
        }
    }
    val favorites = vm.favorites
    val channels = remember(catalog, state.liveCategory, if (state.liveCategory == FAVORITE_CATEGORY) favorites else null) {
        when (state.liveCategory) {
            ALL_CATEGORIES -> catalog.live
            FAVORITE_CATEGORY -> catalog.live.filter { FavKeys.live(it.id) in favorites }
            else -> catalog.liveByCategory[state.liveCategory].orEmpty()
        }
    }
    var focused by remember { mutableStateOf<LiveChannel?>(null) }

    val categoryList = rememberLazyListState()
    val selectedCategory = remember { FocusRequester() }
    val selectedIndex = categories.indexOfFirst { it.first == state.liveCategory }
    // Each category starts at its top, instead of keeping the scroll offset of the previous one.
    val channelList = remember(state.liveCategory) { LazyListState() }
    var lastChannel by remember(state.liveCategory) { mutableIntStateOf(0) }
    val channelEntry = remember { FocusRequester() }
    val channelTarget by remember(channelList) {
        derivedStateOf {
            val visible = channelList.layoutInfo.visibleItemsInfo
            if (visible.any { it.index == lastChannel }) lastChannel else channelList.firstVisibleItemIndex
        }
    }

    Row(Modifier.fillMaxHeight()) {
        LazyColumn(
            Modifier
                .width(230.dp)
                .fillMaxHeight()
                .enterAt(selectedCategory) { categoryList.layoutInfo.visibleItemsInfo.any { it.index == selectedIndex } },
            state = categoryList,
        ) {
            itemsIndexed(categories, key = { i, c -> "$i:${c.first}" }) { i, (id, name, count) ->
                TvRow(
                    text = name,
                    secondary = if (count >= 0) "$count kanal" else null,
                    reserveSecondary = true,
                    selected = state.liveCategory == id,
                    modifier = if (i == selectedIndex) Modifier.focusRequester(selectedCategory) else Modifier,
                    onFocus = { state.liveCategory = id },
                    onClick = { state.liveCategory = id },
                )
            }
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.width(340.dp).fillMaxHeight()) {
            if (channels.isEmpty()) {
                EmptyMessage(
                    if (state.liveCategory == FAVORITE_CATEGORY) "Favori kanal yok.\nBir kanalda OK tuşuna basılı tutun."
                    else "Bu kategoride kanal yok."
                )
            } else {
                LazyColumn(
                    Modifier
                        .weight(1f)
                        .enterAt(channelEntry) { channelList.layoutInfo.visibleItemsInfo.any { it.index == channelTarget } },
                    state = channelList,
                ) {
                    itemsIndexed(channels, key = { i, ch -> "$i:${ch.id}" }) { index, ch ->
                        val key = "live:${ch.id}"
                        val now = vm.epg[ch.id]?.firstOrNull { it.isNow() }
                        TvRow(
                            text = ch.name,
                            leading = if (ch.number > 0) ch.number.toString() else null,
                            logo = ch.logo,
                            secondary = now?.title,
                            reserveSecondary = true,
                            favorite = FavKeys.live(ch.id) in favorites,
                            modifier = (if (index == channelTarget) Modifier.focusRequester(channelEntry) else Modifier)
                                .restorableFocus(state, key),
                            onFocus = {
                                focused = ch
                                lastChannel = index
                            },
                            onLongClick = { vm.toggleFavorite(FavKeys.live(ch.id)) },
                            onClick = {
                                vm.storage.lastLiveChannel = ch.id
                                nav.play(channels.map { it.toPlayItem() }, index, key)
                            },
                        )
                    }
                }
                HintBar("OK: izle   ·   OK basılı tut: favori")
            }
        }
        Spacer(Modifier.width(16.dp))
        ChannelPreview(vm, focused, Modifier.weight(1f).fillMaxHeight())
    }
}

private val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())

fun EpgProgram.timeRange(): String = "${timeFormat.format(Date(start))} - ${timeFormat.format(Date(end))}"

@Composable
private fun ChannelPreview(vm: AppViewModel, channel: LiveChannel?, modifier: Modifier) {
    if (channel == null) {
        Box(modifier)
        return
    }
    LaunchedEffect(channel.id) {
        delay(350) // don't fire a request for every channel while scrolling
        vm.loadEpg(channel)
    }
    val programs = vm.epg[channel.id]
    Column(
        modifier
            .clip(RoundedCornerShape(12.dp))
            .background(Palette.panel)
            .padding(18.dp),
    ) {
        val lifecycle = LocalLifecycleOwner.current.lifecycle
        val lifecycleState by lifecycle.currentStateAsState()
        if (vm.livePreview && LocalLayerActive.current && lifecycleState.isAtLeast(Lifecycle.State.RESUMED)) {
            PreviewVideo(channel)
            Spacer(Modifier.height(12.dp))
        } else if (channel.logo != null) {
            AsyncImage(
                model = channel.logo,
                contentDescription = null,
                modifier = Modifier.size(width = 120.dp, height = 68.dp),
                contentScale = ContentScale.Fit,
            )
            Spacer(Modifier.height(10.dp))
        }
        Text(channel.name, fontSize = 20.sp, fontWeight = FontWeight.SemiBold, color = Palette.text, maxLines = 2)
        Spacer(Modifier.height(14.dp))
        when {
            programs == null -> Text("Yayın akışı yükleniyor...", color = Palette.textDim, fontSize = 14.sp)
            programs.isEmpty() -> Text("Yayın akışı yok.", color = Palette.textDim, fontSize = 14.sp)
            else -> ProgramList(programs.take(6))
        }
    }
}

@Composable
fun ProgramList(programs: List<EpgProgram>) {
    val now = System.currentTimeMillis()
    programs.forEachIndexed { i, p ->
        val current = p.isNow(now)
        Column(Modifier.fillMaxWidth().padding(bottom = 10.dp)) {
            Text(
                (if (current) "ŞİMDİ  " else "") + p.timeRange(),
                fontSize = 12.sp,
                color = if (current) Palette.accent else Palette.textDim,
            )
            Text(p.title, fontSize = if (i == 0) 16.sp else 14.sp, color = Palette.text, maxLines = 1)
            if (current) {
                val progress = ((now - p.start).toFloat() / (p.end - p.start)).coerceIn(0f, 1f)
                ProgressLine(progress, Modifier.padding(top = 4.dp))
                if (p.description != null) {
                    Text(p.description, fontSize = 12.sp, color = Palette.textDim, maxLines = 3, modifier = Modifier.padding(top = 4.dp))
                }
            }
        }
    }
}

@Composable
fun ProgressLine(progress: Float, modifier: Modifier = Modifier) {
    Box(
        modifier
            .fillMaxWidth()
            .height(4.dp)
            .clip(RoundedCornerShape(2.dp))
            .background(Palette.panelAlt),
    ) {
        Box(
            Modifier
                .fillMaxWidth(progress)
                .height(4.dp)
                .background(Palette.accent),
        )
    }
}

/** Plays the focused channel in a small box once focus has rested on it for a moment. */
@Composable
private fun PreviewVideo(channel: LiveChannel) {
    val context = LocalContext.current
    val player = remember { buildPlayer(context, PlayerMode.STANDARD, light = true) }
    var ready by remember { mutableStateOf(false) }
    var failed by remember { mutableStateOf(false) }
    DisposableEffect(player) {
        val listener = object : Player.Listener {
            override fun onRenderedFirstFrame() {
                ready = true
            }

            override fun onPlayerError(error: PlaybackException) {
                ready = false
                failed = true
            }
        }
        player.addListener(listener)
        onDispose {
            player.removeListener(listener)
            player.release()
        }
    }
    LaunchedEffect(channel.id) {
        ready = false
        failed = false
        player.stop()
        player.clearMediaItems()
        delay(900) // only open a stream once the user stops on a channel
        player.setMediaItem(mediaItemFor(channel.toPlayItem()))
        player.prepare()
        player.playWhenReady = true
    }
    Box(
        Modifier
            .fillMaxWidth()
            .aspectRatio(16f / 9f)
            .clip(RoundedCornerShape(8.dp))
            .background(Color.Black),
    ) {
        AndroidView(
            factory = { ctx ->
                PlayerView(ctx).apply {
                    useController = false
                    isFocusable = false
                    isFocusableInTouchMode = false
                    descendantFocusability = ViewGroup.FOCUS_BLOCK_DESCENDANTS
                    resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                    setShowBuffering(PlayerView.SHOW_BUFFERING_NEVER)
                    this.player = player
                }
            },
            modifier = Modifier.fillMaxSize(),
        )
        if (!ready) {
            Column(Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally) {
                if (channel.logo != null) {
                    AsyncImage(
                        model = channel.logo,
                        contentDescription = null,
                        modifier = Modifier.size(width = 120.dp, height = 68.dp),
                        contentScale = ContentScale.Fit,
                    )
                    Spacer(Modifier.height(8.dp))
                }
                Text(
                    if (failed) "Önizleme açılamadı" else "Önizleme yükleniyor...",
                    color = Palette.textDim,
                    fontSize = 12.sp,
                )
            }
        }
    }
}
