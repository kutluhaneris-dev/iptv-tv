package dev.kutluhan.iptv.desktop

import androidx.compose.foundation.VerticalScrollbar
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollbarAdapter
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.foundation.focusable
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import dev.kutluhan.iptv.data.Catalog
import dev.kutluhan.iptv.data.Category
import dev.kutluhan.iptv.data.Episode
import dev.kutluhan.iptv.data.FavKeys
import dev.kutluhan.iptv.data.PlayItem
import dev.kutluhan.iptv.data.SeriesItem
import dev.kutluhan.iptv.data.VodItem
import kotlinx.coroutines.CancellationException

fun VodItem.toPlayItem() = PlayItem(name, url, isLive = false)

fun Episode.toPlayItem(series: SeriesItem) = PlayItem("${series.name} · S$season B$number · $title", url, isLive = false)

private fun mediaRows(categories: List<Category>, counts: Map<String, Int>, total: Int) = buildList {
    add(CategoryRow(ALL_CATEGORIES, "Tümü", total))
    add(CategoryRow(RECENT_CATEGORY, "Yeni eklenenler", -1))
    add(CategoryRow(FAVORITE_CATEGORY, "Favoriler", -1))
    categories.forEach { c -> counts[c.id]?.takeIf { it > 0 }?.let { add(CategoryRow(c.id, c.name, it)) } }
}

@Composable
private fun PosterGrid(key: Any, content: androidx.compose.foundation.lazy.grid.LazyGridScope.() -> Unit, modifier: Modifier = Modifier) {
    val state = remember(key) { LazyGridState() }
    Box(modifier.fillMaxHeight()) {
        LazyVerticalGrid(
            columns = GridCells.Adaptive(150.dp),
            modifier = Modifier.fillMaxSize().padding(end = 10.dp),
            state = state,
            content = content,
        )
        VerticalScrollbar(rememberScrollbarAdapter(state), Modifier.align(Alignment.CenterEnd).fillMaxHeight())
    }
}

@Composable
fun VodScreen(app: AppState, catalog: Catalog, state: HomeState, nav: Navigator) {
    val rows = remember(catalog) { mediaRows(catalog.vodCategories, catalog.vodByCategory.mapValues { it.value.size }, catalog.vod.size) }
    val favorites = app.favorites
    val items = remember(catalog, state.vodCategory, if (state.vodCategory == FAVORITE_CATEGORY) favorites else null) {
        when (state.vodCategory) {
            ALL_CATEGORIES -> catalog.vod
            RECENT_CATEGORY -> catalog.vod.sortedByDescending { it.added }.take(RECENT_LIMIT)
            FAVORITE_CATEGORY -> catalog.vod.filter { FavKeys.vod(it.id) in favorites }
            else -> catalog.vodByCategory[state.vodCategory].orEmpty()
        }
    }
    Row(Modifier.fillMaxSize()) {
        CategoryList(rows, state.vodCategory, "film", { state.vodCategory = it })
        Spacer(Modifier.width(12.dp))
        if (items.isEmpty()) {
            EmptyMessage(if (catalog.vod.isEmpty()) "Bu sağlayıcıda film yok." else "Burada film yok.")
        } else {
            PosterGrid(state.vodCategory, {
                itemsIndexed(items, key = { i, v -> "$i:${v.id}" }) { _, v ->
                    PosterCard(
                        title = v.name,
                        image = v.poster,
                        subtitle = v.rating?.let { "★ $it" },
                        favorite = FavKeys.vod(v.id) in favorites,
                        onToggleFavorite = { app.toggleFavorite(FavKeys.vod(v.id)) },
                        onClick = { nav.play(listOf(v.toPlayItem()), 0) },
                    )
                }
            }, Modifier.weight(1f))
        }
    }
}

@Composable
fun SeriesScreen(app: AppState, catalog: Catalog, state: HomeState, nav: Navigator) {
    val rows = remember(catalog) { mediaRows(catalog.seriesCategories, catalog.seriesByCategory.mapValues { it.value.size }, catalog.series.size) }
    val favorites = app.favorites
    val items = remember(catalog, state.seriesCategory, if (state.seriesCategory == FAVORITE_CATEGORY) favorites else null) {
        when (state.seriesCategory) {
            ALL_CATEGORIES -> catalog.series
            RECENT_CATEGORY -> catalog.series.sortedByDescending { it.added }.take(RECENT_LIMIT)
            FAVORITE_CATEGORY -> catalog.series.filter { FavKeys.series(it.id) in favorites }
            else -> catalog.seriesByCategory[state.seriesCategory].orEmpty()
        }
    }
    Row(Modifier.fillMaxSize()) {
        CategoryList(rows, state.seriesCategory, "dizi", { state.seriesCategory = it })
        Spacer(Modifier.width(12.dp))
        if (items.isEmpty()) {
            EmptyMessage(if (catalog.series.isEmpty()) "Bu sağlayıcıda dizi yok." else "Burada dizi yok.")
        } else {
            PosterGrid(state.seriesCategory, {
                itemsIndexed(items, key = { i, s -> "$i:${s.id}" }) { _, s ->
                    PosterCard(
                        title = s.name,
                        image = s.poster,
                        subtitle = s.rating?.let { "★ $it" },
                        favorite = FavKeys.series(s.id) in favorites,
                        onToggleFavorite = { app.toggleFavorite(FavKeys.series(s.id)) },
                        onClick = { nav.openSeries(s) },
                    )
                }
            }, Modifier.weight(1f))
        }
    }
}

@Composable
fun SeriesDetailScreen(app: AppState, series: SeriesItem, nav: Navigator, onBack: () -> Unit) {
    var episodes by remember(series.id) { mutableStateOf<List<Episode>?>(null) }
    var error by remember(series.id) { mutableStateOf<String?>(null) }
    var season by remember(series.id) { mutableStateOf<Int?>(null) }
    LaunchedEffect(series.id) {
        try {
            val list = app.episodes(series)
            episodes = list
            season = list.firstOrNull()?.season
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            error = e.message ?: "Bölümler alınamadı."
            episodes = emptyList()
        }
    }
    val focus = remember { FocusRequester() }
    val active = LocalLayerActive.current
    LaunchedEffect(active) { if (active) runCatching { focus.requestFocus() } }
    Row(
        Modifier
            .fillMaxSize()
            .background(Palette.background)
            .blockPointer()
            .focusRequester(focus)
            .focusable()
            .onPreviewKeyEvent {
                if (it.type == KeyEventType.KeyDown && (it.key == Key.Escape || it.key == Key.Backspace)) {
                    onBack()
                    true
                } else {
                    false
                }
            }
            .padding(24.dp),
    ) {
        Column(Modifier.width(260.dp)) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Geri", tint = Palette.text) }
            Box(Modifier.fillMaxWidth().aspectRatio(2f / 3f).clip(RoundedCornerShape(12.dp)).background(Palette.panelAlt)) {
                if (series.poster != null) {
                    AsyncImage(series.poster, null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                }
            }
            Row(Modifier.padding(top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(series.name, color = Palette.text, fontSize = 20.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                FavoriteStar(FavKeys.series(series.id) in app.favorites, true, { app.toggleFavorite(FavKeys.series(series.id)) })
            }
            series.rating?.let { Text("★ $it", color = Palette.star, fontSize = 13.sp) }
            series.plot?.let { Text(it, color = Palette.textDim, fontSize = 13.sp, modifier = Modifier.padding(top = 8.dp)) }
        }
        Spacer(Modifier.width(28.dp))
        Column(Modifier.weight(1f).fillMaxHeight()) {
            val list = episodes
            when {
                list == null -> EmptyMessage("Bölümler yükleniyor...")
                list.isEmpty() -> EmptyMessage(error ?: "Bu dizide bölüm bulunamadı.")
                else -> {
                    val seasons = remember(list) { list.map { it.season }.distinct() }
                    Row(Modifier.padding(bottom = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        seasons.forEach { s ->
                            FilterChip(selected = s == season, onClick = { season = s }, label = { Text("$s. Sezon") })
                        }
                    }
                    val inSeason = list.filter { it.season == season }
                    val listState = rememberLazyListState()
                    Box(Modifier.weight(1f)) {
                        LazyColumn(Modifier.fillMaxSize().padding(end = 10.dp), state = listState) {
                            itemsIndexed(inSeason, key = { i, e -> "$i:${e.id}" }) { i, e ->
                                ListRow(
                                    text = "${e.number}. ${e.title}",
                                    secondary = e.plot ?: "",
                                    logo = e.image,
                                    onClick = { nav.play(inSeason.map { it.toPlayItem(series) }, i) },
                                )
                            }
                        }
                        Scrollbar(listState, Modifier.align(Alignment.CenterEnd))
                    }
                }
            }
        }
    }
}
