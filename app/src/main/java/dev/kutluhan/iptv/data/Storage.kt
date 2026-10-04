package dev.kutluhan.iptv.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/** Saved profiles, the active one, favorites and player settings. Everything stays on the TV. */
class Storage(context: Context) {
    private val prefs = context.getSharedPreferences("iptv", Context.MODE_PRIVATE)

    fun profiles(): List<Profile> {
        val raw = prefs.getString("profiles", null) ?: return emptyList()
        return try {
            val arr = JSONArray(raw)
            (0 until arr.length()).mapNotNull { fromJson(arr.getJSONObject(it)) }
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun saveProfile(profile: Profile) {
        val list = profiles().filterNot { it.name == profile.name } + profile
        prefs.edit()
            .putString("profiles", JSONArray(list.map { toJson(it) }).toString())
            .putString("active", profile.name)
            .apply()
    }

    fun deleteProfile(profile: Profile) {
        val list = profiles().filterNot { it.name == profile.name }
        prefs.edit()
            .putString("profiles", JSONArray(list.map { toJson(it) }).toString())
            .remove("fav_${profile.name}")
            .apply()
        if (activeName() == profile.name) setActive(null)
    }

    fun activeName(): String? = prefs.getString("active", null)

    fun activeProfile(): Profile? = activeName()?.let { name -> profiles().firstOrNull { it.name == name } }

    fun setActive(name: String?) {
        prefs.edit().apply { if (name == null) remove("active") else putString("active", name) }.apply()
    }

    fun favorites(profile: Profile): Set<String> =
        prefs.getStringSet("fav_${profile.name}", emptySet())?.toSet() ?: emptySet()

    fun setFavorites(profile: Profile, keys: Set<String>) {
        prefs.edit().putStringSet("fav_${profile.name}", keys).apply()
    }

    var liveFormat: LiveFormat
        get() = runCatching { LiveFormat.valueOf(prefs.getString("live_format", null) ?: "TS") }.getOrDefault(LiveFormat.TS)
        set(value) = prefs.edit().putString("live_format", value.name).apply()

    var userAgent: String
        get() = prefs.getString("user_agent", null) ?: DEFAULT_USER_AGENT
        set(value) = prefs.edit().putString("user_agent", value).apply()

    var lastLiveChannel: String?
        get() = prefs.getString("last_live", null)
        set(value) = prefs.edit().putString("last_live", value).apply()

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
        "m3u" -> M3uProfile(o.getString("name"), o.getString("url"), o.optString("epg").orNullIfBlank())
        else -> null
    }
}
