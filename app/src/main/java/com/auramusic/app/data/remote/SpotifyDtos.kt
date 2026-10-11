package com.auramusic.app.data.remote

import com.google.gson.annotations.SerializedName

// ─── Auth ────────────────────────────────────────────────────────────────────

data class SpotifyTokenResponse(
    @SerializedName("access_token")  val accessToken: String,
    @SerializedName("refresh_token") val refreshToken: String?,
    @SerializedName("expires_in")    val expiresIn: Int   // seconds
)

// ─── User ─────────────────────────────────────────────────────────────────────

data class SpotifyUserProfileDto(
    @SerializedName("id")           val id: String,
    @SerializedName("display_name") val displayName: String?,
    @SerializedName("email")        val email: String?,
    @SerializedName("images")       val images: List<SpotifyImageDto>?
)

data class SpotifyImageDto(
    @SerializedName("url")    val url: String,
    @SerializedName("width")  val width: Int?,
    @SerializedName("height") val height: Int?
)

// ─── Playlists ─────────────────────────────────────────────────────────────────

data class SpotifyPagedPlaylistsDto(
    @SerializedName("items")  val items: List<SpotifyPlaylistDto>,
    @SerializedName("next")   val next: String?,
    @SerializedName("total")  val total: Int
)

data class SpotifyPlaylistDto(
    @SerializedName("id")          val id: String,
    @SerializedName("name")        val name: String,
    @SerializedName("description") val description: String?,
    @SerializedName("images")      val images: List<SpotifyImageDto>?,
    @SerializedName("items")       val itemsSummary: SpotifyTracksSummaryDto? = null,
    @SerializedName("tracks")      val tracks: SpotifyTracksSummaryDto? = null
) {
    /** `items` reemplazó a `tracks`; se conserva el fallback durante la migración. */
    val totalItems: Int
        get() = itemsSummary?.total ?: tracks?.total ?: 0
}

data class SpotifyTracksSummaryDto(
    @SerializedName("total") val total: Int
)

// ─── Tracks ────────────────────────────────────────────────────────────────────

data class SpotifyPagedTracksDto(
    @SerializedName("items") val items: List<SpotifyPlaylistTrackDto>,
    @SerializedName("next")  val next: String?
)

data class SpotifyPlaylistTrackDto(
    @SerializedName("item")     val item: SpotifyTrackDto? = null,
    @SerializedName("is_local") val isLocal: Boolean = false,
    @SerializedName("track")    val legacyTrack: SpotifyTrackDto? = null
) {
    /** `track` es el nombre anterior del campo y solo se usa como fallback. */
    val resolvedItem: SpotifyTrackDto?
        get() = item ?: legacyTrack
}

data class SpotifyTrackDto(
    @SerializedName("id")           val id: String?,
    @SerializedName("type")         val type: String? = null,
    @SerializedName("is_playable")  val isPlayable: Boolean? = null,
    @SerializedName("name")         val name: String,
    @SerializedName("duration_ms")  val durationMs: Long,
    @SerializedName("artists")      val artists: List<SpotifyArtistDto>,
    @SerializedName("album")        val album: SpotifyAlbumDto,
    @SerializedName("is_local")     val isLocal: Boolean = false
)

data class SpotifyArtistDto(
    @SerializedName("id")   val id: String?,
    @SerializedName("name") val name: String
)

data class SpotifyAlbumDto(
    @SerializedName("name")   val name: String,
    @SerializedName("images") val images: List<SpotifyImageDto>?
)
