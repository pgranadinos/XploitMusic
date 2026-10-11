package com.auramusic.app.data.provider.youtubemusic

import com.auramusic.app.domain.model.Song
import com.auramusic.app.domain.model.ChartEntry
import com.auramusic.app.domain.model.LatamCountry
import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import java.text.Normalizer
import java.util.Locale

internal class YouTubeMusicParser {

    data class ChartPlaylist(val browseId: String, val title: String)

    /** Compare the same metric/period across countries: daily official music-video charts. */
    fun findCountryChartPlaylist(response: JsonObject, country: LatamCountry): ChartPlaylist? =
        response.objectsNamed(TWO_ROW_ITEM).mapNotNull { renderer ->
            val title = renderer.titleText().orEmpty()
            val normalized = title.recordingKey()
            val countryName = country.label.recordingKey()
            val matchesCountry = (" $normalized ").contains(" $countryName ")
            val daily = normalized.contains("diario") || normalized.contains("daily")
            val musicVideos = normalized.contains("videos musicales") || normalized.contains("music videos")
            if (!matchesCountry || !daily || !musicVideos || !normalized.contains("top") ||
                normalized.contains("shorts") || normalized.contains("global") ||
                normalized.contains("todo el mundo")) return@mapNotNull null
            val browseId = renderer.objectValue("navigationEndpoint")
                ?.objectValue("browseEndpoint")?.string("browseId")
                ?: renderer.objectValue("title")?.firstStringNamed("browseId")
            if (browseId?.startsWith("VL") != true) return@mapNotNull null
            ChartPlaylist(browseId, title)
        }.firstOrNull()

    /** Keep source ranks, including gaps left by unavailable tracks. No re-ranking or search fallback. */
    fun parseRankedChartEntries(response: JsonObject): List<ChartEntry> =
        response.objectsNamed("musicPlaylistShelfRenderer")
            .flatMap { it.array("contents").orEmpty().asSequence() }
            .mapNotNull { it.takeIf(JsonElement::isJsonObject)?.asJsonObject?.objectValue(RESPONSIVE_ITEM) }
            .mapNotNull { renderer ->
                val rankText = renderer.objectValue("customIndexColumn")
                    ?.objectValue("musicCustomIndexColumnRenderer")?.objectValue("text")
                val rank = (rankText?.firstStringNamed("text") ?: rankText?.string("simpleText"))
                    ?.trim()?.toIntOrNull()?.takeIf { it > 0 } ?: return@mapNotNull null
                val song = parseResponsiveItem(renderer, officialAudioOnly = false) ?: return@mapNotNull null
                ChartEntry(rank, song)
            }.distinctBy { it.song.id }.sortedBy { it.rank }.toList()

    fun parseSearchSongs(response: JsonObject): List<Song> =
        response.objectsNamed(RESPONSIVE_ITEM)
            .mapNotNull { parseResponsiveItem(it, officialAudioOnly = true) }
            .toList()
            .deduplicated()

    fun parseWatchQueue(response: JsonObject): List<Song> =
        response.objectsNamed(PLAYLIST_ITEM)
            .mapNotNull { parsePlaylistItem(it, officialAudioOnly = true) }
            .toList()
            .deduplicated()

    fun parseChartSongs(response: JsonObject): List<Song> =
        response.objectsNamed(RESPONSIVE_ITEM)
            .mapNotNull { parseResponsiveItem(it, officialAudioOnly = false) }
            .toList()
            .deduplicated()

    fun findChartPlaylistBrowseId(response: JsonObject): String? =
        response.objectsNamed(TWO_ROW_ITEM)
            .mapNotNull { renderer ->
                val browseId = renderer.firstStringNamed("browseId")
                    ?.takeIf { it.startsWith("VL") }
                    ?: return@mapNotNull null
                val title = renderer.titleText().orEmpty().lowercase(Locale.ROOT)
                val priority = when {
                    title.contains("daily") || title.contains("diario") -> 3
                    title.contains("top") -> 2
                    else -> 1
                }
                Triple(priority, title, browseId)
            }
            .maxWithOrNull(compareBy<Triple<Int, String, String>> { it.first }.thenBy { it.second })
            ?.third

    private fun parseResponsiveItem(renderer: JsonObject, officialAudioOnly: Boolean): Song? {
        if (renderer.string("musicItemRendererDisplayPolicy")
                == "MUSIC_ITEM_RENDERER_DISPLAY_POLICY_GREY_OUT"
        ) return null

        val videoType = renderer.firstStringNamed("musicVideoType")
        if (!isAcceptedVideoType(videoType, officialAudioOnly)) return null

        val columns = renderer.array("flexColumns")
        val title = columns?.getOrNull(0)?.asJsonObject
            ?.columnRuns()?.firstOrNull()?.text()
            ?.trim().orEmpty()
        val videoId = renderer.firstStringNamed("videoId")?.trim().orEmpty()
        if (title.isBlank() || videoId.isBlank()) return null

        val metadataRuns = columns.orEmpty()
            .drop(1)
            .flatMap { it.asJsonObject.columnRuns() }
        val artist = metadataRuns.artistNames().ifEmpty {
            metadataRuns.mapNotNull { it.text() }
                .firstOrNull(::isUsableArtistFallback)
                ?.let(::listOf)
                .orEmpty()
        }.joinToString(", ").ifBlank { "YouTube Music" }

        return Song(
            id = videoId,
            title = title,
            artist = artist,
            thumbnailUrl = renderer.bestThumbnailUrl(),
            durationSeconds = renderer.durationSeconds(),
        )
    }

    private fun parsePlaylistItem(renderer: JsonObject, officialAudioOnly: Boolean): Song? {
        if (renderer.has("unplayableText")) return null
        val videoType = renderer.firstStringNamed("musicVideoType")
        if (!isAcceptedVideoType(videoType, officialAudioOnly)) return null

        val videoId = renderer.string("videoId")?.trim().orEmpty()
        val title = renderer.titleText()?.trim().orEmpty()
        if (videoId.isBlank() || title.isBlank()) return null

        val bylineRuns = renderer.objectValue("longBylineText")?.array("runs")
            .orEmpty().mapNotNull { it.takeIf(JsonElement::isJsonObject)?.asJsonObject }
        val artist = bylineRuns.artistNames().ifEmpty {
            bylineRuns.mapNotNull { it.text() }
                .firstOrNull(::isUsableArtistFallback)
                ?.let(::listOf)
                .orEmpty()
        }.joinToString(", ").ifBlank { "YouTube Music" }

        return Song(
            id = videoId,
            title = title,
            artist = artist,
            thumbnailUrl = renderer.bestThumbnailUrl(),
            durationSeconds = renderer.durationSeconds(),
        )
    }

    private fun isAcceptedVideoType(videoType: String?, officialAudioOnly: Boolean): Boolean {
        if (videoType == "MUSIC_VIDEO_TYPE_UGC" ||
            videoType == "MUSIC_VIDEO_TYPE_PODCAST_EPISODE"
        ) return false
        return !officialAudioOnly || videoType == "MUSIC_VIDEO_TYPE_ATV"
    }

    private fun List<JsonObject>.artistNames(): List<String> =
        mapNotNull { run ->
            val endpoint = run.objectValue("navigationEndpoint")?.objectValue("browseEndpoint")
            val browseId = endpoint?.string("browseId")
            val pageType = endpoint?.firstStringNamed("pageType")
            val isArtist = pageType == "MUSIC_PAGE_TYPE_ARTIST" ||
                pageType == "MUSIC_PAGE_TYPE_USER_CHANNEL" ||
                browseId?.startsWith("UC") == true
            run.text()?.trim()?.takeIf { isArtist && it.isNotBlank() }
        }.distinct()

    private fun isUsableArtistFallback(text: String): Boolean {
        val value = text.trim()
        return value.isNotBlank() &&
            value != "•" && value != "·" &&
            !value.matches(DURATION_REGEX) &&
            value.lowercase(Locale.ROOT) !in NON_ARTIST_LABELS
    }

    private fun JsonObject.durationSeconds(): Long {
        val duration = firstStringNamed("lengthText")
            ?: allStrings().firstOrNull { it.matches(DURATION_REGEX) }
            ?: return 0L
        val parts = duration.split(':').mapNotNull(String::toLongOrNull)
        return when (parts.size) {
            2 -> parts[0] * 60 + parts[1]
            3 -> parts[0] * 3600 + parts[1] * 60 + parts[2]
            else -> 0L
        }
    }

    private fun JsonObject.bestThumbnailUrl(): String =
        arraysNamed("thumbnails")
            .flatMap { thumbnails -> thumbnails.mapNotNull(::thumbnailCandidate) }
            .maxByOrNull { it.second }
            ?.first.orEmpty()

    private fun thumbnailCandidate(element: JsonElement): Pair<String, Int>? {
        if (!element.isJsonObject) return null
        val thumbnail = element.asJsonObject
        val url = thumbnail.string("url")?.takeIf { it.startsWith("https://") } ?: return null
        val area = (thumbnail.int("width") ?: 0) * (thumbnail.int("height") ?: 0)
        return url to area
    }

    private fun List<Song>.deduplicated(): List<Song> {
        val ids = hashSetOf<String>()
        val recordings = hashSetOf<String>()
        return filter { song ->
            val recordingKey = listOf(
                song.title.recordingKey(),
                song.artist.recordingKey(),
                if (song.durationSeconds > 0) (song.durationSeconds / 3).toString() else "unknown",
            ).joinToString("|")
            ids.add(song.id) && recordings.add(recordingKey)
        }
    }

    private fun String.recordingKey(): String = Normalizer.normalize(this, Normalizer.Form.NFD)
        .replace(DIACRITICS_REGEX, "")
        .replace(EDITION_SUFFIX_REGEX, "")
        .lowercase(Locale.ROOT)
        .replace(NON_WORD_REGEX, " ")
        .trim()
        .replace(WHITESPACE_REGEX, " ")

    private fun JsonObject.titleText(): String? = objectValue("title")
        ?.array("runs")?.getOrNull(0)?.asJsonObject?.text()

    private fun JsonObject.columnRuns(): List<JsonObject> =
        objectValue("musicResponsiveListItemFlexColumnRenderer")
            ?.objectValue("text")?.array("runs")
            .orEmpty().mapNotNull { it.takeIf(JsonElement::isJsonObject)?.asJsonObject }

    private fun JsonObject.text(): String? = string("text")

    private fun JsonObject.firstStringNamed(name: String): String? =
        objectsAndValuesNamed(name)
            .mapNotNull { it.takeIf(JsonElement::isJsonPrimitive)?.asString }
            .firstOrNull()

    private fun JsonObject.allStrings(): Sequence<String> = sequence {
        for ((_, value) in entrySet()) {
            when {
                value.isJsonPrimitive && value.asJsonPrimitive.isString -> yield(value.asString)
                value.isJsonObject -> yieldAll(value.asJsonObject.allStrings())
                value.isJsonArray -> value.asJsonArray.forEach { child ->
                    if (child.isJsonObject) yieldAll(child.asJsonObject.allStrings())
                }
            }
        }
    }

    private fun JsonObject.objectsNamed(name: String): Sequence<JsonObject> = sequence {
        for ((key, value) in entrySet()) {
            if (key == name && value.isJsonObject) yield(value.asJsonObject)
            when {
                value.isJsonObject -> yieldAll(value.asJsonObject.objectsNamed(name))
                value.isJsonArray -> value.asJsonArray.forEach { child ->
                    if (child.isJsonObject) yieldAll(child.asJsonObject.objectsNamed(name))
                }
            }
        }
    }

    private fun JsonObject.arraysNamed(name: String): Sequence<JsonArray> = sequence {
        for ((key, value) in entrySet()) {
            if (key == name && value.isJsonArray) yield(value.asJsonArray)
            when {
                value.isJsonObject -> yieldAll(value.asJsonObject.arraysNamed(name))
                value.isJsonArray -> value.asJsonArray.forEach { child ->
                    if (child.isJsonObject) yieldAll(child.asJsonObject.arraysNamed(name))
                }
            }
        }
    }

    private fun JsonObject.objectsAndValuesNamed(name: String): Sequence<JsonElement> = sequence {
        for ((key, value) in entrySet()) {
            if (key == name) yield(value)
            when {
                value.isJsonObject -> yieldAll(value.asJsonObject.objectsAndValuesNamed(name))
                value.isJsonArray -> value.asJsonArray.forEach { child ->
                    if (child.isJsonObject) yieldAll(child.asJsonObject.objectsAndValuesNamed(name))
                }
            }
        }
    }

    private fun JsonObject.string(name: String): String? =
        get(name)?.takeIf { it.isJsonPrimitive }?.asString

    private fun JsonObject.int(name: String): Int? =
        get(name)?.takeIf { it.isJsonPrimitive }?.asInt

    private fun JsonObject.objectValue(name: String): JsonObject? =
        get(name)?.takeIf(JsonElement::isJsonObject)?.asJsonObject

    private fun JsonObject.array(name: String): JsonArray? =
        get(name)?.takeIf(JsonElement::isJsonArray)?.asJsonArray

    private fun JsonArray?.orEmpty(): List<JsonElement> =
        this?.toList().orEmpty()

    private fun JsonArray.getOrNull(index: Int): JsonElement? =
        if (index in 0 until size()) get(index) else null

    companion object {
        private const val RESPONSIVE_ITEM = "musicResponsiveListItemRenderer"
        private const val PLAYLIST_ITEM = "playlistPanelVideoRenderer"
        private const val TWO_ROW_ITEM = "musicTwoRowItemRenderer"
        private val DURATION_REGEX = Regex("""\d{1,2}:\d{2}(?::\d{2})?""")
        private val DIACRITICS_REGEX = Regex("\\p{M}+")
        private val EDITION_SUFFIX_REGEX = Regex(
            """(?i)(?:\([^)]*(?:official|audio|video|lyrics?|visuali[sz]er)[^)]*\)|\[[^\]]*(?:official|audio|video|lyrics?|visuali[sz]er)[^\]]*\])"""
        )
        private val NON_WORD_REGEX = Regex("""[^\p{L}\p{N}]+""")
        private val WHITESPACE_REGEX = Regex("""\s+""")
        private val NON_ARTIST_LABELS = setOf(
            "song", "songs", "canción", "canciones", "album", "álbum", "single", "ep"
        )
    }
}
