package com.auramusic.app.data.remote

import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query

interface SpotifyApi {

    /** Perfil del usuario autenticado */
    @GET("me")
    suspend fun getProfile(): SpotifyUserProfileDto

    /**
     * Playlists del usuario. Spotify devuelve máx 50 por página.
     * [offset] se usa para paginar.
     */
    @GET("me/playlists")
    suspend fun getPlaylists(
        @Query("limit")  limit: Int = 50,
        @Query("offset") offset: Int = 0
    ): SpotifyPagedPlaylistsDto

    /**
     * Items de una playlist. Spotify devuelve máx 50 por página.
     * Solo pedimos los campos necesarios para el matching → reduce payload.
     */
    @GET("playlists/{id}/items")
    suspend fun getPlaylistItems(
        @Path("id")      playlistId: String,
        @Query("limit")  limit: Int = 50,
        @Query("offset") offset: Int = 0,
        @Query("fields") fields: String = "next,items(is_local,item(id,type,is_playable,name,duration_ms,artists(name),album(name,images)))"
    ): SpotifyPagedTracksDto
}
