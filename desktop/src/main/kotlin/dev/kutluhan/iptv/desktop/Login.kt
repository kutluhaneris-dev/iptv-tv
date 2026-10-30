package dev.kutluhan.iptv.desktop

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.kutluhan.iptv.data.DEFAULT_USER_AGENT
import dev.kutluhan.iptv.data.M3uProfile
import dev.kutluhan.iptv.data.Profile
import dev.kutluhan.iptv.data.XtreamProfile
import kotlinx.coroutines.delay

@Composable
fun LoginScreen(app: AppState) {
    var mode by remember { mutableStateOf(0) } // 0 = Xtream, 1 = M3U
    var name by remember { mutableStateOf("") }
    var server by remember { mutableStateOf("") }
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var m3uUrl by remember { mutableStateOf("") }
    var epgUrl by remember { mutableStateOf("") }
    var formError by remember { mutableStateOf<String?>(null) }
    var saved by remember { mutableStateOf(app.storage.profiles()) }

    fun submit() {
        formError = null
        app.clearError()
        val profile: Profile = if (mode == 0) {
            if (server.isBlank() || username.isBlank() || password.isBlank()) {
                formError = "Sunucu, kullanıcı adı ve şifre gerekli."
                return
            }
            XtreamProfile(name.ifBlank { "Xtream ${username.trim()}" }, server.trim(), username.trim(), password.trim())
        } else {
            if (m3uUrl.isBlank()) {
                formError = "M3U linki gerekli."
                return
            }
            M3uProfile(name.ifBlank { "M3U" }, m3uUrl.trim(), epgUrl.trim().ifBlank { null })
        }
        app.connect(profile)
    }

    Box(Modifier.fillMaxSize().background(Palette.background)) {
        Row(Modifier.fillMaxSize().padding(horizontal = 48.dp, vertical = 36.dp)) {
            Column(Modifier.weight(1.2f).fillMaxHeight().verticalScroll(rememberScrollState())) {
                Text("IPTV", fontSize = 30.sp, color = Palette.text)
                Text(
                    "Sağlayıcınızın bilgilerini girin. Bilgiler sadece bu bilgisayarda saklanır.",
                    fontSize = 14.sp,
                    color = Palette.textDim,
                    modifier = Modifier.padding(bottom = 16.dp),
                )
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    FilterChip(selected = mode == 0, onClick = { mode = 0 }, label = { Text("Xtream Codes") })
                    FilterChip(selected = mode == 1, onClick = { mode = 1 }, label = { Text("M3U Linki") })
                }
                Spacer(Modifier.height(8.dp))
                Field(name, { name = it }, "Profil adı (isteğe bağlı)", "Evdeki IPTV", onDone = ::submit)
                if (mode == 0) {
                    Field(server, { server = it }, "Sunucu adresi", "http://ornek.com:8080", onDone = ::submit)
                    Field(username, { username = it }, "Kullanıcı adı", onDone = ::submit)
                    Field(password, { password = it }, "Şifre", password = true, onDone = ::submit)
                } else {
                    Field(m3uUrl, { m3uUrl = it }, "M3U playlist linki", "http://.../get.php?...&type=m3u_plus", onDone = ::submit)
                    Field(epgUrl, { epgUrl = it }, "EPG (XMLTV) linki, isteğe bağlı", "Boş bırakırsanız playlist içindeki kullanılır", onDone = ::submit)
                }
                val message = formError ?: app.error
                if (message != null) {
                    Text(message, color = Palette.error, fontSize = 14.sp, modifier = Modifier.padding(top = 8.dp))
                }
                Spacer(Modifier.height(14.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Button(onClick = ::submit, enabled = !app.loading) {
                        Text(if (app.loading) "Bağlanıyor..." else "Bağlan")
                    }
                    if (app.userAgent != DEFAULT_USER_AGENT) {
                        OutlinedButton(onClick = { app.updateUserAgent(DEFAULT_USER_AGENT) }) {
                            Text("User-Agent'ı VLC'ye geri al")
                        }
                    }
                }
            }
            Spacer(Modifier.width(48.dp))
            Column(Modifier.weight(1f).fillMaxHeight()) {
                if (saved.isNotEmpty()) {
                    Text("Kayıtlı profiller", fontSize = 18.sp, color = Palette.text, modifier = Modifier.padding(bottom = 8.dp))
                    LazyColumn {
                        items(saved, key = { it.name }) { p ->
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                ListRow(
                                    text = p.name,
                                    secondary = when (p) {
                                        is XtreamProfile -> p.server
                                        is M3uProfile -> "M3U"
                                    },
                                    modifier = Modifier.weight(1f),
                                    onClick = { app.connect(p) },
                                )
                                IconButton(onClick = {
                                    app.deleteProfile(p)
                                    saved = app.storage.profiles()
                                }) {
                                    Icon(Icons.Filled.Delete, "Profili sil", tint = Palette.textDim)
                                }
                            }
                        }
                    }
                }
            }
        }
        if (app.loading) LoadingOverlay(onCancel = { app.cancelLoading() })
    }
}

@Composable
private fun Field(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    placeholder: String? = null,
    password: Boolean = false,
    onDone: () -> Unit,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        placeholder = placeholder?.let { { Text(it) } },
        singleLine = true,
        visualTransformation = if (password) PasswordVisualTransformation() else VisualTransformation.None,
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
        keyboardActions = KeyboardActions(onDone = { onDone() }),
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .onPreviewKeyEvent {
                if (it.type == KeyEventType.KeyDown && (it.key == Key.Enter || it.key == Key.NumPadEnter)) {
                    onDone()
                    true
                } else {
                    false
                }
            },
    )
}

@Composable
private fun LoadingOverlay(onCancel: () -> Unit) {
    var seconds by remember { mutableIntStateOf(0) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(1000)
            seconds++
        }
    }
    Box(
        Modifier.fillMaxSize().background(Palette.background.copy(alpha = 0.92f)).blockPointer(),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("Kanallar yükleniyor... ($seconds sn)", fontSize = 20.sp, color = Palette.text)
            Text(
                "Büyük listelerde bu bir dakika kadar sürebilir.",
                fontSize = 14.sp,
                color = Palette.textDim,
                modifier = Modifier.padding(top = 6.dp, bottom = 16.dp),
            )
            Button(onClick = onCancel) { Text("İptal") }
        }
    }
}
