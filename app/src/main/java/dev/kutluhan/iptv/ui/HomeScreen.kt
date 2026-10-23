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
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.Alignment
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Surface
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
    // The sections sit in a strip along the top, so the lists and the preview get the full width.
    val selectedTab = remember { FocusRequester() }
    Column(
        Modifier
            .fillMaxSize()
            .background(Palette.background),
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .background(Palette.panel)
                .padding(horizontal = 20.dp, vertical = 8.dp)
                // Coming up from the content lands on the current section, not the one above it.
                .focusProperties { enter = { selectedTab } }
                .focusGroup(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(
                vm.profile?.name ?: "IPTV",
                fontSize = 13.sp,
                color = Palette.textDim,
                maxLines = 1,
                modifier = Modifier.padding(end = 14.dp),
            )
            Section.entries.forEachIndexed { i, s ->
                if (s == Section.SETTINGS) Spacer(Modifier.weight(1f))
                var mod: Modifier = Modifier
                if (state.section == s) mod = mod.focusRequester(selectedTab)
                if (i == 0) mod = mod.focusRequester(firstItem)
                SectionTab(
                    label = s.label,
                    selected = state.section == s,
                    modifier = mod,
                    onFocus = { if (!state.returning) state.section = s },
                    onClick = { state.section = s },
                )
            }
        }
        Box(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(start = 20.dp, top = 12.dp, end = 20.dp, bottom = 10.dp),
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

@Composable
private fun SectionTab(
    label: String,
    selected: Boolean,
    modifier: Modifier,
    onFocus: () -> Unit,
    onClick: () -> Unit,
) {
    Surface(
        onClick = onClick,
        modifier = modifier.onFocusChanged { if (it.isFocused) onFocus() },
        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(50)),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.05f),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = if (selected) Palette.panelAlt else Color.Transparent,
            contentColor = if (selected) Palette.accent else Palette.text,
            focusedContainerColor = Palette.focus,
            focusedContentColor = Palette.onFocus,
        ),
    ) {
        Text(label, fontSize = 14.sp, modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp))
    }
}
