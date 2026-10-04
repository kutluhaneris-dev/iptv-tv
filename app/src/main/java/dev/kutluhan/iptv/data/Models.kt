package dev.kutluhan.iptv.data

data class Category(val id: String, val name: String)

data class LiveChannel(
    val id: String,
    val name: String,
    val logo: String?,
    val categoryId: String,
    val epgId: String?,
    val url: String,
    val number: Int,
)

data class VodItem(
    val id: String,
    val name: String,
    val poster: String?,
    val categoryId: String,
    val url: String,
    val rating: String?,
)

data class SeriesItem(
    val id: String,
    val name: String,
    val poster: String?,
    val categoryId: String,
    val plot: String?,
    val rating: String?,
)

data class Episode(
    val id: String,
    val title: String,
    val season: Int,
    val number: Int,
    val url: String,
    val plot: String?,
    val image: String?,
)

data class EpgProgram(
    val title: String,
    val description: String?,
    val start: Long,
    val end: Long,
) {
    fun isNow(now: Long = System.currentTimeMillis()) = now in start until end
}

/** Something the player can play. [channel] is set for live channels so EPG can be shown. */
data class PlayItem(
    val title: String,
    val url: String,
    val isLive: Boolean,
    val channel: LiveChannel? = null,
)

class Catalog(
    val liveCategories: List<Category>,
    val live: List<LiveChannel>,
    val vodCategories: List<Category>,
    val vod: List<VodItem>,
    val seriesCategories: List<Category>,
    val series: List<SeriesItem>,
) {
    val liveByCategory: Map<String, List<LiveChannel>> by lazy { live.groupBy { it.categoryId } }
    val vodByCategory: Map<String, List<VodItem>> by lazy { vod.groupBy { it.categoryId } }
    val seriesByCategory: Map<String, List<SeriesItem>> by lazy { series.groupBy { it.categoryId } }
}

sealed interface Profile {
    val name: String
}

data class XtreamProfile(
    override val name: String,
    val server: String,
    val username: String,
    val password: String,
) : Profile

data class M3uProfile(
    override val name: String,
    val url: String,
    val epgUrl: String?,
) : Profile

object FavKeys {
    fun live(id: String) = "live:$id"
    fun vod(id: String) = "vod:$id"
    fun series(id: String) = "series:$id"
}
