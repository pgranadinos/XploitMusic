package com.auramusic.app.presentation.home;

@kotlin.Metadata(mv = {1, 9, 0}, k = 2, xi = 48, d1 = {"\u0000B\n\u0000\n\u0002\u0010\u0002\n\u0000\n\u0002\u0018\u0002\n\u0002\b\u0002\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0002\u0010 \n\u0000\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0002\b\u0002\n\u0002\u0018\u0002\n\u0002\b\u0002\n\u0002\u0010\u000e\n\u0002\b\u0003\n\u0002\u0018\u0002\n\u0000\u001a\u0010\u0010\u0000\u001a\u00020\u00012\u0006\u0010\u0002\u001a\u00020\u0003H\u0003\u001aF\u0010\u0004\u001a\u00020\u00012\u001e\u0010\u0005\u001a\u001a\u0012\u0004\u0012\u00020\u0007\u0012\n\u0012\b\u0012\u0004\u0012\u00020\u00070\b\u0012\u0004\u0012\u00020\u00010\u00062\u0012\u0010\t\u001a\u000e\u0012\u0004\u0012\u00020\u0007\u0012\u0004\u0012\u00020\u00010\n2\b\b\u0002\u0010\u000b\u001a\u00020\fH\u0007\u001a\u0010\u0010\r\u001a\u00020\u00012\u0006\u0010\u000e\u001a\u00020\u000fH\u0003\u001a\u0010\u0010\u0010\u001a\u00020\u00012\u0006\u0010\u0011\u001a\u00020\u0012H\u0003\u001a\u001e\u0010\u0013\u001a\u00020\u00012\u0006\u0010\u0014\u001a\u00020\u00072\f\u0010\u0015\u001a\b\u0012\u0004\u0012\u00020\u00010\u0016H\u0007\u00a8\u0006\u0017"}, d2 = {"ChannelListItem", "", "channel", "Lcom/auramusic/app/domain/model/SearchResultItem$ChannelResult;", "HomeScreen", "onSongClick", "Lkotlin/Function2;", "Lcom/auramusic/app/domain/model/Song;", "", "onSearchSongClick", "Lkotlin/Function1;", "viewModel", "Lcom/auramusic/app/presentation/home/HomeViewModel;", "PlaylistListItem", "playlist", "Lcom/auramusic/app/domain/model/SearchResultItem$PlaylistResult;", "SectionHeader", "title", "", "SongCard", "song", "onClick", "Lkotlin/Function0;", "app_debug"})
public final class HomeScreenKt {
    
    @kotlin.OptIn(markerClass = {androidx.compose.material3.ExperimentalMaterial3Api.class})
    @androidx.compose.runtime.Composable()
    public static final void HomeScreen(@org.jetbrains.annotations.NotNull()
    kotlin.jvm.functions.Function2<? super com.auramusic.app.domain.model.Song, ? super java.util.List<com.auramusic.app.domain.model.Song>, kotlin.Unit> onSongClick, @org.jetbrains.annotations.NotNull()
    kotlin.jvm.functions.Function1<? super com.auramusic.app.domain.model.Song, kotlin.Unit> onSearchSongClick, @org.jetbrains.annotations.NotNull()
    com.auramusic.app.presentation.home.HomeViewModel viewModel) {
    }
    
    @androidx.compose.runtime.Composable()
    private static final void SectionHeader(java.lang.String title) {
    }
    
    @androidx.compose.runtime.Composable()
    public static final void SongCard(@org.jetbrains.annotations.NotNull()
    com.auramusic.app.domain.model.Song song, @org.jetbrains.annotations.NotNull()
    kotlin.jvm.functions.Function0<kotlin.Unit> onClick) {
    }
    
    @androidx.compose.runtime.Composable()
    private static final void ChannelListItem(com.auramusic.app.domain.model.SearchResultItem.ChannelResult channel) {
    }
    
    @androidx.compose.runtime.Composable()
    private static final void PlaylistListItem(com.auramusic.app.domain.model.SearchResultItem.PlaylistResult playlist) {
    }
}