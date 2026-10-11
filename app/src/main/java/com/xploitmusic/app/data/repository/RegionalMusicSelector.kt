package com.xploitmusic.app.data.repository

import com.xploitmusic.app.domain.model.Song

private val blockedRegionalContent = Regex(
    "en vivo|\\blive\\b|directo|livestream|premiere|shorts?|#short|" +
        "noticias?|\\bnews\\b|eclipse|lunar|podcast|entrevista|interview|" +
        "trailer|gameplay|gaming|tutorial|review|reaction|prank|vlog|" +
        "documental|documentary|conferencia|debate|reportaje|breaking|" +
        "highlights?|partido|fútbol|futbol|soccer|full album|álbum completo|album completo|" +
        "top\\s*\\d+|charts?|ranking|playlists?|compilaci[oó]n|recopilaci[oó]n|" +
        "canciones más escuchadas de la semana|mega\\s*mix|mix\\s*20\\d{2}|radio en vivo",
    RegexOption.IGNORE_CASE
)

private val officialMusicSignals = Regex(
    "official audio|official video|audio oficial|video oficial|music video|visualizer|lyrics?|letra",
    RegexOption.IGNORE_CASE
)

private val alternateVersionSignals = Regex(
    "cover|karaoke|nightcore|sped up|slowed|remix|mashup|8d audio",
    RegexOption.IGNORE_CASE
)

private val titleNoise = Regex(
    "\\([^)]*(official|oficial|audio|video|lyrics?|letra|visualizer)[^)]*\\)|" +
        "\\[[^]]*(official|oficial|audio|video|lyrics?|letra|visualizer)[^]]*]|" +
        "official audio|official video|audio oficial|video oficial|music video|visualizer|lyrics?|letra",
    RegexOption.IGNORE_CASE
)

internal fun regionalMusicQueries(region: String, year: Int): List<String> = when (region) {
    "PE" -> listOf(
        "éxitos latinos Perú $year video oficial",
        "reggaeton más escuchado Perú $year video oficial",
        "pop latino Perú $year video oficial",
        "música urbana Perú $year audio oficial"
    )
    else -> listOf(
        "latin hits $region $year official video",
        "popular music $region $year official video",
        "pop hits $region $year official video",
        "urban music $region $year official audio"
    )
}

internal fun isRegionalMusicCandidate(song: Song): Boolean {
    if (song.id.isBlank() || song.thumbnailUrl.isBlank()) return false
    if (song.durationSeconds !in 75L..600L) return false
    if (song.title.length > 120) return false

    val searchableText = "${song.title} ${song.artist}"
    return !blockedRegionalContent.containsMatchIn(searchableText)
}

internal fun selectRegionalMusic(
    batches: List<List<Song>>,
    limit: Int = 25
): List<Song> {
    data class RankedSong(val song: Song, val score: Int, val order: Int)

    val ranked = batches.flatMapIndexed { sourceIndex, songs ->
        songs.mapIndexedNotNull { resultIndex, song ->
            if (!isRegionalMusicCandidate(song)) return@mapIndexedNotNull null

            val titleAndArtist = "${song.title} ${song.artist}"
            val officialBonus = if (officialMusicSignals.containsMatchIn(titleAndArtist)) 20 else 0
            val channelBonus = when {
                song.artist.contains("vevo", ignoreCase = true) -> 15
                song.artist.endsWith(" - Topic", ignoreCase = true) -> 15
                song.artist.contains("music", ignoreCase = true) -> 5
                else -> 0
            }
            val alternatePenalty = if (alternateVersionSignals.containsMatchIn(song.title)) 12 else 0
            val score = 100 - (sourceIndex * 10) - (resultIndex * 3) +
                officialBonus + channelBonus - alternatePenalty

            RankedSong(
                song = song,
                score = score,
                order = sourceIndex * 100 + resultIndex
            )
        }
    }.sortedWith(
        compareByDescending<RankedSong> { it.score }
            .thenBy { it.order }
    )

    val seenIds = mutableSetOf<String>()
    val seenTitles = mutableSetOf<String>()
    return ranked.mapNotNull { rankedSong ->
        val song = rankedSong.song
        val normalizedTitle = song.title
            .lowercase()
            .replace(titleNoise, " ")
            .replace(Regex("[^\\p{L}\\p{N}]+"), " ")
            .trim()

        if (!seenIds.add(song.id)) return@mapNotNull null
        if (normalizedTitle.isNotBlank() && !seenTitles.add(normalizedTitle)) return@mapNotNull null
        song
    }.take(limit)
}
