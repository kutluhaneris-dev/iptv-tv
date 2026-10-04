package dev.kutluhan.iptv.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.darkColorScheme

object Palette {
    val background = Color(0xFF0B0F14)
    val panel = Color(0xFF141A22)
    val panelAlt = Color(0xFF1B232E)
    val accent = Color(0xFF2E8BFF)
    val text = Color(0xFFE8EDF2)
    val textDim = Color(0xFF93A1B0)
    val focus = Color(0xFFFFFFFF)
    val onFocus = Color(0xFF0B0F14)
    val star = Color(0xFFFFC107)
    val error = Color(0xFFFF6B6B)
}

@Composable
fun IptvTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = Palette.accent,
            background = Palette.background,
            surface = Palette.panel,
            onSurface = Palette.text,
            onBackground = Palette.text,
        ),
        content = content,
    )
}
