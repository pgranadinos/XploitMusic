package com.auramusic.app.presentation.library;

@kotlin.Metadata(mv = {1, 9, 0}, k = 1, xi = 48, d1 = {"\u0000T\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0002\b\u0002\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0000\n\u0002\u0010\u000b\n\u0000\n\u0002\u0018\u0002\n\u0002\u0010 \n\u0002\u0018\u0002\n\u0002\b\u0007\n\u0002\u0010\u0002\n\u0000\n\u0002\u0010\t\n\u0000\n\u0002\u0018\u0002\n\u0002\b\u0003\n\u0002\u0010\u000e\n\u0002\b\b\b\u0007\u0018\u0000 &2\u00020\u0001:\u0001&B\u0017\b\u0007\u0012\u0006\u0010\u0002\u001a\u00020\u0003\u0012\u0006\u0010\u0004\u001a\u00020\u0005\u00a2\u0006\u0002\u0010\u0006J\u0016\u0010\u0016\u001a\u00020\u00172\u0006\u0010\u0018\u001a\u00020\u00192\u0006\u0010\u001a\u001a\u00020\u001bJ\u0006\u0010\u001c\u001a\u00020\u0017J\u000e\u0010\u001d\u001a\u00020\u00172\u0006\u0010\u001e\u001a\u00020\u001fJ\u000e\u0010 \u001a\u00020\u00172\u0006\u0010\u0018\u001a\u00020\u0019J\u0006\u0010!\u001a\u00020\u0017J\u0016\u0010\"\u001a\u00020\u00172\u0006\u0010\u0018\u001a\u00020\u00192\u0006\u0010#\u001a\u00020\u001fJ\u000e\u0010$\u001a\u00020\u00172\u0006\u0010\u0018\u001a\u00020\u0019J\u0006\u0010%\u001a\u00020\u0017R\u0016\u0010\u0007\u001a\n\u0012\u0006\u0012\u0004\u0018\u00010\t0\bX\u0082\u0004\u00a2\u0006\u0002\n\u0000R\u0014\u0010\n\u001a\b\u0012\u0004\u0012\u00020\u000b0\bX\u0082\u0004\u00a2\u0006\u0002\n\u0000R\u000e\u0010\u0002\u001a\u00020\u0003X\u0082\u0004\u00a2\u0006\u0002\n\u0000R\u001d\u0010\f\u001a\u000e\u0012\n\u0012\b\u0012\u0004\u0012\u00020\u000f0\u000e0\r\u00a2\u0006\b\n\u0000\u001a\u0004\b\u0010\u0010\u0011R\u0019\u0010\u0012\u001a\n\u0012\u0006\u0012\u0004\u0018\u00010\t0\r\u00a2\u0006\b\n\u0000\u001a\u0004\b\u0013\u0010\u0011R\u0017\u0010\u0014\u001a\b\u0012\u0004\u0012\u00020\u000b0\r\u00a2\u0006\b\n\u0000\u001a\u0004\b\u0015\u0010\u0011R\u000e\u0010\u0004\u001a\u00020\u0005X\u0082\u0004\u00a2\u0006\u0002\n\u0000\u00a8\u0006\'"}, d2 = {"Lcom/auramusic/app/presentation/library/LibraryViewModel;", "Landroidx/lifecycle/ViewModel;", "playlistDao", "Lcom/auramusic/app/data/local/dao/PlaylistDao;", "songDao", "Lcom/auramusic/app/data/local/dao/SongDao;", "(Lcom/auramusic/app/data/local/dao/PlaylistDao;Lcom/auramusic/app/data/local/dao/SongDao;)V", "_selectedPlaylist", "Lkotlinx/coroutines/flow/MutableStateFlow;", "Lcom/auramusic/app/data/local/entity/PlaylistWithSongs;", "_showCreateDialog", "", "playlists", "Lkotlinx/coroutines/flow/StateFlow;", "", "Lcom/auramusic/app/data/local/entity/PlaylistEntity;", "getPlaylists", "()Lkotlinx/coroutines/flow/StateFlow;", "selectedPlaylist", "getSelectedPlaylist", "showCreateDialog", "getShowCreateDialog", "addSongToPlaylist", "", "playlistId", "", "song", "Lcom/auramusic/app/domain/model/Song;", "clearSelection", "createPlaylist", "name", "", "deletePlaylist", "dismissCreatePlaylistDialog", "removeSongFromPlaylist", "songId", "selectPlaylist", "showCreatePlaylistDialog", "Companion", "app_debug"})
@dagger.hilt.android.lifecycle.HiltViewModel()
public final class LibraryViewModel extends androidx.lifecycle.ViewModel {
    @org.jetbrains.annotations.NotNull()
    private final com.auramusic.app.data.local.dao.PlaylistDao playlistDao = null;
    @org.jetbrains.annotations.NotNull()
    private final com.auramusic.app.data.local.dao.SongDao songDao = null;
    @org.jetbrains.annotations.NotNull()
    private static final java.lang.String TAG = "XploitDebug";
    @org.jetbrains.annotations.NotNull()
    private final kotlinx.coroutines.flow.StateFlow<java.util.List<com.auramusic.app.data.local.entity.PlaylistEntity>> playlists = null;
    @org.jetbrains.annotations.NotNull()
    private final kotlinx.coroutines.flow.MutableStateFlow<com.auramusic.app.data.local.entity.PlaylistWithSongs> _selectedPlaylist = null;
    @org.jetbrains.annotations.NotNull()
    private final kotlinx.coroutines.flow.StateFlow<com.auramusic.app.data.local.entity.PlaylistWithSongs> selectedPlaylist = null;
    @org.jetbrains.annotations.NotNull()
    private final kotlinx.coroutines.flow.MutableStateFlow<java.lang.Boolean> _showCreateDialog = null;
    @org.jetbrains.annotations.NotNull()
    private final kotlinx.coroutines.flow.StateFlow<java.lang.Boolean> showCreateDialog = null;
    @org.jetbrains.annotations.NotNull()
    public static final com.auramusic.app.presentation.library.LibraryViewModel.Companion Companion = null;
    
    @javax.inject.Inject()
    public LibraryViewModel(@org.jetbrains.annotations.NotNull()
    com.auramusic.app.data.local.dao.PlaylistDao playlistDao, @org.jetbrains.annotations.NotNull()
    com.auramusic.app.data.local.dao.SongDao songDao) {
        super();
    }
    
    @org.jetbrains.annotations.NotNull()
    public final kotlinx.coroutines.flow.StateFlow<java.util.List<com.auramusic.app.data.local.entity.PlaylistEntity>> getPlaylists() {
        return null;
    }
    
    @org.jetbrains.annotations.NotNull()
    public final kotlinx.coroutines.flow.StateFlow<com.auramusic.app.data.local.entity.PlaylistWithSongs> getSelectedPlaylist() {
        return null;
    }
    
    @org.jetbrains.annotations.NotNull()
    public final kotlinx.coroutines.flow.StateFlow<java.lang.Boolean> getShowCreateDialog() {
        return null;
    }
    
    public final void showCreatePlaylistDialog() {
    }
    
    public final void dismissCreatePlaylistDialog() {
    }
    
    public final void createPlaylist(@org.jetbrains.annotations.NotNull()
    java.lang.String name) {
    }
    
    public final void deletePlaylist(long playlistId) {
    }
    
    public final void selectPlaylist(long playlistId) {
    }
    
    public final void clearSelection() {
    }
    
    public final void addSongToPlaylist(long playlistId, @org.jetbrains.annotations.NotNull()
    com.auramusic.app.domain.model.Song song) {
    }
    
    public final void removeSongFromPlaylist(long playlistId, @org.jetbrains.annotations.NotNull()
    java.lang.String songId) {
    }
    
    @kotlin.Metadata(mv = {1, 9, 0}, k = 1, xi = 48, d1 = {"\u0000\u0012\n\u0002\u0018\u0002\n\u0002\u0010\u0000\n\u0002\b\u0002\n\u0002\u0010\u000e\n\u0000\b\u0086\u0003\u0018\u00002\u00020\u0001B\u0007\b\u0002\u00a2\u0006\u0002\u0010\u0002R\u000e\u0010\u0003\u001a\u00020\u0004X\u0082T\u00a2\u0006\u0002\n\u0000\u00a8\u0006\u0005"}, d2 = {"Lcom/auramusic/app/presentation/library/LibraryViewModel$Companion;", "", "()V", "TAG", "", "app_debug"})
    public static final class Companion {
        
        private Companion() {
            super();
        }
    }
}