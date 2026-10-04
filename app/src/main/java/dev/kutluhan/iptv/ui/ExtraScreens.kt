package dev.kutluhan.iptv.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Button
import androidx.tv.material3.Text
import dev.kutluhan.iptv.BuildConfig
import dev.kutluhan.iptv.data.Catalog
import dev.kutluhan.iptv.data.DEFAULT_USER_AGENT
import dev.kutluhan.iptv.data.FavKeys
import dev.kutluhan.iptv.data.LiveChannel
import dev.kutluhan.iptv.data.LiveFormat
import dev.kutluhan.iptv.data.M3uProfile
import dev.kutluhan.iptv.data.SeriesItem
import dev.kutluhan.iptv.data.VodItem
import dev.kutluhan.iptv.data.XtreamProfile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.util.Locale

@Composable
fun FavoritesScreen(vm: AppViewModel, catalog: Catalog, state: HomeState, nav: Navigator) {
    val favorites = vm.favorites
    val channels = remember(catalog, favorites) { catalog.live.filter { FavKeys.live(it.id) in favorites } }
    val movies = remember(catalog, favorites) { catalog.vod.filter { FavKeys.vod(it.id) in favorites } }
    val series = remember(catalog, favorites) { catalog.series.filter { FavKeys.series(it.id) in favorites } }
    if (channels.isEmpty() && movies.isEmpty() && series.isEmpty()) {
        EmptyMessage("Henüz favori yok.\nBir kanal, film ya da dizide OK tuşuna basılı tutarak ekleyebilirsiniz.")
        return
    }
    LazyColumn(Modifier.fillMaxSize()) {
        if (channels.isNotEmpty()) {
            item { ScreenTitle("Kanallar") }
            itemsIndexed(channels, key = { i, c -> "c$i:${c.id}" }) { index, ch ->
                val key = "fav-live:${ch.id}"
                TvRow(
                    text = ch.name,
                    logo = ch.logo,
                    leading = if (ch.number > 0) ch.number.toString() else null,
                    secondary = vm.epg[ch.id]?.firstOrNull { it.isNow() }?.title,
                    modifier = Modifier.restorableFocus(state, key),
                    onFocus = { vm.loadEpg(ch) },
                    onLongClick = { vm.toggleFavorite(FavKeys.live(ch.id)) },
                    onClick = { nav.play(channels.map { it.toPlayItem() }, index, key) },
                )
            }
        }
        if (movies.isNotEmpty()) {
            item { ScreenTitle("Filmler", Modifier.padding(top = 16.dp)) }
            item {
                LazyRow {
                    itemsIndexed(movies, key = { i, v -> "m$i:${v.id}" }) { _, v ->
                        val key = "fav-vod:${v.id}"
                        PosterCard(
                            v.name, v.poster,
                            modifier = Modifier.width(130.dp).restorableFocus(state, key),
                            onLongClick = { vm.toggleFavorite(FavKeys.vod(v.id)) },
                            onClick = { nav.play(listOf(v.toPlayItem()), 0, key) },
                        )
                    }
                }
            }
        }
        if (series.isNotEmpty()) {
            item { ScreenTitle("Diziler", Modifier.padding(top = 16.dp)) }
            item {
                LazyRow {
                    itemsIndexed(series, key = { i, s -> "s$i:${s.id}" }) { _, s ->
                        val key = "fav-series:${s.id}"
                        PosterCard(
                            s.name, s.poster,
                            modifier = Modifier.width(130.dp).restorableFocus(state, key),
                            onLongClick = { vm.toggleFavorite(FavKeys.series(s.id)) },
                            onClick = { nav.openSeries(s, key) },
                        )
                    }
                }
            }
        }
    }
}

private sealed interface SearchHit {
    data class Live(val channel: LiveChannel) : SearchHit
    data class Movie(val item: VodItem) : SearchHit
    data class Show(val item: SeriesItem) : SearchHit
}

@Composable
fun SearchScreen(vm: AppViewModel, catalog: Catalog, state: HomeState, nav: Navigator) {
    var results by remember { mutableStateOf<List<SearchHit>>(emptyList()) }
    val query = state.searchQuery
    LaunchedEffect(query, catalog) {
        delay(300)
        val q = query.trim().lowercase(Locale.getDefault())
        results = if (q.length < 2) emptyList() else withContext(Dispatchers.Default) {
            val out = ArrayList<SearchHit>()
            catalog.live.forEach { if (q in it.name.lowercase(Locale.getDefault())) out += SearchHit.Live(it) }
            catalog.vod.forEach { if (q in it.name.lowercase(Locale.getDefault())) out += SearchHit.Movie(it) }
            catalog.series.forEach { if (q in it.name.lowercase(Locale.getDefault())) out += SearchHit.Show(it) }
            out.take(300)
        }
    }
    Column(Modifier.fillMaxSize()) {
        TvTextField(
            value = query,
            onValueChange = { state.searchQuery = it },
            label = "Kanal, film veya dizi ara",
            placeholder = "En az 2 harf",
            keyboardType = KeyboardType.Text,
            imeAction = ImeAction.Search,
        )
        Spacer(Modifier.height(8.dp))
        if (query.trim().length >= 2 && results.isEmpty()) {
            EmptyMessage("Sonuç yok.")
        }
        LazyColumn(Modifier.fillMaxSize()) {
            itemsIndexed(results, key = { i, _ -> i }) { _, hit ->
                when (hit) {
                    is SearchHit.Live -> {
                        val key = "search-live:${hit.channel.id}"
                        TvRow(
                            text = hit.channel.name,
                            secondary = "Canlı TV",
                            logo = hit.channel.logo,
                            modifier = Modifier.restorableFocus(state, key),
                            onLongClick = { vm.toggleFavorite(FavKeys.live(hit.channel.id)) },
                            favorite = FavKeys.live(hit.channel.id) in vm.favorites,
                            onClick = { nav.play(listOf(hit.channel.toPlayItem()), 0, key) },
                        )
                    }
                    is SearchHit.Movie -> {
                        val key = "search-vod:${hit.item.id}"
                        TvRow(
                            text = hit.item.name,
                            secondary = "Film",
                            modifier = Modifier.restorableFocus(state, key),
                            onLongClick = { vm.toggleFavorite(FavKeys.vod(hit.item.id)) },
                            favorite = FavKeys.vod(hit.item.id) in vm.favorites,
                            onClick = { nav.play(listOf(hit.item.toPlayItem()), 0, key) },
                        )
                    }
                    is SearchHit.Show -> {
                        val key = "search-series:${hit.item.id}"
                        TvRow(
                            text = hit.item.name,
                            secondary = "Dizi",
                            modifier = Modifier.restorableFocus(state, key),
                            onLongClick = { vm.toggleFavorite(FavKeys.series(hit.item.id)) },
                            favorite = FavKeys.series(hit.item.id) in vm.favorites,
                            onClick = { nav.openSeries(hit.item, key) },
                        )
                    }
                }
            }
        }
    }
}

private val userAgents = listOf(
    "VLC (önerilen)" to DEFAULT_USER_AGENT,
    "IPTV Smarters" to "IPTVSmartersPlayer",
    "TiviMate" to "TiviMate/4.7.0 (Android TV)",
    "Tarayıcı" to "Mozilla/5.0 (Linux; Android 10) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0 Safari/537.36",
)

@Composable
fun SettingsScreen(vm: AppViewModel) {
    val profile = vm.profile
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
    ) {
        ScreenTitle("Ayarlar")
        Text("Profil", fontSize = 16.sp, color = Palette.textDim)
        Text(
            when (profile) {
                is XtreamProfile -> "${profile.name}  ·  ${profile.server}  ·  ${profile.username}"
                is M3uProfile -> "${profile.name}  ·  M3U"
                null -> "-"
            },
            fontSize = 16.sp,
            color = Palette.text,
            modifier = Modifier.padding(bottom = 10.dp),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Button(onClick = { vm.reload() }) { Text("Listeyi yenile") }
            Button(onClick = { vm.logout() }) { Text("Profil değiştir / çıkış") }
        }

        Spacer(Modifier.height(24.dp))
        Text("Canlı yayın formatı (Xtream)", fontSize = 16.sp, color = Palette.textDim)
        Text(
            "Kanallar açılmıyor veya donuyorsa diğer formatı deneyin.",
            fontSize = 13.sp,
            color = Palette.textDim,
            modifier = Modifier.padding(bottom = 6.dp),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            LiveFormat.entries.forEach { f ->
                Button(onClick = { vm.updateLiveFormat(f) }) {
                    Text(if (vm.liveFormat == f) "✓ ${f.label}" else f.label)
                }
            }
        }

        Spacer(Modifier.height(24.dp))
        Text("Kullanıcı aracısı (User-Agent)", fontSize = 16.sp, color = Palette.textDim)
        Text(
            "Bazı sağlayıcılar sadece belirli uygulamalara izin verir.",
            fontSize = 13.sp,
            color = Palette.textDim,
            modifier = Modifier.padding(bottom = 6.dp),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            userAgents.forEach { (label, ua) ->
                Button(onClick = { vm.updateUserAgent(ua) }) {
                    Text(if (vm.userAgent == ua) "✓ $label" else label)
                }
            }
        }

        Spacer(Modifier.height(24.dp))
        Text(
            "Sürüm ${BuildConfig.VERSION_NAME}",
            fontSize = 13.sp,
            color = Palette.textDim,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}
