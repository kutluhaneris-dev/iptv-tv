package dev.kutluhan.iptv.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Button
import androidx.tv.material3.Text
import coil.compose.AsyncImage
import dev.kutluhan.iptv.data.Catalog
import dev.kutluhan.iptv.data.Category
import dev.kutluhan.iptv.data.Episode
import dev.kutluhan.iptv.data.FavKeys
import dev.kutluhan.iptv.data.PlayItem
import dev.kutluhan.iptv.data.SeriesItem
import dev.kutluhan.iptv.data.VodItem
import kotlinx.coroutines.delay

fun VodItem.toPlayItem() = PlayItem(name, url, isLive = false)

@Composable
private fun CategoryColumn(
    categories: List<Category>,
    counts: Map<String, Int>,
    total: Int,
    selected: String,
    unit: String,
    onSelect: (String) -> Unit,
) {
    val rows = remember(categories, counts) {
        buildList {
            add(Triple(ALL_CATEGORIES, "Tümü", total))
            add(Triple(FAVORITE_CATEGORY, "Favoriler", -1))
            categories.forEach { c -> counts[c.id]?.takeIf { it > 0 }?.let { add(Triple(c.id, c.name, it)) } }
        }
    }
    LazyColumn(
        Modifier
            .width(230.dp)
            .fillMaxHeight()
            .focusRestorer(),
    ) {
        itemsIndexed(rows, key = { i, r -> "$i:${r.first}" }) { _, (id, name, count) ->
            TvRow(
                text = name,
                secondary = if (count >= 0) "$count $unit" else null,
                selected = selected == id,
                onFocus = { onSelect(id) },
                onClick = { onSelect(id) },
            )
        }
    }
}

@Composable
fun VodScreen(vm: AppViewModel, catalog: Catalog, state: HomeState, nav: Navigator) {
    val counts = remember(catalog) { catalog.vodByCategory.mapValues { it.value.size } }
    val favorites = vm.favorites
    val items = remember(catalog, state.vodCategory, if (state.vodCategory == FAVORITE_CATEGORY) favorites else null) {
        when (state.vodCategory) {
            ALL_CATEGORIES -> catalog.vod
            FAVORITE_CATEGORY -> catalog.vod.filter { FavKeys.vod(it.id) in favorites }
            else -> catalog.vodByCategory[state.vodCategory].orEmpty()
        }
    }
    Row(Modifier.fillMaxSize()) {
        CategoryColumn(catalog.vodCategories, counts, catalog.vod.size, state.vodCategory, "film") { state.vodCategory = it }
        Spacer(Modifier.width(12.dp))
        if (items.isEmpty()) {
            EmptyMessage(if (catalog.vod.isEmpty()) "Bu sağlayıcıda film yok." else "Burada film yok.")
        } else {
            LazyVerticalGrid(
                columns = GridCells.Adaptive(128.dp),
                modifier = Modifier.weight(1f).focusRestorer(),
            ) {
                itemsIndexed(items, key = { i, v -> "$i:${v.id}" }) { _, v ->
                    val key = "vod:${v.id}"
                    PosterCard(
                        title = v.name,
                        image = v.poster,
                        subtitle = v.rating?.let { "★ $it" },
                        favorite = FavKeys.vod(v.id) in favorites,
                        modifier = Modifier.restorableFocus(state, key),
                        onLongClick = { vm.toggleFavorite(FavKeys.vod(v.id)) },
                        onClick = { nav.play(listOf(v.toPlayItem()), 0, key) },
                    )
                }
            }
        }
    }
}

@Composable
fun SeriesScreen(vm: AppViewModel, catalog: Catalog, state: HomeState, nav: Navigator) {
    val counts = remember(catalog) { catalog.seriesByCategory.mapValues { it.value.size } }
    val favorites = vm.favorites
    val items = remember(catalog, state.seriesCategory, if (state.seriesCategory == FAVORITE_CATEGORY) favorites else null) {
        when (state.seriesCategory) {
            ALL_CATEGORIES -> catalog.series
            FAVORITE_CATEGORY -> catalog.series.filter { FavKeys.series(it.id) in favorites }
            else -> catalog.seriesByCategory[state.seriesCategory].orEmpty()
        }
    }
    if (catalog.series.isEmpty()) {
        EmptyMessage("Bu sağlayıcıda dizi yok. (M3U listelerinde diziler Filmler bölümünde görünür.)")
        return
    }
    Row(Modifier.fillMaxSize()) {
        CategoryColumn(catalog.seriesCategories, counts, catalog.series.size, state.seriesCategory, "dizi") { state.seriesCategory = it }
        Spacer(Modifier.width(12.dp))
        if (items.isEmpty()) {
            EmptyMessage("Burada dizi yok.")
        } else {
            LazyVerticalGrid(
                columns = GridCells.Adaptive(128.dp),
                modifier = Modifier.weight(1f).focusRestorer(),
            ) {
                itemsIndexed(items, key = { i, s -> "$i:${s.id}" }) { _, s ->
                    val key = "series:${s.id}"
                    PosterCard(
                        title = s.name,
                        image = s.poster,
                        subtitle = s.rating?.let { "★ $it" },
                        favorite = FavKeys.series(s.id) in favorites,
                        modifier = Modifier.restorableFocus(state, key),
                        onLongClick = { vm.toggleFavorite(FavKeys.series(s.id)) },
                        onClick = { nav.openSeries(s, key) },
                    )
                }
            }
        }
    }
}

/** Season picker and episode list. Its own [HomeState] keeps focus restoration local to this page. */
@Composable
fun SeriesDetailScreen(vm: AppViewModel, series: SeriesItem, pageState: HomeState, nav: Navigator) {
    var episodes by remember(series.id) { mutableStateOf<List<Episode>?>(null) }
    var error by remember(series.id) { mutableStateOf<String?>(null) }
    var season by remember(series.id) { mutableStateOf<Int?>(null) }
    val firstFocus = remember { FocusRequester() }

    LaunchedEffect(series.id) {
        try {
            val list = vm.episodes(series)
            episodes = list
            season = list.firstOrNull()?.season
        } catch (e: Exception) {
            error = e.message ?: "Bölümler yüklenemedi."
        }
        delay(100)
        runCatching { firstFocus.requestFocus() }
    }

    val fav = vm.isFavorite(FavKeys.series(series.id))
    Row(
        Modifier
            .fillMaxSize()
            .background(Palette.background)
            .padding(32.dp),
    ) {
        Column(Modifier.width(220.dp)) {
            Box(
                Modifier
                    .width(200.dp)
                    .aspectRatio(2f / 3f)
                    .clip(RoundedCornerShape(10.dp))
                    .background(Palette.panelAlt),
            ) {
                if (series.poster != null) {
                    AsyncImage(series.poster, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                }
            }
            Spacer(Modifier.height(12.dp))
            Button(
                onClick = { vm.toggleFavorite(FavKeys.series(series.id)) },
                modifier = Modifier.focusRequester(firstFocus),
            ) {
                Text(if (fav) "Favorilerden çıkar" else "Favorilere ekle")
            }
        }
        Spacer(Modifier.width(24.dp))
        Column(Modifier.weight(1f)) {
            Text(series.name, fontSize = 26.sp, fontWeight = FontWeight.SemiBold, color = Palette.text, maxLines = 2)
            if (series.plot != null) {
                Text(series.plot, fontSize = 13.sp, color = Palette.textDim, maxLines = 3, modifier = Modifier.padding(top = 6.dp))
            }
            Spacer(Modifier.height(14.dp))
            val list = episodes
            when {
                error != null -> Text(error!!, color = Palette.error)
                list == null -> Text("Bölümler yükleniyor...", color = Palette.textDim)
                list.isEmpty() -> Text("Bu dizide bölüm bulunamadı.", color = Palette.textDim)
                else -> {
                    val seasons = remember(list) { list.map { it.season }.distinct() }
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.focusRestorer(),
                    ) {
                        itemsIndexed(seasons, key = { _, s -> s }) { _, s ->
                            Button(onClick = { season = s }) {
                                Text(if (season == s) "✓ Sezon $s" else "Sezon $s")
                            }
                        }
                    }
                    Spacer(Modifier.height(10.dp))
                    val seasonEpisodes = list.filter { it.season == season }
                    LazyColumn(Modifier.fillMaxHeight().focusRestorer()) {
                        itemsIndexed(seasonEpisodes, key = { i, e -> "$i:${e.id}" }) { index, e ->
                            val key = "ep:${e.id}"
                            TvRow(
                                text = "${e.number}. ${e.title}",
                                secondary = e.plot,
                                modifier = Modifier.restorableFocus(pageState, key),
                                onClick = {
                                    nav.play(
                                        seasonEpisodes.map { PlayItem("${series.name} · S${it.season}B${it.number}", it.url, isLive = false) },
                                        index,
                                        key,
                                    )
                                },
                            )
                        }
                    }
                }
            }
        }
    }
}
