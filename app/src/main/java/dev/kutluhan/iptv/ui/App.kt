package dev.kutluhan.iptv.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import dev.kutluhan.iptv.data.PlayItem
import dev.kutluhan.iptv.data.SeriesItem

private sealed interface Route {
    class Player(val items: List<PlayItem>, val index: Int) : Route
    class Series(val series: SeriesItem, val state: HomeState = HomeState()) : Route
}

@Composable
fun App(vm: AppViewModel) {
    IptvTheme {
        val catalog = vm.catalog
        if (catalog == null) {
            LoginScreen(vm)
            return@IptvTheme
        }
        val home = remember(catalog) { HomeState() }
        val stack = remember(catalog) { mutableStateListOf<Route>() }

        fun navigatorFor(state: HomeState) = Navigator(
            play = { items, index, restoreKey ->
                state.restoreKey = restoreKey
                stack.add(Route.Player(items, index))
            },
            openSeries = { series, restoreKey ->
                state.restoreKey = restoreKey
                stack.add(Route.Series(series))
            },
        )

        fun pop() {
            if (stack.isEmpty()) return
            // removeAt instead of removeLast: the latter is a Java 21 API missing on older TVs.
            stack.removeAt(stack.lastIndex)
            val below = (stack.lastOrNull() as? Route.Series)?.state ?: home
            below.restoreTick++
        }

        BackHandler(enabled = stack.isNotEmpty()) { pop() }

        Box(Modifier.fillMaxSize().background(Palette.background)) {
            // Lower layers stay composed so their scroll position survives, but focus can't wander into them.
            Layer(active = stack.isEmpty()) {
                HomeScreen(vm, catalog, home, navigatorFor(home))
            }
            stack.forEachIndexed { i, route ->
                key(route) {
                    Layer(active = i == stack.lastIndex) {
                        when (route) {
                            is Route.Player -> PlayerScreen(vm, route.items, route.index) { idx ->
                                route.items.getOrNull(idx)?.channel?.let { vm.storage.lastLiveChannel = it.id }
                            }
                            is Route.Series -> SeriesDetailScreen(vm, route.series, route.state, navigatorFor(route.state))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun Layer(active: Boolean, content: @Composable () -> Unit) {
    Box(
        Modifier
            .fillMaxSize()
            .focusProperties {
                enter = { if (active) FocusRequester.Default else FocusRequester.Cancel }
                exit = { if (active) FocusRequester.Cancel else FocusRequester.Default }
            }
            .focusGroup(),
    ) {
        content()
    }
}
