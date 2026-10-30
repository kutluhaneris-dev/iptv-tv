package dev.kutluhan.iptv.desktop

import androidx.compose.foundation.ContextMenuArea
import androidx.compose.foundation.ContextMenuItem
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.VerticalScrollbar
import androidx.compose.foundation.rememberScrollbarAdapter
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.StarOutline
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import dev.kutluhan.iptv.data.EpgProgram
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())

fun EpgProgram.timeRange(): String = "${timeFormat.format(Date(start))} - ${timeFormat.format(Date(end))}"

fun favoriteMenu(isFavorite: Boolean, toggle: () -> Unit): List<ContextMenuItem> =
    listOf(ContextMenuItem(if (isFavorite) "Favorilerden çıkar" else "Favorilere ekle", toggle))

@Composable
fun FavoriteStar(isFavorite: Boolean, visible: Boolean, onToggle: () -> Unit, modifier: Modifier = Modifier) {
    if (!isFavorite && !visible) {
        Box(modifier.size(28.dp))
        return
    }
    val source = remember { MutableInteractionSource() }
    val hovered by source.collectIsHoveredAsState()
    Box(
        modifier
            .size(28.dp)
            .clip(RoundedCornerShape(50))
            .hoverable(source)
            .combinedClickable(interactionSource = source, indication = null, onClick = onToggle)
            .background(if (hovered) Color.White.copy(alpha = 0.12f) else Color.Transparent),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            if (isFavorite) Icons.Filled.Star else Icons.Outlined.StarOutline,
            contentDescription = if (isFavorite) "Favorilerden çıkar" else "Favorilere ekle",
            tint = if (isFavorite) Palette.star else Palette.textDim,
            modifier = Modifier.size(18.dp),
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ListRow(
    text: String,
    modifier: Modifier = Modifier,
    secondary: String? = null,
    logo: String? = null,
    leading: String? = null,
    selected: Boolean = false,
    favorite: Boolean? = null,
    onToggleFavorite: (() -> Unit)? = null,
    onDoubleClick: (() -> Unit)? = null,
    onClick: () -> Unit,
) {
    val source = remember { MutableInteractionSource() }
    val hovered by source.collectIsHoveredAsState()
    val content = @Composable {
        Row(
            modifier
                .fillMaxWidth()
                .height(if (secondary != null || logo != null) 52.dp else 40.dp)
                .padding(vertical = 1.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(
                    when {
                        selected -> Palette.accent.copy(alpha = 0.28f)
                        hovered -> Palette.hover
                        else -> Color.Transparent
                    },
                )
                .hoverable(source)
                .combinedClickable(
                    interactionSource = source,
                    indication = null,
                    onDoubleClick = onDoubleClick,
                    onClick = onClick,
                )
                .padding(horizontal = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            if (leading != null) {
                Text(leading, color = Palette.textDim, fontSize = 12.sp, modifier = Modifier.width(34.dp), textAlign = TextAlign.End)
            }
            if (logo != null) {
                AsyncImage(
                    model = logo,
                    contentDescription = null,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.size(width = 52.dp, height = 36.dp),
                )
            }
            Column(Modifier.weight(1f)) {
                Text(
                    text,
                    color = if (selected) Color.White else Palette.text,
                    fontSize = 14.sp,
                    fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (secondary != null) {
                    Text(secondary, color = Palette.textDim, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            if (favorite != null && onToggleFavorite != null) {
                FavoriteStar(favorite, hovered || selected, onToggleFavorite)
            }
        }
    }
    if (favorite != null && onToggleFavorite != null) {
        ContextMenuArea(items = { favoriteMenu(favorite, onToggleFavorite) }) { content() }
    } else {
        content()
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun PosterCard(
    title: String,
    image: String?,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    favorite: Boolean = false,
    onToggleFavorite: () -> Unit,
    onClick: () -> Unit,
) {
    val source = remember { MutableInteractionSource() }
    val hovered by source.collectIsHoveredAsState()
    ContextMenuArea(items = { favoriteMenu(favorite, onToggleFavorite) }) {
        Column(
            modifier
                .padding(6.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(if (hovered) Palette.hover else Palette.panel)
                .hoverable(source)
                .combinedClickable(interactionSource = source, indication = null, onClick = onClick),
        ) {
            Box(Modifier.fillMaxWidth().aspectRatio(2f / 3f).background(Palette.panelAlt)) {
                if (image != null) {
                    AsyncImage(
                        model = image,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                    )
                } else {
                    Text(
                        title,
                        color = Palette.textDim,
                        fontSize = 13.sp,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.align(Alignment.Center).padding(8.dp),
                    )
                }
                FavoriteStar(
                    favorite,
                    hovered,
                    onToggleFavorite,
                    Modifier
                        .align(Alignment.TopEnd)
                        .padding(4.dp)
                        .clip(RoundedCornerShape(50))
                        .background(Color.Black.copy(alpha = 0.45f)),
                )
            }
            Column(Modifier.padding(horizontal = 8.dp, vertical = 6.dp)) {
                Text(title, color = Palette.text, fontSize = 13.sp, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.height(34.dp))
                Text(subtitle ?: "", color = Palette.star, fontSize = 11.sp, maxLines = 1)
            }
        }
    }
}

data class CategoryRow(val id: String, val name: String, val count: Int)

@Composable
fun CategoryList(rows: List<CategoryRow>, selected: String, unit: String, onSelect: (String) -> Unit, modifier: Modifier = Modifier) {
    val state = rememberLazyListState()
    Box(modifier.width(230.dp).fillMaxHeight()) {
        LazyColumn(Modifier.fillMaxSize().padding(end = 8.dp), state = state) {
            itemsIndexed(rows, key = { i, r -> "$i:${r.id}" }) { _, r ->
                ListRow(
                    text = r.name,
                    secondary = if (r.count >= 0) "${r.count} $unit" else null,
                    selected = r.id == selected,
                    onClick = { onSelect(r.id) },
                )
            }
        }
        Scrollbar(state, Modifier.align(Alignment.CenterEnd))
    }
}

@Composable
fun Scrollbar(state: LazyListState, modifier: Modifier = Modifier) {
    VerticalScrollbar(rememberScrollbarAdapter(state), modifier.fillMaxHeight())
}

@Composable
fun SectionTitle(text: String, modifier: Modifier = Modifier) {
    Text(text, color = Palette.text, fontSize = 18.sp, fontWeight = FontWeight.SemiBold, modifier = modifier.padding(vertical = 8.dp))
}

@Composable
fun EmptyMessage(text: String, modifier: Modifier = Modifier) {
    Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(text, color = Palette.textDim, fontSize = 15.sp, textAlign = TextAlign.Center)
    }
}

@Composable
fun ProgressLine(progress: Float, modifier: Modifier = Modifier) {
    Box(modifier.fillMaxWidth().height(3.dp).clip(RoundedCornerShape(2.dp)).background(Color.White.copy(alpha = 0.15f))) {
        Box(Modifier.fillMaxWidth(progress.coerceIn(0f, 1f)).fillMaxHeight().background(Palette.accent))
    }
}

@Composable
fun ProgramList(programs: List<EpgProgram>, modifier: Modifier = Modifier) {
    val now = System.currentTimeMillis()
    LazyColumn(modifier) {
        itemsIndexed(programs.take(12), key = { i, p -> "$i:${p.start}" }) { _, p ->
            val live = p.isNow(now)
            Column(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(p.timeRange(), color = if (live) Palette.accent else Palette.textDim, fontSize = 13.sp)
                    Text(
                        p.title,
                        color = Palette.text,
                        fontSize = 14.sp,
                        fontWeight = if (live) FontWeight.SemiBold else FontWeight.Normal,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                if (live) {
                    ProgressLine((now - p.start).toFloat() / (p.end - p.start).coerceAtLeast(1), Modifier.padding(top = 4.dp))
                    p.description?.let {
                        Text(it, color = Palette.textDim, fontSize = 12.sp, maxLines = 3, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 4.dp))
                    }
                }
            }
        }
    }
}

/** Darkens the edge of the video so overlay text stays readable. */
val topShade = Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.75f), Color.Transparent))
val bottomShade = Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.85f)))

/** For screens drawn over others: stops clicks and hovers from reaching what is underneath. */
fun Modifier.blockPointer(): Modifier = this.pointerInput(Unit) {
    awaitPointerEventScope {
        while (true) awaitPointerEvent()
    }
}
