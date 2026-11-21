package dev.kutluhan.iptv.desktop

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import dev.kutluhan.iptv.data.PlayItem
import dev.kutluhan.iptv.data.SeriesItem

private sealed interface Route {
    class Player(val items: List<PlayItem>, val index: Int) : Route
    class Series(val series: SeriesItem) : Route
}

class Navigator(
    val play: (items: List<PlayItem>, index: Int) -> Unit,
    val openSeries: (SeriesItem) -> Unit,
)

/** False while another screen covers this one, e.g. so the channel preview leaves the player alone. */
val LocalLayerActive = compositionLocalOf { true }

@Composable
fun App(app: AppState, player: VideoPlayer?) {
    IptvTheme {
        val catalog = app.catalog
        if (catalog == null) {
            LoginScreen(app)
            return@IptvTheme
        }
        val home = remember(catalog) { HomeState() }
        val stack = remember(catalog) { mutableStateListOf<Route>() }
        val nav = remember(catalog) {
            Navigator(
                play = { items, index -> stack.add(Route.Player(items, index)) },
                openSeries = { stack.add(Route.Series(it)) },
            )
        }
        fun pop() {
            if (stack.isNotEmpty()) stack.removeAt(stack.lastIndex)
        }

        Box(Modifier.fillMaxSize().background(Palette.background)) {
            // Lower screens stay composed so lists keep their scroll position.
            CompositionLocalProvider(LocalLayerActive provides stack.isEmpty()) {
                HomeScreen(app, catalog, home, nav, player)
            }
            stack.forEachIndexed { i, route ->
                key(route) {
                    CompositionLocalProvider(LocalLayerActive provides (i == stack.lastIndex)) {
                        when (route) {
                            is Route.Player -> PlayerScreen(
                                app = app,
                                player = player,
                                items = route.items,
                                startIndex = route.index,
                                onIndexChange = { item ->
                                    item.channel?.let {
                                        app.storage.lastLiveChannel = it.id
                                        home.liveSelected = it.id
                                    }
                                },
                                onClose = { keepPlaying ->
                                    if (!keepPlaying || !app.livePreview || home.section != Section.LIVE) player?.stop()
                                    pop()
                                },
                            )
                            is Route.Series -> SeriesDetailScreen(app, route.series, nav, onBack = { pop() })
                        }
                    }
                }
            }
        }
    }
}
