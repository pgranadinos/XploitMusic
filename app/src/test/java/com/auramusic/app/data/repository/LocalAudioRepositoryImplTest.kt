package com.auramusic.app.data.repository

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class LocalAudioRepositoryImplTest {

    @Test
    fun `folder query escapes wildcards instead of including unrelated folders`() {
        assertThat(LocalAudioRepositoryImpl.folderLikePattern("Music/Rock_100%!/"))
            .isEqualTo("Music/Rock!_100!%!!/%")
        assertThat(LocalAudioRepositoryImpl.folderLikePattern("Music/"))
            .isEqualTo("Music/%")
    }

    @Test
    fun `extracts a MediaStore relative path from selected tree`() {
        assertThat(
            LocalAudioRepositoryImpl.relativePathFromDocumentId("primary:Music/Rock")
        ).isEqualTo("Music/Rock/")
    }

    @Test
    fun `uses selected directory as its visible name`() {
        assertThat(
            LocalAudioRepositoryImpl.folderNameFromDocumentId("primary:Music/Rock")
        ).isEqualTo("Rock")
        assertThat(
            LocalAudioRepositoryImpl.folderNameFromDocumentId("primary:")
        ).isEqualTo("Almacenamiento interno")
    }

    @Test
    fun `rejects document ids without a storage separator`() {
        assertThat(
            LocalAudioRepositoryImpl.relativePathFromDocumentId("invalid")
        ).isNull()
    }
}
