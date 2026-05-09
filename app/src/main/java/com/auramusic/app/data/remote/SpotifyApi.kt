package com.auramusic.app.data.remote

import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query
import retrofit2.http.Url

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
     * Tracks de una playlist. Spotify devuelve máx 100 por página.
     * Solo pedimos los campos necesarios para el matching → reduce payload.
     */
    @GET("playlists/{id}/tracks")
    suspend fun getPlaylistTracks(
        @Path("id")      playlistId: String,
        @Query("limit")  limit: Int = 100,
        @Query("offset") offset: Int = 0,
        @Query("fields") fields: String = "next,items(track(id,name,duration_ms,artists(name),album(name,images),is_local))"
    ): SpotifyPagedTracksDto

    /**
     * Soporte para paginación dinámica: cuando Spotify devuelve una URL absoluta en `next`,
     * hacemos el fetch directamente a esa URL en vez de construirla a mano.
     */
    @GET
    suspend fun getNextPage(@Url url: String): SpotifyPagedTracksDto
}
