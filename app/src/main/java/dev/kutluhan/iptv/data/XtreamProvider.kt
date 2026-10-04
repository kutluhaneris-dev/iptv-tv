package dev.kutluhan.iptv.data

import android.util.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.URLEncoder

class XtreamProvider(
    private val profile: XtreamProfile,
    private val liveFormat: () -> LiveFormat,
) : Provider {

    private val base: String = normalizeServer(profile.server)
    private val user = profile.username.trim()
    private val pass = profile.password.trim()

    private fun enc(s: String) = URLEncoder.encode(s, "UTF-8")

    private fun api(action: String? = null, extra: String = ""): String =
        "$base/player_api.php?username=${enc(user)}&password=${enc(pass)}" +
            (action?.let { "&action=$it" } ?: "") + extra

    /** Checks the credentials; throws with a readable message if they are wrong or expired. */
    suspend fun authenticate() = withContext(Dispatchers.IO) {
        val text = try {
            Http.text(api())
        } catch (e: IOException) {
            throw IOException("Sunucuya bağlanılamadı: ${e.message}", e)
        }
        val json = try {
            JSONObject(text)
        } catch (e: Exception) {
            throw IOException("Sunucu Xtream Codes yanıtı vermedi. Adresi kontrol edin.")
        }
        val info = json.optJSONObject("user_info")
            ?: throw IOException("Kullanıcı adı veya şifre hatalı.")
        if (info.optString("auth") != "1") throw IOException("Kullanıcı adı veya şifre hatalı.")
        val status = info.optString("status")
        if (status.isNotEmpty() && !status.equals("Active", ignoreCase = true)) {
            throw IOException("Hesap durumu: $status")
        }
    }

    override suspend fun loadCatalog(): Catalog = coroutineScope {
        authenticate()
        val liveCats = async(Dispatchers.IO) { categories("get_live_categories") }
        val vodCats = async(Dispatchers.IO) { categories("get_vod_categories") }
        val seriesCats = async(Dispatchers.IO) { categories("get_series_categories") }
        val live = async(Dispatchers.IO) { liveStreams() }
        val vod = async(Dispatchers.IO) { vodStreams() }
        val series = async(Dispatchers.IO) { seriesList() }
        Catalog(
            liveCategories = liveCats.await(),
            live = live.await(),
            vodCategories = vodCats.await(),
            vod = vod.await(),
            seriesCategories = seriesCats.await(),
            series = series.await(),
        )
    }

    private fun categories(action: String): List<Category> = Http.open(api(action)) { input ->
        val out = ArrayList<Category>()
        readJsonObjects(input) { o ->
            val id = o["category_id"] ?: return@readJsonObjects
            out += Category(id, o["category_name"] ?: id)
        }
        out
    }

    private fun liveStreams(): List<LiveChannel> = Http.open(api("get_live_streams")) { input ->
        val ext = liveFormat().extension
        val out = ArrayList<LiveChannel>()
        readJsonObjects(input) { o ->
            val id = o["stream_id"] ?: return@readJsonObjects
            out += LiveChannel(
                id = id,
                name = o["name"]?.trim() ?: id,
                logo = o["stream_icon"].orNullIfBlank(),
                categoryId = o["category_id"] ?: "",
                epgId = o["epg_channel_id"].orNullIfBlank(),
                url = "$base/live/${enc(user)}/${enc(pass)}/$id.$ext",
                number = o["num"]?.toIntOrNull() ?: 0,
            )
        }
        out
    }

    private fun vodStreams(): List<VodItem> = Http.open(api("get_vod_streams")) { input ->
        val out = ArrayList<VodItem>()
        readJsonObjects(input) { o ->
            val id = o["stream_id"] ?: return@readJsonObjects
            val ext = o["container_extension"].orNullIfBlank() ?: "mp4"
            out += VodItem(
                id = id,
                name = o["name"]?.trim() ?: id,
                poster = o["stream_icon"].orNullIfBlank(),
                categoryId = o["category_id"] ?: "",
                url = "$base/movie/${enc(user)}/${enc(pass)}/$id.$ext",
                rating = o["rating"].orNullIfBlank()?.takeIf { it != "0" },
            )
        }
        out
    }

    private fun seriesList(): List<SeriesItem> = Http.open(api("get_series")) { input ->
        val out = ArrayList<SeriesItem>()
        readJsonObjects(input) { o ->
            val id = o["series_id"] ?: return@readJsonObjects
            out += SeriesItem(
                id = id,
                name = o["name"]?.trim() ?: id,
                poster = o["cover"].orNullIfBlank(),
                categoryId = o["category_id"] ?: "",
                plot = o["plot"].orNullIfBlank(),
                rating = o["rating"].orNullIfBlank()?.takeIf { it != "0" },
            )
        }
        out
    }

    override suspend fun epg(channel: LiveChannel): List<EpgProgram> = withContext(Dispatchers.IO) {
        val json = JSONObject(Http.text(api("get_short_epg", "&stream_id=${channel.id}&limit=8")))
        val listings = json.optJSONArray("epg_listings") ?: return@withContext emptyList()
        (0 until listings.length()).mapNotNull { i ->
            val p = listings.optJSONObject(i) ?: return@mapNotNull null
            val start = p.optString("start_timestamp").toLongOrNull() ?: return@mapNotNull null
            val end = p.optString("stop_timestamp").toLongOrNull() ?: return@mapNotNull null
            EpgProgram(
                title = decodeB64(p.optString("title")),
                description = decodeB64(p.optString("description")).orNullIfBlank(),
                start = start * 1000,
                end = end * 1000,
            )
        }.filter { it.end > System.currentTimeMillis() }.sortedBy { it.start }
    }

    override suspend fun episodes(series: SeriesItem): List<Episode> = withContext(Dispatchers.IO) {
        val json = JSONObject(Http.text(api("get_series_info", "&series_id=${series.id}")))
        val out = ArrayList<Episode>()
        fun addAll(arr: JSONArray?, seasonKey: Int?) {
            if (arr == null) return
            for (i in 0 until arr.length()) {
                val e = arr.optJSONObject(i) ?: continue
                val id = e.optString("id").orNullIfBlank() ?: continue
                val ext = e.optString("container_extension").orNullIfBlank() ?: "mp4"
                val info = e.optJSONObject("info")
                val season = e.optString("season").toIntOrNull() ?: seasonKey ?: 1
                val num = e.optString("episode_num").toIntOrNull() ?: (i + 1)
                out += Episode(
                    id = id,
                    title = e.optString("title").orNullIfBlank() ?: "Bölüm $num",
                    season = season,
                    number = num,
                    url = "$base/series/${enc(user)}/${enc(pass)}/$id.$ext",
                    plot = info?.optString("plot").orNullIfBlank(),
                    image = info?.optString("movie_image").orNullIfBlank(),
                )
            }
        }
        when (val episodes = json.opt("episodes")) {
            is JSONObject -> episodes.keys().forEach { key -> addAll(episodes.optJSONArray(key), key.toIntOrNull()) }
            is JSONArray -> for (i in 0 until episodes.length()) addAll(episodes.optJSONArray(i), null)
        }
        out.sortedWith(compareBy({ it.season }, { it.number }))
    }

    private fun decodeB64(s: String): String {
        if (s.isEmpty()) return s
        return try {
            String(Base64.decode(s, Base64.DEFAULT), Charsets.UTF_8)
        } catch (e: IllegalArgumentException) {
            s
        }
    }

    companion object {
        fun normalizeServer(raw: String): String {
            var s = raw.trim().trimEnd('/')
            if (!s.startsWith("http://", ignoreCase = true) && !s.startsWith("https://", ignoreCase = true)) {
                s = "http://$s"
            }
            // People often paste a full API or playlist URL; keep only scheme://host:port.
            val schemeEnd = s.indexOf("://") + 3
            val pathStart = s.indexOf('/', schemeEnd)
            return if (pathStart > 0) s.substring(0, pathStart) else s
        }
    }
}
