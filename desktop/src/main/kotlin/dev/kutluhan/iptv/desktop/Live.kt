package dev.kutluhan.iptv.desktop

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.kutluhan.iptv.data.Catalog
import dev.kutluhan.iptv.data.FavKeys
import dev.kutluhan.iptv.data.LiveChannel
import dev.kutluhan.iptv.data.PlayItem
import kotlinx.coroutines.delay
import java.util.Locale

fun LiveChannel.toPlayItem() = PlayItem(name, url, isLive = true, channel = this)

@Composable
fun LiveScreen(app: AppState, catalog: Catalog, state: HomeState, nav: Navigator, player: VideoPlayer?) {
    val favorites = app.favorites
    val rows = remember(catalog) {
        buildList {
            add(CategoryRow(ALL_CATEGORIES, "Tümü", catalog.live.size))
            add(CategoryRow(FAVORITE_CATEGORY, "Favoriler", -1))
            catalog.liveCategories.forEach { c ->
                catalog.liveByCategory[c.id]?.size?.takeIf { it > 0 }?.let { add(CategoryRow(c.id, c.name, it)) }
            }
        }
    }
    val filter = state.liveFilter.trim().lowercase(Locale.getDefault())
    val channels = remember(catalog, state.liveCategory, filter, if (state.liveCategory == FAVORITE_CATEGORY) favorites else null) {
        val base = when (state.liveCategory) {
            ALL_CATEGORIES -> catalog.live
            FAVORITE_CATEGORY -> catalog.live.filter { FavKeys.live(it.id) in favorites }
            else -> catalog.liveByCategory[state.liveCategory].orEmpty()
        }
        if (filter.isEmpty()) base else base.filter { filter in it.name.lowercase(Locale.getDefault()) }
    }
    val selected = remember(catalog, state.liveSelected) { catalog.live.firstOrNull { it.id == state.liveSelected } }

    fun openFull(ch: LiveChannel) {
        val list = channels.ifEmpty { listOf(ch) }
        val index = list.indexOfFirst { it.id == ch.id }.coerceAtLeast(0)
        state.liveSelected = ch.id
        nav.play(list.map { it.toPlayItem() }, index)
    }

    Row(Modifier.fillMaxSize()) {
        CategoryList(rows, state.liveCategory, "kanal", { state.liveCategory = it })
        Spacer(Modifier.width(12.dp))
        ChannelColumn(app, channels, selected, state, onSelect = {
            state.liveSelected = it.id
            app.loadEpg(it)
        }, onOpen = ::openFull)
        Spacer(Modifier.width(16.dp))
        ChannelPreview(app, selected, player, Modifier.weight(1f), onOpen = { selected?.let(::openFull) })
    }
}

@Composable
private fun ChannelColumn(
    app: AppState,
    channels: List<LiveChannel>,
    selected: LiveChannel?,
    state: HomeState,
    onSelect: (LiveChannel) -> Unit,
    onOpen: (LiveChannel) -> Unit,
) {
    val listState = rememberLazyListState()
    val focus = remember { FocusRequester() }
    val selectedIndex = channels.indexOfFirst { it.id == selected?.id }
    LaunchedEffect(selectedIndex) {
        if (selectedIndex < 0) return@LaunchedEffect
        val visible = listState.layoutInfo.visibleItemsInfo
        val first = visible.firstOrNull()?.index ?: 0
        val last = visible.lastOrNull()?.index ?: 0
        if (selectedIndex <= first || selectedIndex >= last) {
            listState.scrollToItem((selectedIndex - visible.size / 2).coerceAtLeast(0))
        }
    }
    Column(Modifier.width(380.dp).fillMaxHeight()) {
        OutlinedTextField(
            value = state.liveFilter,
            onValueChange = { state.liveFilter = it },
            singleLine = true,
            placeholder = { Text("Bu listede kanal ara", fontSize = 13.sp) },
            leadingIcon = { Icon(Icons.Filled.Search, null, tint = Palette.textDim) },
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 8.dp)
                .onPreviewKeyEvent {
                    if (it.type == KeyEventType.KeyDown && it.key == Key.DirectionDown && channels.isNotEmpty()) {
                        onSelect(channels[0])
                        focus.requestFocus()
                        true
                    } else {
                        false
                    }
                },
        )
        if (channels.isEmpty()) {
            EmptyMessage(if (state.liveCategory == FAVORITE_CATEGORY) "Favori kanal yok.\nBir kanalın yanındaki ☆ ile ekleyebilirsiniz." else "Kanal yok.")
            return@Column
        }
        Box(
            Modifier
                .weight(1f)
                .focusRequester(focus)
                .focusable()
                .onPreviewKeyEvent { e ->
                    if (e.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                    val i = selectedIndex
                    when (e.key) {
                        Key.DirectionDown -> { onSelect(channels[(i + 1).coerceAtMost(channels.lastIndex)]); true }
                        Key.DirectionUp -> { onSelect(channels[(i - 1).coerceAtLeast(0)]); true }
                        Key.PageDown -> { onSelect(channels[(i + 10).coerceAtMost(channels.lastIndex)]); true }
                        Key.PageUp -> { onSelect(channels[(i - 10).coerceAtLeast(0)]); true }
                        Key.Enter, Key.NumPadEnter -> { selected?.let(onOpen); true }
                        else -> false
                    }
                },
        ) {
            LazyColumn(Modifier.fillMaxSize().padding(end = 8.dp), state = listState) {
                itemsIndexed(channels, key = { i, c -> "$i:${c.id}" }) { _, ch ->
                    val fav = FavKeys.live(ch.id) in app.favorites
                    ListRow(
                        text = ch.name,
                        leading = if (ch.number > 0) ch.number.toString() else null,
                        logo = ch.logo,
                        secondary = app.epg[ch.id]?.firstOrNull { it.isNow() }?.title ?: "",
                        selected = ch.id == selected?.id,
                        favorite = fav,
                        onToggleFavorite = { app.toggleFavorite(FavKeys.live(ch.id)) },
                        onDoubleClick = { onOpen(ch) },
                        onClick = {
                            focus.requestFocus()
                            onSelect(ch)
                        },
                    )
                    LaunchedEffect(ch.id) {
                        // Only rows that stay on screen a moment fetch their guide.
                        delay(400)
                        app.loadEpg(ch)
                    }
                }
            }
            Scrollbar(listState, Modifier.align(Alignment.CenterEnd))
        }
        Text(
            "Çift tık veya Enter: tam ekran  ·  ↑↓: kanal  ·  sağ tık: favori",
            color = Palette.textDim,
            fontSize = 11.sp,
            modifier = Modifier.padding(top = 6.dp),
        )
    }
}

@Composable
private fun ChannelPreview(app: AppState, channel: LiveChannel?, player: VideoPlayer?, modifier: Modifier, onOpen: () -> Unit) {
    val active = LocalLayerActive.current
    LaunchedEffect(channel?.id, active, app.livePreview) {
        if (!active || player == null || channel == null || !app.livePreview) return@LaunchedEffect
        // Let quick arrow presses settle before opening a stream.
        delay(350)
        if (player.url != channel.url) player.play(channel.url, app.userAgent, live = true)
    }
    Column(modifier.fillMaxHeight()) {
        Box(
            Modifier
                .fillMaxWidth()
                .aspectRatio(16f / 9f)
                .clip(RoundedCornerShape(10.dp))
                .background(Color.Black)
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, enabled = channel != null) { onOpen() },
            contentAlignment = Alignment.Center,
        ) {
            when {
                player == null -> VlcMissing()
                channel == null -> Text("Önizleme için bir kanal seçin", color = Palette.textDim)
                !app.livePreview -> Text("Önizleme kapalı · izlemek için tıklayın", color = Palette.textDim)
                else -> {
                    val frame = player.frame
                    if (frame != null && player.url == channel.url) {
                        Image(frame, null, contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize())
                    }
                    when {
                        player.state == PlayState.ERROR && player.url == channel.url ->
                            Text("Kanal açılamadı", color = Palette.error)
                        player.url != channel.url || player.state == PlayState.OPENING || frame == null ->
                            CircularProgressIndicator(Modifier.size(36.dp), color = Palette.accent)
                    }
                    Icon(
                        Icons.Filled.Fullscreen,
                        "Tam ekran",
                        tint = Color.White.copy(alpha = 0.8f),
                        modifier = Modifier.align(Alignment.BottomEnd).padding(10.dp).size(26.dp),
                    )
                }
            }
        }
        if (channel != null) {
            Row(Modifier.padding(top = 12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(channel.name, color = Palette.text, fontSize = 20.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                FavoriteStar(FavKeys.live(channel.id) in app.favorites, true, { app.toggleFavorite(FavKeys.live(channel.id)) })
            }
            val programs = app.epg[channel.id]
            when {
                programs == null -> Text("Yayın akışı yükleniyor...", color = Palette.textDim, fontSize = 14.sp)
                programs.isEmpty() -> Text("Yayın akışı yok.", color = Palette.textDim, fontSize = 14.sp)
                else -> ProgramList(programs, Modifier.padding(top = 6.dp).weight(1f))
            }
        }
    }
}

@Composable
fun VlcMissing(modifier: Modifier = Modifier) {
    val uri = LocalUriHandler.current
    Column(modifier.padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text("Video için VLC gerekli", color = Palette.text, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
        Text(
            "Bu uygulama görüntüyü VLC'nin motoruyla oynatır. Ücretsiz VLC'yi kurup uygulamayı yeniden açın.",
            color = Palette.textDim,
            fontSize = 14.sp,
            modifier = Modifier.padding(top = 6.dp),
        )
        TextButton(onClick = { uri.openUri(Vlc.DOWNLOAD_URL) }) { Text("VLC'yi indir (videolan.org)") }
    }
}
