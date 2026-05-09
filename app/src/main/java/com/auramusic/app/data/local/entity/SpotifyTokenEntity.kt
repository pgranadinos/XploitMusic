package com.auramusic.app.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Almacena el token de sesión de Spotify en Room (singleton, id=1 siempre).
 *
 * El access_token expira en 3600s. El refresh_token es de larga duración y
 * se usa para renovar el access_token sin que el usuario tenga que loguearse de nuevo.
 *
 * NEVER log these values.
 */
@Entity(tableName = "spotify_token")
data class SpotifyTokenEntity(
    @PrimaryKey
    val id: Int = 1,

    @ColumnInfo(name = "access_token")
    val accessToken: String,

    @ColumnInfo(name = "refresh_token")
    val refreshToken: String,

    /** Epoch millis en que el access_token expira */
    @ColumnInfo(name = "expires_at")
    val expiresAt: Long,

    /** ID del usuario de Spotify, útil para display */
    @ColumnInfo(name = "spotify_user_id")
    val spotifyUserId: String = "",

    @ColumnInfo(name = "display_name")
    val displayName: String = "",

    @ColumnInfo(name = "avatar_url")
    val avatarUrl: String = ""
)
