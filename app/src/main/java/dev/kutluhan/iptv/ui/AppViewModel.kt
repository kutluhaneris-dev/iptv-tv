package dev.kutluhan.iptv.ui

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import dev.kutluhan.iptv.data.Catalog
import dev.kutluhan.iptv.data.EpgProgram
import dev.kutluhan.iptv.data.Episode
import dev.kutluhan.iptv.data.Http
import dev.kutluhan.iptv.data.LiveChannel
import dev.kutluhan.iptv.data.LiveFormat
import dev.kutluhan.iptv.data.PlayerMode
import dev.kutluhan.iptv.data.M3uProfile
import dev.kutluhan.iptv.data.M3uProvider
import dev.kutluhan.iptv.data.Profile
import dev.kutluhan.iptv.data.Provider
import dev.kutluhan.iptv.data.SeriesItem
import dev.kutluhan.iptv.data.Storage
import dev.kutluhan.iptv.data.XtreamProfile
import dev.kutluhan.iptv.data.XtreamProvider
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout

private const val LOAD_TIMEOUT_MS = 120_000L

class AppViewModel(app: Application) : AndroidViewModel(app) {
    val storage = Storage(app)

    var profile by mutableStateOf<Profile?>(null)
        private set
    var catalog by mutableStateOf<Catalog?>(null)
        private set
    var loading by mutableStateOf(false)
        private set
    var error by mutableStateOf<String?>(null)
        private set
    var favorites by mutableStateOf<Set<String>>(emptySet())
        private set
    var liveFormat by mutableStateOf(storage.liveFormat)
        private set
    var userAgent by mutableStateOf(storage.userAgent)
        private set
    var playerMode by mutableStateOf(storage.playerMode)
        private set

    /** EPG by channel id; an empty list means "looked it up, nothing there". */
    val epg = mutableStateMapOf<String, List<EpgProgram>>()

    private var provider: Provider? = null
    private var loadJob: Job? = null
    private val epgRequested = HashSet<String>()

    init {
        Http.userAgent = userAgent
        storage.activeProfile()?.let { connect(it) }
    }

    fun connect(p: Profile) {
        loadJob?.cancel()
        error = null
        loading = true
        profile = p
        catalog = null
        epg.clear()
        epgRequested.clear()
        val prov = when (p) {
            is XtreamProfile -> XtreamProvider(p) { liveFormat }
            is M3uProfile -> M3uProvider(p)
        }
        provider = prov
        loadJob = viewModelScope.launch {
            try {
                val c = withTimeout(LOAD_TIMEOUT_MS) { prov.loadCatalog() }
                storage.saveProfile(p)
                favorites = storage.favorites(p)
                catalog = c
            } catch (e: TimeoutCancellationException) {
                error = "Sunucu ${LOAD_TIMEOUT_MS / 1000} saniyede yanıt vermedi. User-Agent'ı değiştirdiyseniz aşağıdan VLC'ye geri alıp tekrar deneyin."
                profile = null
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                error = e.message ?: e.javaClass.simpleName
                profile = null
            } finally {
                // A newer connect() or cancelLoading() owns the flag once it has replaced this job.
                if (loadJob === coroutineContext[Job]) loading = false
            }
        }
    }

    /** Stops a load the user gave up on and returns to the login screen. */
    fun cancelLoading() {
        loadJob?.cancel()
        loadJob = null
        loading = false
        profile = null
        catalog = null
        provider = null
        error = "Yükleme iptal edildi."
    }

    fun reload() {
        profile?.let { connect(it) }
    }

    fun logout() {
        loadJob?.cancel()
        storage.setActive(null)
        profile = null
        catalog = null
        provider = null
        error = null
        loading = false
    }

    fun deleteProfile(p: Profile) {
        storage.deleteProfile(p)
        if (profile?.name == p.name) logout()
    }

    fun clearError() {
        error = null
    }

    fun loadEpg(channel: LiveChannel) {
        val prov = provider ?: return
        if (!epgRequested.add(channel.id)) return
        viewModelScope.launch {
            val list = try {
                prov.epg(channel)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                emptyList()
            }
            epg[channel.id] = list
        }
    }

    suspend fun episodes(series: SeriesItem): List<Episode> = provider?.episodes(series).orEmpty()

    fun isFavorite(key: String) = key in favorites

    fun toggleFavorite(key: String) {
        val p = profile ?: return
        favorites = if (key in favorites) favorites - key else favorites + key
        storage.setFavorites(p, favorites)
    }

    fun updateLiveFormat(format: LiveFormat) {
        liveFormat = format
        storage.liveFormat = format
        // Channel URLs carry the extension, so rebuild them.
        if (profile is XtreamProfile) reload()
    }

    fun updatePlayerMode(mode: PlayerMode) {
        playerMode = mode
        storage.playerMode = mode
    }

    fun updateUserAgent(ua: String) {
        userAgent = ua
        storage.userAgent = ua
        Http.userAgent = ua
    }
}
