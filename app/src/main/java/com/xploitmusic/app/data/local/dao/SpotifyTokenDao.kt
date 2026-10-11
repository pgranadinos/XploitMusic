package com.xploitmusic.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.xploitmusic.app.data.local.entity.SpotifyTokenEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface SpotifyTokenDao {

    /** Observa el estado de autenticación. Emite null si no hay sesión activa. */
    @Query("SELECT * FROM spotify_token WHERE id = 1 LIMIT 1")
    fun observeToken(): Flow<SpotifyTokenEntity?>

    /** Obtiene el token sincrónicamente para operaciones de red */
    @Query("SELECT * FROM spotify_token WHERE id = 1 LIMIT 1")
    suspend fun getToken(): SpotifyTokenEntity?

    /** Inserta o reemplaza el token (PKCE exchange o refresh) */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertToken(token: SpotifyTokenEntity)

    /** Eliminar sesión (logout) */
    @Query("DELETE FROM spotify_token")
    suspend fun deleteToken()
}
