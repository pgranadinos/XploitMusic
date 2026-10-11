package com.auramusic.app.presentation.home

import com.auramusic.app.domain.model.Song
import com.auramusic.app.domain.model.LatamCharts

// ----------------------------------------------------
// UI Models for the Home Redesign
// ----------------------------------------------------

data class QuickPickItem(
    val id: String,
    val title: String,
    val subtitle: String,
    val imageUrl: String,
    val isPlaylist: Boolean = false,
    val song: Song? = null
)

data class HistoryContext(
    val id: String,
    val title: String,
    val description: String,
    val imageUrl: String,
    val playedAt: Long,
    val type: String // "ALBUM", "PLAYLIST", "RADIO", "ARTIST"
)

data class UiPlaylist(
    val id: String,
    val title: String,
    val description: String,
    val imageUrl: String,
    val seedArtist: String
)

// ----------------------------------------------------
// UI State Wrappers
// ----------------------------------------------------

sealed class SectionState<out T> {
    object Loading : SectionState<Nothing>()
    data class Success<T>(val data: T) : SectionState<T>()
    object Empty : SectionState<Nothing>()
    data class Error(val message: String) : SectionState<Nothing>()
}

data class HomeUiState(
    val quickPicks: SectionState<List<QuickPickItem>> = SectionState.Loading,
    val jumpBackIn: SectionState<List<HistoryContext>> = SectionState.Loading,
    val artistMixes: SectionState<List<UiPlaylist>> = SectionState.Loading,
    val latamCharts: SectionState<LatamCharts> = SectionState.Loading,
    val recommendations: SectionState<List<Song>> = SectionState.Loading,
    val mostPlayed: SectionState<List<Song>> = SectionState.Loading,
    val recents: SectionState<List<Song>> = SectionState.Loading
)

sealed class HomeAction {
    object RefreshAll : HomeAction()
    object RetryQuickPicks : HomeAction()
    object RetryJumpBackIn : HomeAction()
    object RetryArtistMixes : HomeAction()
    object RetryLatamCharts : HomeAction()
    object RetryRecommendations : HomeAction()
}
