package dev.kutluhan.iptv.desktop

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
fun FavoritesScreen(app: AppState, catalog: Catalog, nav: Navigator) {
    val favorites = app.favorites
    val channels = remember(catalog, favorites) { catalog.live.filter { FavKeys.live(it.id) in favorites } }
    val movies = remember(catalog, favorites) { catalog.vod.filter { FavKeys.vod(it.id) in favorites } }
    val series = remember(catalog, favorites) { catalog.series.filter { FavKeys.series(it.id) in favorites } }
    if (channels.isEmpty() && movies.isEmpty() && series.isEmpty()) {
        EmptyMessage("Henüz favori yok.\nBir kanal, film ya da dizideki ☆ işaretine tıklayarak veya sağ tıklayarak ekleyebilirsiniz.")
        return
    }
    val listState = rememberLazyListState()
    Box(Modifier.fillMaxSize()) {
        LazyColumn(Modifier.fillMaxSize().padding(end = 10.dp), state = listState) {
            if (channels.isNotEmpty()) {
                item { SectionTitle("Kanallar") }
                itemsIndexed(channels, key = { i, c -> "c$i:${c.id}" }) { index, ch ->
                    ListRow(
                        text = ch.name,
                        logo = ch.logo,
                        leading = if (ch.number > 0) ch.number.toString() else null,
                        secondary = app.epg[ch.id]?.firstOrNull { it.isNow() }?.title ?: "",
                        favorite = true,
                        onToggleFavorite = { app.toggleFavorite(FavKeys.live(ch.id)) },
                        onClick = { nav.play(channels.map { it.toPlayItem() }, index) },
                    )
                    LaunchedEffect(ch.id) { app.loadEpg(ch) }
                }
            }
            if (movies.isNotEmpty()) {
                item { SectionTitle("Filmler", Modifier.padding(top = 16.dp)) }
                item {
                    LazyRow {
                        itemsIndexed(movies, key = { i, v -> "m$i:${v.id}" }) { _, v ->
                            PosterCard(
                                v.name, v.poster,
                                modifier = Modifier.width(160.dp),
                                favorite = true,
                                onToggleFavorite = { app.toggleFavorite(FavKeys.vod(v.id)) },
                                onClick = { nav.play(listOf(v.toPlayItem()), 0) },
                            )
                        }
                    }
                }
            }
            if (series.isNotEmpty()) {
                item { SectionTitle("Diziler", Modifier.padding(top = 16.dp)) }
                item {
                    LazyRow {
                        itemsIndexed(series, key = { i, s -> "s$i:${s.id}" }) { _, s ->
                            PosterCard(
                                s.name, s.poster,
                                modifier = Modifier.width(160.dp),
                                favorite = true,
                                onToggleFavorite = { app.toggleFavorite(FavKeys.series(s.id)) },
                                onClick = { nav.openSeries(s) },
                            )
                        }
                    }
                }
            }
        }
        Scrollbar(listState, Modifier.align(Alignment.CenterEnd))
    }
}

private sealed interface SearchHit {
    data class Live(val channel: LiveChannel) : SearchHit
    data class Movie(val item: VodItem) : SearchHit
    data class Show(val item: SeriesItem) : SearchHit
}

@Composable
fun SearchScreen(app: AppState, catalog: Catalog, state: HomeState, nav: Navigator) {
    var results by remember { mutableStateOf<List<SearchHit>>(emptyList()) }
    val query = state.searchQuery
    LaunchedEffect(query, catalog) {
        delay(250)
        val q = query.trim().lowercase(Locale.getDefault())
        results = if (q.length < 2) emptyList() else withContext(Dispatchers.Default) {
            val out = ArrayList<SearchHit>()
            catalog.live.forEach { if (q in it.name.lowercase(Locale.getDefault())) out += SearchHit.Live(it) }
            catalog.vod.forEach { if (q in it.name.lowercase(Locale.getDefault())) out += SearchHit.Movie(it) }
            catalog.series.forEach { if (q in it.name.lowercase(Locale.getDefault())) out += SearchHit.Show(it) }
            out.take(300)
        }
    }
    val field = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { field.requestFocus() } }
    Column(Modifier.fillMaxSize()) {
        OutlinedTextField(
            value = query,
            onValueChange = { state.searchQuery = it },
            singleLine = true,
            label = { Text("Kanal, film veya dizi ara") },
            placeholder = { Text("En az 2 harf") },
            leadingIcon = { Icon(Icons.Filled.Search, null) },
            modifier = Modifier.width(520.dp).focusRequester(field),
        )
        Spacer(Modifier.height(10.dp))
        if (query.trim().length >= 2 && results.isEmpty()) {
            EmptyMessage("Sonuç yok.")
            return@Column
        }
        val listState = rememberLazyListState()
        Box(Modifier.fillMaxSize()) {
            LazyColumn(Modifier.fillMaxSize().padding(end = 10.dp), state = listState) {
                itemsIndexed(results, key = { i, _ -> i }) { _, hit ->
                    when (hit) {
                        is SearchHit.Live -> ListRow(
                            text = hit.channel.name,
                            secondary = "Canlı TV",
                            logo = hit.channel.logo,
                            favorite = FavKeys.live(hit.channel.id) in app.favorites,
                            onToggleFavorite = { app.toggleFavorite(FavKeys.live(hit.channel.id)) },
                            onClick = { nav.play(listOf(hit.channel.toPlayItem()), 0) },
                        )
                        is SearchHit.Movie -> ListRow(
                            text = hit.item.name,
                            secondary = "Film",
                            logo = hit.item.poster,
                            favorite = FavKeys.vod(hit.item.id) in app.favorites,
                            onToggleFavorite = { app.toggleFavorite(FavKeys.vod(hit.item.id)) },
                            onClick = { nav.play(listOf(hit.item.toPlayItem()), 0) },
                        )
                        is SearchHit.Show -> ListRow(
                            text = hit.item.name,
                            secondary = "Dizi",
                            logo = hit.item.poster,
                            favorite = FavKeys.series(hit.item.id) in app.favorites,
                            onToggleFavorite = { app.toggleFavorite(FavKeys.series(hit.item.id)) },
                            onClick = { nav.openSeries(hit.item) },
                        )
                    }
                }
            }
            Scrollbar(listState, Modifier.align(Alignment.CenterEnd))
        }
    }
}

val userAgents = listOf(
    "VLC (önerilen)" to DEFAULT_USER_AGENT,
    "IPTV Smarters" to "IPTVSmartersPlayer",
    "TiviMate" to "TiviMate/4.7.0 (Android TV)",
    "Tarayıcı" to "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0 Safari/537.36",
)

@Composable
private fun SettingTitle(title: String, hint: String? = null) {
    Text(title, fontSize = 16.sp, color = Palette.text, modifier = Modifier.padding(top = 24.dp))
    if (hint != null) Text(hint, fontSize = 13.sp, color = Palette.textDim, modifier = Modifier.padding(bottom = 8.dp))
}

@Composable
private fun Choice(label: String, selected: Boolean, onClick: () -> Unit) {
    FilterChip(selected = selected, onClick = onClick, label = { Text(label) })
}

@Composable
fun SettingsScreen(app: AppState) {
    val profile = app.profile
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        SectionTitle("Ayarlar")
        Text("Profil", fontSize = 16.sp, color = Palette.text)
        Text(
            when (profile) {
                is XtreamProfile -> "${profile.name}  ·  ${profile.server}  ·  ${profile.username}"
                is M3uProfile -> "${profile.name}  ·  M3U"
                null -> "-"
            },
            fontSize = 14.sp,
            color = Palette.textDim,
            modifier = Modifier.padding(bottom = 10.dp),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedButton(onClick = { app.reload() }) { Text("Listeyi yenile") }
            OutlinedButton(onClick = { app.logout() }) { Text("Profil değiştir / çıkış") }
        }

        SettingTitle("Canlı yayın formatı (Xtream)", "Kanallar açılmıyor veya donuyorsa diğer formatı deneyin.")
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            LiveFormat.entries.forEach { f -> Choice(f.label, app.liveFormat == f) { app.updateLiveFormat(f) } }
        }

        SettingTitle(
            "Canlı kanal önizlemesi",
            "Kanal listesinde seçili kanal sağdaki kutuda oynar. Hesabınız tek bağlantıya izin veriyorsa kapatın.",
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Choice("Açık", app.livePreview) { app.updateLivePreview(true) }
            Choice("Kapalı", !app.livePreview) { app.updateLivePreview(false) }
        }

        SettingTitle("Kullanıcı aracısı (User-Agent)", "Bazı sağlayıcılar sadece belirli uygulamalara izin verir.")
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            userAgents.forEach { (label, ua) -> Choice(label, app.userAgent == ua) { app.updateUserAgent(ua) } }
        }

        SettingTitle("Video motoru")
        if (Vlc.available) {
            Text("VLC bulundu.", fontSize = 13.sp, color = Palette.textDim)
        } else {
            VlcMissing(Modifier.fillMaxWidth())
        }

        Spacer(Modifier.height(24.dp))
        Text(
            "Sürüm ${System.getProperty("jpackage.app-version") ?: "geliştirme"}",
            fontSize = 13.sp,
            color = Palette.textDim,
        )
    }
}
