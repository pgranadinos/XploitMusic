package com.auramusic.app.data.remote

import retrofit2.http.Field
import retrofit2.http.FormUrlEncoded
import retrofit2.http.POST

/**
 * Token endpoint de Spotify. Base URL: https://accounts.spotify.com/
 *
 * Usamos PKCE (Proof Key for Code Exchange) — no hay client_secret para apps móviles.
 */
interface SpotifyAuthApi {

    /**
     * Intercambia el authorization code por access_token + refresh_token.
     * Se llama una sola vez después del login OAuth.
     */
    @FormUrlEncoded
    @POST("api/token")
    suspend fun exchangeCode(
        @Field("grant_type")    grantType: String = "authorization_code",
        @Field("code")          code: String,
        @Field("redirect_uri")  redirectUri: String,
        @Field("client_id")     clientId: String,
        @Field("code_verifier") codeVerifier: String
    ): SpotifyTokenResponse

    /**
     * Refresca el access_token cuando está próximo a expirar.
     * Solo requiere el refresh_token y el client_id (sin secret).
     */
    @FormUrlEncoded
    @POST("api/token")
    suspend fun refreshToken(
        @Field("grant_type")    grantType: String = "refresh_token",
        @Field("refresh_token") refreshToken: String,
        @Field("client_id")     clientId: String
    ): SpotifyTokenResponse
}
