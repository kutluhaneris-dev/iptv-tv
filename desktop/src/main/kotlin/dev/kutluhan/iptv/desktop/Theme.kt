package dev.kutluhan.iptv.desktop

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

object Palette {
    val background = Color(0xFF0B0F14)
    val panel = Color(0xFF141A22)
    val panelAlt = Color(0xFF1B232E)
    val hover = Color(0xFF232D3A)
    val accent = Color(0xFF2E8BFF)
    val text = Color(0xFFE8EDF2)
    val textDim = Color(0xFF93A1B0)
    val star = Color(0xFFFFC107)
    val error = Color(0xFFFF6B6B)
}

@Composable
fun IptvTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = Palette.accent,
            onPrimary = Color.White,
            background = Palette.background,
            surface = Palette.panel,
            surfaceVariant = Palette.panelAlt,
            onSurface = Palette.text,
            onSurfaceVariant = Palette.textDim,
            onBackground = Palette.text,
            error = Palette.error,
        ),
        content = content,
    )
}
