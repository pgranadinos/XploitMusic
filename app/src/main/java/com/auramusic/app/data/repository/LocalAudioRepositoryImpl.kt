package com.auramusic.app.data.repository

import android.content.ContentResolver
import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.DocumentsContract
import android.provider.MediaStore
import android.util.Log
import com.auramusic.app.domain.model.LocalAudioFolder
import com.auramusic.app.domain.model.Song
import com.auramusic.app.domain.repository.LocalAudioRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject

class LocalAudioRepositoryImpl @Inject constructor(
    private val contentResolver: ContentResolver,
    @ApplicationContext private val context: Context
) : LocalAudioRepository {

    companion object {
        private const val TAG = "XploitDebug"
        private const val PREFS_NAME = "local_audio_folders"
        private const val KEY_FOLDER_URIS = "folder_uris"

        internal fun folderLikePattern(path: String): String =
            path.replace("!", "!!").replace("%", "!%").replace("_", "!_") + "%"

        internal fun relativePathFromDocumentId(documentId: String): String? {
            val separator = documentId.indexOf(':')
            if (separator < 0) return null
            val path = documentId.substring(separator + 1).trim('/', '\\')
            return if (path.isEmpty()) "" else "$path/"
        }

        internal fun folderNameFromDocumentId(documentId: String): String {
            val path = relativePathFromDocumentId(documentId).orEmpty().trimEnd('/')
            return path.substringAfterLast('/').ifBlank { "Almacenamiento interno" }
        }
    }

    private val preferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    override fun getSelectedFolders(): List<LocalAudioFolder> = selectedFolderUris()
        .mapNotNull { uriValue ->
            val documentId = documentId(uriValue) ?: return@mapNotNull null
            LocalAudioFolder(uri = uriValue, name = folderNameFromDocumentId(documentId),
                path = documentId.replace("primary:", "Almacenamiento interno / "))
        }
        .sortedBy { it.name.lowercase() }

    override fun addSelectedFolder(uri: String): Boolean {
        if (documentId(uri) == null) return false
        preferences.edit()
            .putStringSet(KEY_FOLDER_URIS, selectedFolderUris() + uri)
            .apply()
        return true
    }

    override fun removeSelectedFolder(uri: String) {
        preferences.edit()
            .putStringSet(KEY_FOLDER_URIS, selectedFolderUris() - uri)
            .apply()
    }

    override suspend fun getLocalSongs(): List<Song> = withContext(Dispatchers.IO) {
        val songs = mutableListOf<Song>()
        val documentIds = selectedFolderUris().mapNotNull(::documentId)
        if (documentIds.isEmpty()) {
            Log.d(TAG, "LocalAudio: No selected folders; skipping device-wide scan")
            return@withContext emptyList()
        }

        val selectedPaths = documentIds.mapNotNull(::relativePathFromDocumentId).distinct()
        if (selectedPaths.isEmpty()) return@withContext emptyList()

        val pathColumn = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            MediaStore.Audio.Media.RELATIVE_PATH
        } else {
            MediaStore.Audio.Media.DATA
        }

        val projection = arrayOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.TITLE,
            MediaStore.Audio.Media.ARTIST,
            MediaStore.Audio.Media.DURATION,
            MediaStore.Audio.Media.ALBUM_ID,
            pathColumn
        )

        val queryPaths = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            selectedPaths
        } else {
            selectedPaths.map { relativePath ->
                val root = Environment.getExternalStorageDirectory().absolutePath.trimEnd('/', '\\')
                if (relativePath.isEmpty()) "$root/" else "$root/$relativePath"
            }
        }
        val pathFilters = queryPaths.joinToString(" OR ") { "$pathColumn LIKE ? ESCAPE '!'" }
        val selection = "${MediaStore.Audio.Media.IS_MUSIC} != 0 AND ($pathFilters)"
        val selectionArgs = queryPaths.map(::folderLikePattern).toTypedArray()
        val sortOrder = "${MediaStore.Audio.Media.DATE_ADDED} DESC"

        try {
            contentResolver.query(
                MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                projection,
                selection,
                selectionArgs,
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

    private fun selectedFolderUris(): Set<String> =
        preferences.getStringSet(KEY_FOLDER_URIS, emptySet()).orEmpty().toSet()

    private fun documentId(uriValue: String): String? = try {
        DocumentsContract.getTreeDocumentId(Uri.parse(uriValue))
    } catch (_: Exception) {
        null
    }

    private fun String.toAndroidUri(): android.net.Uri = android.net.Uri.parse(this)
}
