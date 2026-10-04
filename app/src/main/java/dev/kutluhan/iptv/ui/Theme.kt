package dev.kutluhan.iptv.ui

import androidx.compose.foundation.gestures.BringIntoViewSpec
import androidx.compose.foundation.gestures.LocalBringIntoViewSpec
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
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
    ) {
        val margin = with(LocalDensity.current) { 56.dp.toPx() }
        val scrollSpec = remember(margin) { MinimalScrollSpec(margin) }
        CompositionLocalProvider(LocalBringIntoViewSpec provides scrollSpec, content = content)
    }
}

/**
 * Compose on TV keeps the focused item pinned near the top of a list, so the whole list jumps on
 * every D-pad press. Instead, let focus walk down the visible rows and scroll only when it gets
 * within [margin] of an edge, like classic TV channel lists.
 */
private class MinimalScrollSpec(private val margin: Float) : BringIntoViewSpec {
    override fun calculateScrollDistance(offset: Float, size: Float, containerSize: Float): Float {
        val edge = margin.coerceAtMost((containerSize - size) / 2).coerceAtLeast(0f)
        val trailing = offset + size
        return when {
            offset < edge -> offset - edge
            trailing > containerSize - edge -> trailing - (containerSize - edge)
            else -> 0f
        }
    }
}
