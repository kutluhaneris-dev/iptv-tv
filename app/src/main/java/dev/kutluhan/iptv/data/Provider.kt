package dev.kutluhan.iptv.data

interface Provider {
    /** Loads everything the home screen needs. Throws with a user-readable message on failure. */
    suspend fun loadCatalog(): Catalog

    /** Current and upcoming programmes for [channel], or empty if there is no guide. */
    suspend fun epg(channel: LiveChannel): List<EpgProgram>

    suspend fun episodes(series: SeriesItem): List<Episode>
}

enum class LiveFormat(val extension: String, val label: String) {
    TS("ts", "MPEG-TS (.ts)"),
    HLS("m3u8", "HLS (.m3u8)"),
}
