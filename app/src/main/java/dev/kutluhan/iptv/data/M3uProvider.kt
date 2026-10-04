package dev.kutluhan.iptv.data

import android.util.Xml
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.xmlpull.v1.XmlPullParser
import java.io.BufferedReader
import java.io.InputStream
import java.io.InputStreamReader
import java.text.SimpleDateFormat
import java.util.Locale

class M3uProvider(private val profile: M3uProfile) : Provider {

    @Volatile
    private var guide: Map<String, List<EpgProgram>>? = null
    @Volatile
    private var guideUrl: String? = profile.epgUrl.orNullIfBlank()
    private var channels: List<LiveChannel> = emptyList()

    override suspend fun loadCatalog(): Catalog = withContext(Dispatchers.IO) {
        val playlist = Http.open(profile.url.trim()) { M3uParser.parse(it) }
        if (guideUrl == null) guideUrl = playlist.epgUrl
        val live = playlist.entries.filter { !it.isVod }
        val vod = playlist.entries.filter { it.isVod }
        channels = live.mapIndexed { i, e ->
            LiveChannel(
                id = "${i}_${e.url.hashCode()}",
                name = e.name,
                logo = e.logo,
                categoryId = e.group,
                epgId = e.tvgId ?: e.tvgName ?: e.name,
                url = e.url,
                number = e.channelNumber ?: (i + 1),
            )
        }
        Catalog(
            liveCategories = live.map { it.group }.distinct().map { Category(it, it) },
            live = channels,
            vodCategories = vod.map { it.group }.distinct().map { Category(it, it) },
            vod = vod.mapIndexed { i, e ->
                VodItem("${i}_${e.url.hashCode()}", e.name, e.logo, e.group, e.url, null)
            },
            seriesCategories = emptyList(),
            series = emptyList(),
        )
    }

    override suspend fun epg(channel: LiveChannel): List<EpgProgram> {
        val g = guide ?: loadGuide() ?: return emptyList()
        val now = System.currentTimeMillis()
        val key = channel.epgId ?: return emptyList()
        return g[key.lowercase(Locale.ROOT)].orEmpty().filter { it.end > now }
    }

    private val guideLock = Mutex()

    private suspend fun loadGuide(): Map<String, List<EpgProgram>>? {
        val url = guideUrl ?: return null
        return guideLock.withLock {
            guide?.let { return@withLock it }
            val wanted = HashSet<String>()
            channels.forEach { ch -> ch.epgId?.let { wanted += it.lowercase(Locale.ROOT) } }
            val parsed = try {
                Http.open(url) { XmltvParser.parse(it, wanted) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                emptyMap()
            }
            guide = parsed
            parsed
        }
    }

    override suspend fun episodes(series: SeriesItem): List<Episode> = emptyList()
}

data class M3uEntry(
    val name: String,
    val url: String,
    val group: String,
    val logo: String?,
    val tvgId: String?,
    val tvgName: String?,
    val channelNumber: Int?,
) {
    val isVod: Boolean
        get() {
            val u = url.lowercase(Locale.ROOT).substringBefore('?')
            return "/movie/" in u || "/series/" in u ||
                u.endsWith(".mp4") || u.endsWith(".mkv") || u.endsWith(".avi")
        }
}

class M3uPlaylist(val entries: List<M3uEntry>, val epgUrl: String?)

object M3uParser {
    private val attr = Regex("""([A-Za-z0-9_-]+)="([^"]*)"""")

    fun parse(input: InputStream): M3uPlaylist {
        val reader = BufferedReader(InputStreamReader(input, Charsets.UTF_8))
        val entries = ArrayList<M3uEntry>()
        var epgUrl: String? = null
        var pending: Map<String, String>? = null
        var pendingName: String? = null
        var pendingGroup: String? = null
        reader.lineSequence().forEach { raw ->
            val line = raw.trim()
            when {
                line.isEmpty() -> Unit
                line.startsWith("#EXTM3U") -> {
                    val a = attributes(line)
                    epgUrl = (a["url-tvg"] ?: a["x-tvg-url"])?.split(',')?.firstOrNull()?.trim().orNullIfBlank()
                }
                line.startsWith("#EXTINF") -> {
                    val commaIdx = nameSeparator(line)
                    val head = if (commaIdx >= 0) line.substring(0, commaIdx) else line
                    pending = attributes(head)
                    pendingName = if (commaIdx >= 0) line.substring(commaIdx + 1).trim() else null
                }
                line.startsWith("#EXTGRP:") -> pendingGroup = line.removePrefix("#EXTGRP:").trim()
                line.startsWith("#") -> Unit
                else -> {
                    val a = pending ?: emptyMap()
                    val name = pendingName.orNullIfBlank() ?: a["tvg-name"].orNullIfBlank() ?: line.substringAfterLast('/')
                    entries += M3uEntry(
                        name = name,
                        url = line,
                        group = a["group-title"].orNullIfBlank() ?: pendingGroup ?: "Diğer",
                        logo = a["tvg-logo"].orNullIfBlank(),
                        tvgId = a["tvg-id"].orNullIfBlank(),
                        tvgName = a["tvg-name"].orNullIfBlank(),
                        channelNumber = a["tvg-chno"]?.toIntOrNull(),
                    )
                    pending = null
                    pendingName = null
                    pendingGroup = null
                }
            }
        }
        return M3uPlaylist(entries, epgUrl)
    }

    private fun attributes(s: String): Map<String, String> =
        attr.findAll(s).associate { it.groupValues[1].lowercase(Locale.ROOT) to it.groupValues[2] }

    /** Index of the comma that separates the attributes from the display name, ignoring commas in quotes. */
    private fun nameSeparator(line: String): Int {
        var inQuotes = false
        for (i in line.indices) {
            when (line[i]) {
                '"' -> inQuotes = !inQuotes
                ',' -> if (!inQuotes) return i
            }
        }
        return -1
    }
}

object XmltvParser {
    private val windowBefore = 2 * 60 * 60 * 1000L
    private val windowAfter = 36 * 60 * 60 * 1000L

    /** Parses programmes for the lowercased channel ids in [wanted], keyed by lowercased id, keeping only the next ~day. */
    fun parse(input: InputStream, wanted: Set<String>): Map<String, List<EpgProgram>> {
        val parser = Xml.newPullParser()
        parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false)
        parser.setInput(input, null)
        val now = System.currentTimeMillis()
        val out = HashMap<String, MutableList<EpgProgram>>()
        val fmtZ = SimpleDateFormat("yyyyMMddHHmmss Z", Locale.US)
        val fmt = SimpleDateFormat("yyyyMMddHHmmss", Locale.US)
        fun time(s: String?): Long? {
            if (s.isNullOrBlank()) return null
            return try {
                (if (s.trim().length > 14) fmtZ.parse(s.trim()) else fmt.parse(s.trim()))?.time
            } catch (e: Exception) {
                null
            }
        }

        var channel: String? = null
        var start: Long? = null
        var end: Long? = null
        var title: String? = null
        var desc: String? = null
        var event = parser.eventType
        while (event != XmlPullParser.END_DOCUMENT) {
            if (event == XmlPullParser.START_TAG) {
                when (parser.name) {
                    "programme" -> {
                        channel = parser.getAttributeValue(null, "channel")
                        start = time(parser.getAttributeValue(null, "start"))
                        end = time(parser.getAttributeValue(null, "stop"))
                        title = null
                        desc = null
                        val keep = channel != null && channel.lowercase(Locale.ROOT) in wanted &&
                            start != null && end != null && end > now - windowBefore && start < now + windowAfter
                        if (!keep) {
                            skip(parser)
                            channel = null
                        }
                    }
                    "title" -> if (channel != null && title == null) title = parser.nextText()
                    "desc" -> if (channel != null && desc == null) desc = parser.nextText()
                }
            } else if (event == XmlPullParser.END_TAG && parser.name == "programme" && channel != null) {
                out.getOrPut(channel.lowercase(Locale.ROOT)) { ArrayList() } += EpgProgram(title ?: "", desc, start!!, end!!)
                channel = null
            }
            event = parser.next()
        }
        out.values.forEach { it.sortBy { p -> p.start } }
        return out
    }

    private fun skip(parser: XmlPullParser) {
        var depth = 1
        while (depth > 0) {
            when (parser.next()) {
                XmlPullParser.START_TAG -> depth++
                XmlPullParser.END_TAG -> depth--
                XmlPullParser.END_DOCUMENT -> return
            }
        }
    }
}
