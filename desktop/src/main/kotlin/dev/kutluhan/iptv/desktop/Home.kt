package dev.kutluhan.iptv.desktop

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.kutluhan.iptv.data.Catalog

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
const val RECENT_CATEGORY = "__new__"

/** How many items "Yeni eklenenler" shows, newest first. */
const val RECENT_LIMIT = 200

/** UI state that survives opening the player or a series page on top of the home screen. */
@Stable
class HomeState {
    var section by mutableStateOf(Section.LIVE)
    var liveCategory by mutableStateOf(ALL_CATEGORIES)
    var liveSelected by mutableStateOf<String?>(null)
    var liveFilter by mutableStateOf("")
    var vodCategory by mutableStateOf(ALL_CATEGORIES)
    var seriesCategory by mutableStateOf(ALL_CATEGORIES)
    var searchQuery by mutableStateOf("")
}

@Composable
fun HomeScreen(app: AppState, catalog: Catalog, state: HomeState, nav: Navigator, player: VideoPlayer?) {
    Column(Modifier.fillMaxSize().background(Palette.background)) {
        Row(
            Modifier
                .fillMaxWidth()
                .background(Palette.panel)
                .padding(horizontal = 20.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(
                app.profile?.name ?: "IPTV",
                fontSize = 13.sp,
                color = Palette.textDim,
                maxLines = 1,
                modifier = Modifier.padding(end = 14.dp),
            )
            Section.entries.forEach { s ->
                if (s == Section.SETTINGS) Spacer(Modifier.weight(1f))
                SectionTab(s.label, state.section == s) {
                    if (s != Section.LIVE && state.section == Section.LIVE) player?.stop()
                    state.section = s
                }
            }
        }
        Box(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(start = 20.dp, top = 14.dp, end = 20.dp, bottom = 12.dp),
        ) {
            when (state.section) {
                Section.LIVE -> LiveScreen(app, catalog, state, nav, player)
                Section.VOD -> VodScreen(app, catalog, state, nav)
                Section.SERIES -> SeriesScreen(app, catalog, state, nav)
                Section.FAVORITES -> FavoritesScreen(app, catalog, nav)
                Section.SEARCH -> SearchScreen(app, catalog, state, nav)
                Section.SETTINGS -> SettingsScreen(app)
            }
        }
    }
}

@Composable
private fun SectionTab(label: String, selected: Boolean, onClick: () -> Unit) {
    val source = remember { MutableInteractionSource() }
    val hovered by source.collectIsHoveredAsState()
    Box(
        Modifier
            .clip(RoundedCornerShape(50))
            .background(
                when {
                    selected -> Palette.panelAlt
                    hovered -> Palette.hover
                    else -> Color.Transparent
                },
            )
            .hoverable(source)
            .clickable(interactionSource = source, indication = null, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 7.dp),
    ) {
        Text(
            label,
            fontSize = 14.sp,
            color = if (selected) Palette.accent else Palette.text,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
        )
    }
}
