package com.auramusic.app.data.repository

import android.content.ContentResolver
import android.content.ContentUris
import android.provider.MediaStore
import android.util.Log
import com.auramusic.app.domain.model.Song
import com.auramusic.app.domain.repository.LocalAudioRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject

class LocalAudioRepositoryImpl @Inject constructor(
    private val contentResolver: ContentResolver
) : LocalAudioRepository {

    companion object {
        private const val TAG = "XploitDebug"
    }

    override suspend fun getLocalSongs(): List<Song> = withContext(Dispatchers.IO) {
        val songs = mutableListOf<Song>()

        val projection = arrayOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.TITLE,
            MediaStore.Audio.Media.ARTIST,
            MediaStore.Audio.Media.DURATION,
            MediaStore.Audio.Media.ALBUM_ID
        )

        // Only include music files (not ringtones, alarms, etc.)
        val selection = "${MediaStore.Audio.Media.IS_MUSIC} != 0"
        val sortOrder = "${MediaStore.Audio.Media.DATE_ADDED} DESC"

        try {
            contentResolver.query(
                MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                projection,
                selection,
                null,
                sortOrder
            )?.use { cursor ->
                val idCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
                val titleCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
                val artistCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
                val durationCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)
                val albumIdCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM_ID)

                while (cursor.moveToNext()) {
                    val id = cursor.getLong(idCol)
                    val title = cursor.getString(titleCol) ?: "Unknown"
                    val artist = cursor.getString(artistCol) ?: "Unknown"
                    val durationMs = cursor.getLong(durationCol)
                    val albumId = cursor.getLong(albumIdCol)

                    // Skip very short audio clips (< 30s)
                    if (durationMs < 30_000) continue

                    // Build content URI that ExoPlayer can play natively
                    val contentUri = ContentUris.withAppendedId(
                        MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, id
                    ).toString()

                    // Build album art URI
                    val albumArtUri = ContentUris.withAppendedId(
                        "content://media/external/audio/albumart".toAndroidUri(),
                        albumId
                    ).toString()

                    songs.add(
                        Song(
                            id = "local_$id",
                            title = title,
                            artist = if (artist == "<unknown>") "Unknown Artist" else artist,
                            thumbnailUrl = albumArtUri,
                            durationSeconds = durationMs / 1000,
                            streamUrl = contentUri
                        )
                    )
                }
            }
            Log.d(TAG, "LocalAudio: Found ${songs.size} local songs")
        } catch (e: Exception) {
            Log.e(TAG, "LocalAudio: Error querying MediaStore: ${e.message}", e)
        }

        songs
    }

    private fun String.toAndroidUri(): android.net.Uri = android.net.Uri.parse(this)
}
