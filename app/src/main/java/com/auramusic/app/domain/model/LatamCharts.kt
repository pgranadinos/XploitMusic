package com.auramusic.app.domain.model

data class ChartEntry(val rank: Int, val song: Song)

enum class LatamCountry(val label: String) {
    MX("México"), CO("Colombia"), AR("Argentina"), BR("Brasil"), CL("Chile"), PE("Perú"),
    EC("Ecuador"), BO("Bolivia"), PY("Paraguay"), UY("Uruguay"), CR("Costa Rica"),
    PA("Panamá"), GT("Guatemala"), HN("Honduras"), SV("El Salvador"),
    NI("Nicaragua"), DO("República Dominicana")
}

data class CountryChart(
    val country: LatamCountry,
    val sourceTitle: String,
    val playlistId: String,
    val entries: List<ChartEntry>,
    // Retrieval time, not the publication date of the ranking.
    val fetchedAtMs: Long,
)

data class CountryPosition(val country: LatamCountry, val rank: Int)
data class LatamTrack(val song: Song, val positions: List<CountryPosition>)

data class LatamCharts(
    val charts: List<CountryChart>,
    val entries: List<LatamTrack>,
    val unavailableCountries: List<LatamCountry>,
)
