package dev.kutluhan.iptv.desktop

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowPosition
import androidx.compose.ui.window.WindowState
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState

val LocalWindowState = staticCompositionLocalOf<WindowState> { error("no window") }

@Suppress("DEPRECATION")
fun main() = application {
    val windowState = rememberWindowState(size = DpSize(1360.dp, 840.dp), position = WindowPosition(Alignment.Center))
    val app = remember { AppState() }
    val player = remember { Vlc.factory?.let { VideoPlayer(it, app.storage.volume) } }
    Window(
        onCloseRequest = {
            player?.let { app.storage.volume = it.volume }
            player?.release()
            exitApplication()
        },
        state = windowState,
        title = "IPTV",
        icon = painterResource("icon.png"),
    ) {
        window.minimumSize = java.awt.Dimension(960, 600)
        CompositionLocalProvider(LocalWindowState provides windowState) {
            App(app, player)
        }
    }
}
