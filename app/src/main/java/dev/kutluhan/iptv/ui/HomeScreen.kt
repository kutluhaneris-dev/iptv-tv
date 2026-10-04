package dev.kutluhan.iptv.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Text
import dev.kutluhan.iptv.data.Catalog
import dev.kutluhan.iptv.data.PlayItem
import dev.kutluhan.iptv.data.SeriesItem
import kotlinx.coroutines.delay

enum class Section(val label: String) {
    LIVE("Canlı TV"),
    VOD("Filmler"),
    SERIES("Diziler"),
    FAVORITES("Favoriler"),
    SEARCH("Ara"),
    SETTINGS("Ayarlar"),
}

const val ALL_CATEGORIES = "__all__"
const val FAVORITE_CATEGORY = "__fav__"

/** UI state that must survive opening the player or a series page on top of the home screen. */
@Stable
class HomeState {
    var section by mutableStateOf(Section.LIVE)
    var liveCategory by mutableStateOf(ALL_CATEGORIES)
    var vodCategory by mutableStateOf(ALL_CATEGORIES)
    var seriesCategory by mutableStateOf(ALL_CATEGORIES)
    var searchQuery by mutableStateOf("")

    /** Key of the item that opened the screen on top, so focus can return to it. */
    var restoreKey by mutableStateOf<String?>(null)
    var restoreTick by mutableIntStateOf(0)

    /**
     * True right after a screen on top closes. Compose then hands focus to the first focusable
     * item (the "Canlı TV" menu entry), which must not switch the section away from where the
     * user was.
     */
    var returning = false
}

/** Lets the item with [key] take focus back when the screen above it closes. */
@Composable
fun Modifier.restorableFocus(state: HomeState, key: String): Modifier {
    if (state.restoreKey != key) return this
    val requester = remember { FocusRequester() }
    LaunchedEffect(state.restoreTick) {
        if (state.restoreTick > 0) {
            delay(60)
            runCatching { requester.requestFocus() }
        }
    }
    return this.focusRequester(requester)
}

class Navigator(
    val play: (items: List<PlayItem>, index: Int, restoreKey: String) -> Unit,
    val openSeries: (SeriesItem, restoreKey: String) -> Unit,
)

@Composable
fun HomeScreen(vm: AppViewModel, catalog: Catalog, state: HomeState, nav: Navigator) {
    val firstItem = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        if (state.restoreKey == null) {
            delay(100)
            runCatching { firstItem.requestFocus() }
        }
    }
    LaunchedEffect(state.restoreTick) {
        if (state.restoreTick > 0) {
            delay(500)
            state.returning = false
        }
    }
    Row(
        Modifier
            .fillMaxSize()
            .background(Palette.background),
    ) {
        Column(
            Modifier
                .width(180.dp)
                .fillMaxHeight()
                .background(Palette.panel)
                .padding(horizontal = 10.dp, vertical = 20.dp)
                .focusRestorer(),
        ) {
            Text(
                vm.profile?.name ?: "IPTV",
                fontSize = 15.sp,
                color = Palette.textDim,
                maxLines = 1,
                modifier = Modifier.padding(start = 12.dp, bottom = 16.dp),
            )
            Section.entries.forEachIndexed { i, s ->
                if (s == Section.SETTINGS) Spacer(Modifier.height(24.dp))
                TvRow(
                    text = s.label,
                    selected = state.section == s,
                    modifier = if (i == 0) Modifier.focusRequester(firstItem) else Modifier,
                    onFocus = { if (!state.returning) state.section = s },
                    onClick = { state.section = s },
                )
            }
        }
        Box(
            Modifier
                .weight(1f)
                .fillMaxHeight()
                .padding(start = 16.dp, top = 20.dp, end = 20.dp, bottom = 12.dp),
        ) {
            when (state.section) {
                Section.LIVE -> LiveScreen(vm, catalog, state, nav)
                Section.VOD -> VodScreen(vm, catalog, state, nav)
                Section.SERIES -> SeriesScreen(vm, catalog, state, nav)
                Section.FAVORITES -> FavoritesScreen(vm, catalog, state, nav)
                Section.SEARCH -> SearchScreen(vm, catalog, state, nav)
                Section.SETTINGS -> SettingsScreen(vm)
            }
        }
    }
}
