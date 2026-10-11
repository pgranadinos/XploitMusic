package com.auramusic.app.data.provider.youtubemusic

import com.auramusic.app.domain.model.Song
import com.auramusic.app.domain.model.CountryChart
import com.auramusic.app.domain.model.LatamCountry
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import javax.inject.Inject
import javax.inject.Singleton

/** Anonymous access to the public YouTube Music catalogue; playback stays in NewPipe/Media3. */
@Singleton
class YouTubeMusicClient @Inject constructor(
    okHttpClient: OkHttpClient,
) {
    private val client = okHttpClient.newBuilder().build()
    private val parser = YouTubeMusicParser()
    private val visitorMutex = Mutex()
    @Volatile private var visitorData: String? = null
    @Volatile private var visitorLookupFinished = false

    suspend fun searchSongs(query: String): List<Song> {
        val body = JsonObject().apply {
            addProperty("query", query)
            addProperty("params", SONG_SEARCH_PARAMS)
        }
        return parser.parseSearchSongs(post("search", body, DEFAULT_REGION))
    }

    suspend fun getTrack(videoId: String): Song =
        watchQueue(videoId).firstOrNull { it.id == videoId }
            ?: throw NoSuchElementException("YouTube Music did not return track $videoId")

    suspend fun getRelated(videoId: String): List<Song> =
        watchQueue(videoId).filterNot { it.id == videoId }

    suspend fun getTrending(region: String): List<Song> {
        val normalizedRegion = region.trim().uppercase(Locale.ROOT).takeIf { it.length == 2 }
            ?: DEFAULT_REGION
        val chartsBody = JsonObject().apply {
            addProperty("browseId", "FEmusic_charts")
            add("formData", JsonObject().apply {
                add("selectedValues", com.google.gson.JsonArray().apply { add(normalizedRegion) })
            })
        }
        val charts = post("browse", chartsBody, normalizedRegion)
        val playlistBrowseId = parser.findChartPlaylistBrowseId(charts) ?: return emptyList()
        val playlistBody = JsonObject().apply { addProperty("browseId", playlistBrowseId) }
        return parser.parseChartSongs(post("browse", playlistBody, normalizedRegion))
    }

    suspend fun getCountryChart(country: LatamCountry): CountryChart {
        val body = JsonObject().apply {
            addProperty("browseId", "FEmusic_charts")
            add("formData", JsonObject().apply {
                add("selectedValues", com.google.gson.JsonArray().apply { add(country.name) })
            })
        }
        val playlist = parser.findCountryChartPlaylist(post("browse", body, country.name), country)
            ?: throw IOException("No se encontró el top diario de ${country.label}")
        val playlistBody = JsonObject().apply { addProperty("browseId", playlist.browseId) }
        val entries = parser.parseRankedChartEntries(post("browse", playlistBody, country.name))
        if (entries.isEmpty()) throw IOException("El ranking no contiene posiciones disponibles")
        return CountryChart(
            country = country,
            sourceTitle = playlist.title,
            playlistId = playlist.browseId.removePrefix("VL"),
            entries = entries,
            fetchedAtMs = System.currentTimeMillis(),
        )
    }

    private suspend fun watchQueue(videoId: String): List<Song> {
        val body = JsonObject().apply {
            addProperty("enablePersistentPlaylistPanel", true)
            addProperty("isAudioOnly", true)
            addProperty("tunerSettingValue", "AUTOMIX_SETTING_NORMAL")
            addProperty("videoId", videoId)
            addProperty("playlistId", "RDAMVM$videoId")
            add("watchEndpointMusicSupportedConfigs", JsonObject().apply {
                add("watchEndpointMusicConfig", JsonObject().apply {
                    addProperty("hasPersistentPlaylistPanel", true)
                    addProperty("musicVideoType", "MUSIC_VIDEO_TYPE_ATV")
                })
            })
        }
        return parser.parseWatchQueue(post("next", body, DEFAULT_REGION))
    }

    private suspend fun post(endpoint: String, body: JsonObject, region: String): JsonObject =
        withContext(Dispatchers.IO) {
            val clientVersion = currentClientVersion()
            val payload = body.deepCopy().apply {
                add("context", requestContext(clientVersion, region))
            }
            val requestBuilder = Request.Builder()
                .url("$BASE_API$endpoint?alt=json")
                .header("Accept", "*/*")
                .header("Accept-Language", "es-PE,es;q=0.9")
                .header("Content-Type", JSON_MEDIA_TYPE.toString())
                .header("Origin", BASE_URL)
                .header("Referer", "$BASE_URL/")
                .header("User-Agent", USER_AGENT)
                .header("Cookie", "SOCS=CAI")
                .header("X-Youtube-Client-Version", clientVersion)
            getVisitorData()?.takeIf(String::isNotBlank)?.let {
                requestBuilder.header("X-Goog-Visitor-Id", it)
            }
            val request = requestBuilder
                .post(payload.toString().toRequestBody(JSON_MEDIA_TYPE))
                .build()

            client.newCall(request).execute().use { response ->
                val responseText = response.body?.string().orEmpty()
                if (!response.isSuccessful) {
                    throw IOException("YouTube Music HTTP ${response.code}")
                }
                val parsed = runCatching { JsonParser().parse(responseText) }.getOrElse {
                    throw IOException("YouTube Music returned invalid JSON", it)
                }
                if (!parsed.isJsonObject) throw IOException("YouTube Music returned an invalid payload")
                parsed.asJsonObject
            }
        }

    private suspend fun getVisitorData(): String? {
        if (visitorLookupFinished) return visitorData
        return visitorMutex.withLock {
            if (visitorLookupFinished) return@withLock visitorData
            visitorData = runCatching {
                withContext(Dispatchers.IO) {
                    val request = Request.Builder()
                        .url(BASE_URL)
                        .header("User-Agent", USER_AGENT)
                        .header("Cookie", "SOCS=CAI")
                        .build()
                    client.newCall(request).execute().use { response ->
                        if (!response.isSuccessful) return@use null
                        val html = response.body?.string().orEmpty()
                        VISITOR_DATA_REGEX.find(html)?.groupValues?.getOrNull(1)
                    }
                }
            }.getOrNull()
            visitorLookupFinished = true
            visitorData
        }
    }

    private fun requestContext(clientVersion: String, region: String): JsonObject = JsonObject().apply {
        add("client", JsonObject().apply {
            addProperty("clientName", "WEB_REMIX")
            addProperty("clientVersion", clientVersion)
            addProperty("hl", "es")
            addProperty("gl", region)
        })
        add("user", JsonObject())
    }

    private fun currentClientVersion(): String {
        val formatter = SimpleDateFormat("yyyyMMdd", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }
        return "1.${formatter.format(Date())}.01.00"
    }

    companion object {
        private const val BASE_URL = "https://music.youtube.com"
        private const val BASE_API = "$BASE_URL/youtubei/v1/"
        private const val DEFAULT_REGION = "PE"
        private const val SONG_SEARCH_PARAMS = "EgWKAQIIAWoMEA4QChADEAQQCRAF"
        private const val USER_AGENT =
            "Mozilla/5.0 (Linux; Android 13) AppleWebKit/537.36 " +
                "(KHTML, like Gecko) Chrome/120.0 Mobile Safari/537.36"
        private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
        private val VISITOR_DATA_REGEX = Regex("""\\?"VISITOR_DATA\\?"\s*:\s*\\?"([^"\\]+)""")
    }
}
