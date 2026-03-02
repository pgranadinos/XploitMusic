package com.auramusic.app.presentation.library;

@kotlin.Metadata(mv = {1, 9, 0}, k = 2, xi = 48, d1 = {"\u0000>\n\u0000\n\u0002\u0010\u0002\n\u0000\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0002\u0010\u000e\n\u0002\b\u0002\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0002\u0010 \n\u0000\n\u0002\u0018\u0002\n\u0002\b\u0002\n\u0002\u0018\u0002\n\u0002\b\u0004\n\u0002\u0010\t\n\u0000\u001a*\u0010\u0000\u001a\u00020\u00012\f\u0010\u0002\u001a\b\u0012\u0004\u0012\u00020\u00010\u00032\u0012\u0010\u0004\u001a\u000e\u0012\u0004\u0012\u00020\u0006\u0012\u0004\u0012\u00020\u00010\u0005H\u0003\u001a2\u0010\u0007\u001a\u00020\u00012\u001e\u0010\b\u001a\u001a\u0012\u0004\u0012\u00020\n\u0012\n\u0012\b\u0012\u0004\u0012\u00020\n0\u000b\u0012\u0004\u0012\u00020\u00010\t2\b\b\u0002\u0010\f\u001a\u00020\rH\u0007\u001a,\u0010\u000e\u001a\u00020\u00012\u0006\u0010\u000f\u001a\u00020\u00102\f\u0010\u0011\u001a\b\u0012\u0004\u0012\u00020\u00010\u00032\f\u0010\u0012\u001a\b\u0012\u0004\u0012\u00020\u00010\u0003H\u0003\u001a\u0010\u0010\u0013\u001a\u00020\u00062\u0006\u0010\u0014\u001a\u00020\u0015H\u0002\u00a8\u0006\u0016"}, d2 = {"CreatePlaylistDialog", "", "onDismiss", "Lkotlin/Function0;", "onCreate", "Lkotlin/Function1;", "", "LibraryScreen", "onSongClick", "Lkotlin/Function2;", "Lcom/auramusic/app/domain/model/Song;", "", "viewModel", "Lcom/auramusic/app/presentation/library/LibraryViewModel;", "PlaylistItem", "playlist", "Lcom/auramusic/app/data/local/entity/PlaylistEntity;", "onClick", "onDelete", "formatDate", "timestamp", "", "app_debug"})
public final class LibraryScreenKt {
    
    @kotlin.OptIn(markerClass = {androidx.compose.material3.ExperimentalMaterial3Api.class})
    @androidx.compose.runtime.Composable()
    public static final void LibraryScreen(@org.jetbrains.annotations.NotNull()
    kotlin.jvm.functions.Function2<? super com.auramusic.app.domain.model.Song, ? super java.util.List<com.auramusic.app.domain.model.Song>, kotlin.Unit> onSongClick, @org.jetbrains.annotations.NotNull()
    com.auramusic.app.presentation.library.LibraryViewModel viewModel) {
    }
    
    @androidx.compose.runtime.Composable()
    private static final void PlaylistItem(com.auramusic.app.data.local.entity.PlaylistEntity playlist, kotlin.jvm.functions.Function0<kotlin.Unit> onClick, kotlin.jvm.functions.Function0<kotlin.Unit> onDelete) {
    }
    
    @androidx.compose.runtime.Composable()
    private static final void CreatePlaylistDialog(kotlin.jvm.functions.Function0<kotlin.Unit> onDismiss, kotlin.jvm.functions.Function1<? super java.lang.String, kotlin.Unit> onCreate) {
    }
    
    private static final java.lang.String formatDate(long timestamp) {
        return null;
    }
}