package dev.kutluhan.iptv.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Button
import androidx.tv.material3.Text
import dev.kutluhan.iptv.data.M3uProfile
import dev.kutluhan.iptv.data.Profile
import dev.kutluhan.iptv.data.XtreamProfile

@Composable
fun LoginScreen(vm: AppViewModel) {
    var mode by rememberSaveable { mutableStateOf(0) } // 0 = Xtream, 1 = M3U
    var name by rememberSaveable { mutableStateOf("") }
    var server by rememberSaveable { mutableStateOf("") }
    var username by rememberSaveable { mutableStateOf("") }
    var password by rememberSaveable { mutableStateOf("") }
    var m3uUrl by rememberSaveable { mutableStateOf("") }
    var epgUrl by rememberSaveable { mutableStateOf("") }
    var formError by rememberSaveable { mutableStateOf<String?>(null) }
    var saved by remember { mutableStateOf(vm.storage.profiles()) }

    fun submit() {
        formError = null
        vm.clearError()
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
        vm.connect(profile)
    }

    Row(
        Modifier
            .fillMaxSize()
            .background(Palette.background)
            .padding(horizontal = 48.dp, vertical = 32.dp),
    ) {
        Column(
            Modifier
                .weight(1.3f)
                .fillMaxHeight()
                .verticalScroll(rememberScrollState()),
        ) {
            Text("IPTV TV", fontSize = 30.sp, color = Palette.text)
            Text(
                "Sağlayıcınızın bilgilerini girin. Bilgiler sadece bu TV'de saklanır.",
                fontSize = 14.sp,
                color = Palette.textDim,
                modifier = Modifier.padding(bottom = 16.dp),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                ModeButton("Xtream Codes", mode == 0) { mode = 0 }
                ModeButton("M3U Linki", mode == 1) { mode = 1 }
            }
            Spacer(Modifier.height(8.dp))
            TvTextField(name, { name = it }, "Profil adı (isteğe bağlı)", placeholder = "Evdeki IPTV")
            if (mode == 0) {
                TvTextField(server, { server = it }, "Sunucu adresi", placeholder = "http://ornek.com:8080")
                TvTextField(username, { username = it }, "Kullanıcı adı")
                TvTextField(
                    password, { password = it }, "Şifre",
                    password = true, imeAction = ImeAction.Done, onDone = { submit() },
                )
            } else {
                TvTextField(m3uUrl, { m3uUrl = it }, "M3U playlist linki", placeholder = "http://.../get.php?...&type=m3u_plus")
                TvTextField(
                    epgUrl, { epgUrl = it }, "EPG (XMLTV) linki, isteğe bağlı",
                    placeholder = "Boş bırakırsanız playlist içindeki kullanılır",
                    imeAction = ImeAction.Done, onDone = { submit() },
                )
            }
            val message = formError ?: vm.error
            if (message != null) {
                Text(message, color = Palette.error, fontSize = 14.sp, modifier = Modifier.padding(top = 8.dp))
            }
            Spacer(Modifier.height(12.dp))
            Button(onClick = { submit() }, enabled = !vm.loading) {
                Text(if (vm.loading) "Bağlanıyor..." else "Bağlan")
            }
        }
        Spacer(Modifier.width(40.dp))
        Column(Modifier.weight(1f).fillMaxHeight()) {
            if (saved.isNotEmpty()) {
                Text("Kayıtlı profiller", fontSize = 18.sp, color = Palette.text, modifier = Modifier.padding(bottom = 8.dp))
                LazyColumn {
                    items(saved, key = { it.name }) { p ->
                        TvRow(
                            text = p.name,
                            secondary = when (p) {
                                is XtreamProfile -> p.server
                                is M3uProfile -> "M3U"
                            } + "  ·  silmek için basılı tutun",
                            onLongClick = {
                                vm.deleteProfile(p)
                                saved = vm.storage.profiles()
                            },
                            onClick = { vm.connect(p) },
                        )
                    }
                }
            }
        }
    }

    if (vm.loading) {
        Box(Modifier.fillMaxSize().background(Palette.background.copy(alpha = 0.85f)), contentAlignment = Alignment.Center) {
            Text("Kanallar yükleniyor...", fontSize = 20.sp, color = Palette.text)
        }
    }
}

@Composable
private fun ModeButton(text: String, selected: Boolean, onClick: () -> Unit) {
    // Same composable for both states so focus is not lost when the selection changes.
    Button(onClick = onClick) { Text(if (selected) "✓ $text" else text) }
}
