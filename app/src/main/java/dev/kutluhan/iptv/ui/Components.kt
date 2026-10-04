package dev.kutluhan.iptv.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Border
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Icon
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import coil.compose.AsyncImage

private val rowShape = RoundedCornerShape(8.dp)

/** A focusable list row. Selected rows stay highlighted when focus moves away. */
@Composable
fun TvRow(
    text: String,
    modifier: Modifier = Modifier,
    selected: Boolean = false,
    secondary: String? = null,
    logo: String? = null,
    favorite: Boolean = false,
    leading: String? = null,
    onFocus: (() -> Unit)? = null,
    onLongClick: (() -> Unit)? = null,
    onClick: () -> Unit,
) {
    var focused by remember { mutableStateOf(false) }
    Surface(
        onClick = onClick,
        onLongClick = onLongClick,
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp)
            .onFocusChanged {
                focused = it.isFocused
                if (it.isFocused) onFocus?.invoke()
            },
        shape = ClickableSurfaceDefaults.shape(rowShape),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.02f),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = if (selected) Palette.panelAlt else Color.Transparent,
            contentColor = if (selected) Palette.accent else Palette.text,
            focusedContainerColor = Palette.focus,
            focusedContentColor = Palette.onFocus,
        ),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (leading != null) {
                Text(
                    leading,
                    modifier = Modifier.width(40.dp),
                    fontSize = 13.sp,
                    color = if (focused) Palette.onFocus else Palette.textDim,
                )
            }
            if (logo != null) {
                AsyncImage(
                    model = logo,
                    contentDescription = null,
                    modifier = Modifier
                        .size(width = 48.dp, height = 30.dp)
                        .clip(RoundedCornerShape(4.dp)),
                    contentScale = ContentScale.Fit,
                )
                Spacer(Modifier.width(10.dp))
            }
            Column(Modifier.weight(1f)) {
                Text(text, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (secondary != null) {
                    Text(
                        secondary,
                        fontSize = 12.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        color = if (focused) Palette.onFocus.copy(alpha = 0.7f) else Palette.textDim,
                    )
                }
            }
            if (favorite) {
                Icon(
                    Icons.Filled.Favorite,
                    contentDescription = "Favori",
                    tint = Palette.star,
                    modifier = Modifier.size(16.dp),
                )
            }
        }
    }
}

@Composable
fun PosterCard(
    title: String,
    image: String?,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    favorite: Boolean = false,
    onLongClick: (() -> Unit)? = null,
    onClick: () -> Unit,
) {
    Column(modifier.padding(6.dp)) {
        Surface(
            onClick = onClick,
            onLongClick = onLongClick,
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(2f / 3f),
            shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(10.dp)),
            scale = ClickableSurfaceDefaults.scale(focusedScale = 1.06f),
            border = ClickableSurfaceDefaults.border(
                focusedBorder = Border(BorderStroke(3.dp, Palette.focus), shape = RoundedCornerShape(10.dp)),
            ),
            colors = ClickableSurfaceDefaults.colors(
                containerColor = Palette.panelAlt,
                focusedContainerColor = Palette.panelAlt,
            ),
        ) {
            Box(Modifier.fillMaxSize()) {
                Text(
                    title,
                    modifier = Modifier
                        .align(Alignment.Center)
                        .padding(8.dp),
                    fontSize = 13.sp,
                    color = Palette.textDim,
                    maxLines = 4,
                )
                if (image != null) {
                    AsyncImage(
                        model = image,
                        contentDescription = title,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop,
                    )
                }
                if (favorite) {
                    Icon(
                        Icons.Filled.Favorite,
                        contentDescription = "Favori",
                        tint = Palette.star,
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(6.dp)
                            .size(18.dp),
                    )
                }
            }
        }
        Text(
            title,
            modifier = Modifier.padding(top = 6.dp),
            fontSize = 13.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            color = Palette.text,
        )
        if (subtitle != null) {
            Text(subtitle, fontSize = 11.sp, color = Palette.textDim, maxLines = 1)
        }
    }
}

@Composable
fun TvTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    placeholder: String = "",
    password: Boolean = false,
    keyboardType: KeyboardType = KeyboardType.Uri,
    imeAction: ImeAction = ImeAction.Next,
    onDone: (() -> Unit)? = null,
) {
    val focusManager = LocalFocusManager.current
    var focused by remember { mutableStateOf(false) }
    Column(modifier.padding(vertical = 6.dp)) {
        Text(label, fontSize = 13.sp, color = Palette.textDim)
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            singleLine = true,
            textStyle = TextStyle(color = Palette.text, fontSize = 17.sp),
            cursorBrush = SolidColor(Palette.accent),
            visualTransformation = if (password) PasswordVisualTransformation() else VisualTransformation.None,
            keyboardOptions = KeyboardOptions(
                keyboardType = if (password) KeyboardType.Password else keyboardType,
                imeAction = imeAction,
                autoCorrectEnabled = false,
            ),
            keyboardActions = KeyboardActions(
                onNext = { focusManager.moveFocus(FocusDirection.Down) },
                onDone = { onDone?.invoke() ?: focusManager.moveFocus(FocusDirection.Down) },
                onSearch = { onDone?.invoke() },
            ),
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 4.dp)
                .onFocusChanged { focused = it.isFocused }
                .clip(rowShape)
                .background(Palette.panelAlt)
                .border(2.dp, if (focused) Palette.accent else Color.Transparent, rowShape)
                .padding(horizontal = 14.dp, vertical = 12.dp),
            decorationBox = { inner ->
                Box {
                    if (value.isEmpty() && placeholder.isNotEmpty()) {
                        Text(placeholder, color = Palette.textDim.copy(alpha = 0.6f), fontSize = 17.sp)
                    }
                    inner()
                }
            },
        )
    }
}

@Composable
fun ScreenTitle(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        modifier = modifier.padding(bottom = 12.dp),
        fontSize = 22.sp,
        fontWeight = FontWeight.SemiBold,
        color = Palette.text,
    )
}

@Composable
fun EmptyMessage(text: String, modifier: Modifier = Modifier) {
    Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(text, color = Palette.textDim, fontSize = 16.sp)
    }
}

@Composable
fun HintBar(text: String, modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.Start) {
        Text(text, color = Palette.textDim, fontSize = 12.sp)
    }
}
