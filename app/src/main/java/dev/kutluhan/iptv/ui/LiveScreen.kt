package dev.kutluhan.iptv.ui

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

    Row(Modifier.fillMaxHeight()) {
        LazyColumn(
            Modifier
                .width(230.dp)
                .fillMaxHeight(),
        ) {
            itemsIndexed(categories, key = { i, c -> "$i:${c.first}" }) { _, (id, name, count) ->
                TvRow(
                    text = name,
                    secondary = if (count >= 0) "$count kanal" else null,
                    selected = state.liveCategory == id,
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
                LazyColumn(Modifier.weight(1f)) {
                    itemsIndexed(channels, key = { i, ch -> "$i:${ch.id}" }) { index, ch ->
                        val key = "live:${ch.id}"
                        val now = vm.epg[ch.id]?.firstOrNull { it.isNow() }
                        TvRow(
                            text = ch.name,
                            leading = if (ch.number > 0) ch.number.toString() else null,
                            logo = ch.logo,
                            secondary = now?.title,
                            favorite = FavKeys.live(ch.id) in favorites,
                            modifier = Modifier.restorableFocus(state, key),
                            onFocus = { focused = ch },
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
        if (channel.logo != null) {
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
