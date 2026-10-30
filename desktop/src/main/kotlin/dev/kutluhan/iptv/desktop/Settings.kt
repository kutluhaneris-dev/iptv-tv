package dev.kutluhan.iptv.desktop

import dev.kutluhan.iptv.data.DEFAULT_USER_AGENT
import dev.kutluhan.iptv.data.LiveFormat
import dev.kutluhan.iptv.data.M3uProfile
import dev.kutluhan.iptv.data.Profile
import dev.kutluhan.iptv.data.XtreamProfile
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * Saved profiles, the active one, favorites and player settings, kept in a JSON file in the
 * user's application data folder. Same shape as the TV app's Storage.
 */
class Settings(private val file: File = defaultFile()) {
    private val root: JSONObject = runCatching { JSONObject(file.readText()) }.getOrElse { JSONObject() }

    @Synchronized
    private fun save() {
        runCatching {
            file.parentFile?.mkdirs()
            val tmp = File(file.parentFile, file.name + ".tmp")
            tmp.writeText(root.toString(2))
            if (!tmp.renameTo(file)) {
                file.delete()
                tmp.renameTo(file)
            }
        }
    }

    private fun string(key: String): String? = root.optString(key, "").ifEmpty { null }

    private fun put(key: String, value: Any?) {
        if (value == null) root.remove(key) else root.put(key, value)
        save()
    }

    fun profiles(): List<Profile> {
        val arr = root.optJSONArray("profiles") ?: return emptyList()
        return (0 until arr.length()).mapNotNull { i -> arr.optJSONObject(i)?.let { fromJson(it) } }
    }

    fun saveProfile(profile: Profile) {
        val list = profiles().filterNot { it.name == profile.name } + profile
        root.put("profiles", JSONArray(list.map { toJson(it) }))
        put("active", profile.name)
    }

    fun deleteProfile(profile: Profile) {
        val list = profiles().filterNot { it.name == profile.name }
        root.put("profiles", JSONArray(list.map { toJson(it) }))
        root.optJSONObject("favorites")?.remove(profile.name)
        if (activeName() == profile.name) root.remove("active")
        save()
    }

    fun activeName(): String? = string("active")

    fun activeProfile(): Profile? = activeName()?.let { name -> profiles().firstOrNull { it.name == name } }

    fun setActive(name: String?) = put("active", name)

    fun favorites(profile: Profile): Set<String> {
        val arr = root.optJSONObject("favorites")?.optJSONArray(profile.name) ?: return emptySet()
        return (0 until arr.length()).map { arr.getString(it) }.toSet()
    }

    fun setFavorites(profile: Profile, keys: Set<String>) {
        val favs = root.optJSONObject("favorites") ?: JSONObject().also { root.put("favorites", it) }
        favs.put(profile.name, JSONArray(keys.toList()))
        save()
    }

    var liveFormat: LiveFormat
        get() = runCatching { LiveFormat.valueOf(string("live_format") ?: "TS") }.getOrDefault(LiveFormat.TS)
        set(value) = put("live_format", value.name)

    var livePreview: Boolean
        get() = root.optBoolean("live_preview", true)
        set(value) = put("live_preview", value)

    var userAgent: String
        get() = string("user_agent") ?: DEFAULT_USER_AGENT
        set(value) = put("user_agent", value)

    var volume: Int
        get() = root.optInt("volume", 100)
        set(value) = put("volume", value)

    var lastLiveChannel: String?
        get() = string("last_live")
        set(value) = put("last_live", value)

    private fun toJson(p: Profile): JSONObject = when (p) {
        is XtreamProfile -> JSONObject()
            .put("type", "xtream").put("name", p.name)
            .put("server", p.server).put("username", p.username).put("password", p.password)
        is M3uProfile -> JSONObject()
            .put("type", "m3u").put("name", p.name)
            .put("url", p.url).put("epg", p.epgUrl ?: "")
    }

    private fun fromJson(o: JSONObject): Profile? = when (o.optString("type")) {
        "xtream" -> XtreamProfile(o.getString("name"), o.getString("server"), o.getString("username"), o.getString("password"))
        "m3u" -> M3uProfile(o.getString("name"), o.getString("url"), o.optString("epg").ifBlank { null })
        else -> null
    }

    companion object {
        fun defaultFile(): File {
            val os = System.getProperty("os.name").lowercase()
            val home = System.getProperty("user.home")
            val dir = when {
                "mac" in os -> File(home, "Library/Application Support/IPTV")
                "win" in os -> File(System.getenv("APPDATA") ?: home, "IPTV")
                else -> File(home, ".config/iptv")
            }
            return File(dir, "settings.json")
        }
    }
}
