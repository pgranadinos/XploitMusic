package com.auramusic.app.presentation.player;

@kotlin.Metadata(mv = {1, 9, 0}, k = 1, xi = 48, d1 = {"\u0000\u0088\u0001\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0002\b\u0002\n\u0002\u0018\u0002\n\u0002\u0010\u000b\n\u0000\n\u0002\u0010 \n\u0002\u0018\u0002\n\u0000\n\u0002\u0010\b\n\u0002\b\u0002\n\u0002\u0010\u000e\n\u0002\b\u0003\n\u0002\u0018\u0002\n\u0000\n\u0002\u0010#\n\u0002\b\u0002\n\u0002\u0018\u0002\n\u0002\b\u0003\n\u0002\u0018\u0002\n\u0002\b\u0003\n\u0002\u0010\t\n\u0002\b\r\n\u0002\u0010\u0002\n\u0002\b\u001a\b\u0007\u0018\u0000 S2\u00020\u0001:\u0001SB?\b\u0007\u0012\u0006\u0010\u0002\u001a\u00020\u0003\u0012\u0006\u0010\u0004\u001a\u00020\u0005\u0012\u0006\u0010\u0006\u001a\u00020\u0007\u0012\u0006\u0010\b\u001a\u00020\t\u0012\u0006\u0010\n\u001a\u00020\u000b\u0012\u0006\u0010\f\u001a\u00020\r\u0012\u0006\u0010\u000e\u001a\u00020\u000f\u00a2\u0006\u0002\u0010\u0010J\u000e\u00109\u001a\u00020:H\u0082@\u00a2\u0006\u0002\u0010;J\u0006\u0010<\u001a\u00020:J\u0012\u0010=\u001a\u0004\u0018\u00010\u00162\u0006\u0010>\u001a\u00020\u001bH\u0002J\u000e\u0010?\u001a\u00020:H\u0082@\u00a2\u0006\u0002\u0010;J\n\u0010@\u001a\u0004\u0018\u00010\u0016H\u0002J\n\u0010A\u001a\u0004\u0018\u00010\u0016H\u0002J\b\u0010B\u001a\u00020:H\u0002J\u0010\u0010C\u001a\u00020:2\u0006\u0010D\u001a\u00020\u0016H\u0002J\u0016\u0010E\u001a\u00020:2\u0006\u0010D\u001a\u00020\u0016H\u0082@\u00a2\u0006\u0002\u0010FJ\u000e\u0010G\u001a\u00020:2\u0006\u0010D\u001a\u00020\u0016J\u001e\u0010H\u001a\u00020:2\u0006\u0010D\u001a\u00020\u00162\u000e\b\u0002\u0010I\u001a\b\u0012\u0004\u0012\u00020\u00160\u0015J\u000e\u0010J\u001a\u00020:H\u0082@\u00a2\u0006\u0002\u0010;J\u0016\u0010K\u001a\u00020:2\u0006\u0010D\u001a\u00020\u0016H\u0082@\u00a2\u0006\u0002\u0010FJ\u000e\u0010L\u001a\u00020:2\u0006\u0010M\u001a\u00020,J\u0006\u0010N\u001a\u00020:J\u0006\u0010O\u001a\u00020:J\u0006\u0010P\u001a\u00020:J\u0006\u0010Q\u001a\u00020:J\u0006\u0010R\u001a\u00020:R\u0014\u0010\u0011\u001a\b\u0012\u0004\u0012\u00020\u00130\u0012X\u0082\u0004\u00a2\u0006\u0002\n\u0000R\u001a\u0010\u0014\u001a\u000e\u0012\n\u0012\b\u0012\u0004\u0012\u00020\u00160\u00150\u0012X\u0082\u0004\u00a2\u0006\u0002\n\u0000R\u000e\u0010\u0017\u001a\u00020\u0018X\u0082\u000e\u00a2\u0006\u0002\n\u0000R\u0016\u0010\u0019\u001a\n\u0012\u0006\u0012\u0004\u0018\u00010\u00160\u0012X\u0082\u0004\u00a2\u0006\u0002\n\u0000R\u0016\u0010\u001a\u001a\n\u0012\u0006\u0012\u0004\u0018\u00010\u001b0\u0012X\u0082\u0004\u00a2\u0006\u0002\n\u0000R\u000e\u0010\u001c\u001a\u00020\u0013X\u0082\u000e\u00a2\u0006\u0002\n\u0000R\u0014\u0010\u001d\u001a\b\u0012\u0004\u0012\u00020\u00130\u0012X\u0082\u0004\u00a2\u0006\u0002\n\u0000R\u001a\u0010\u001e\u001a\u000e\u0012\n\u0012\b\u0012\u0004\u0012\u00020\u001f0\u00150\u0012X\u0082\u0004\u00a2\u0006\u0002\n\u0000R\u0014\u0010 \u001a\b\u0012\u0004\u0012\u00020\u001b0!X\u0082\u0004\u00a2\u0006\u0002\n\u0000R\u001a\u0010\"\u001a\u000e\u0012\n\u0012\b\u0012\u0004\u0012\u00020\u00160\u00150\u0012X\u0082\u0004\u00a2\u0006\u0002\n\u0000R\u0010\u0010#\u001a\u0004\u0018\u00010$X\u0082\u000e\u00a2\u0006\u0002\n\u0000R\u0010\u0010%\u001a\u0004\u0018\u00010\u0016X\u0082\u000e\u00a2\u0006\u0002\n\u0000R\u0010\u0010&\u001a\u0004\u0018\u00010$X\u0082\u000e\u00a2\u0006\u0002\n\u0000R\u0017\u0010\'\u001a\b\u0012\u0004\u0012\u00020\u00130(\u00a2\u0006\b\n\u0000\u001a\u0004\b)\u0010*R\u0017\u0010+\u001a\b\u0012\u0004\u0012\u00020,0(\u00a2\u0006\b\n\u0000\u001a\u0004\b-\u0010*R\u0019\u0010.\u001a\n\u0012\u0006\u0012\u0004\u0018\u00010\u00160(\u00a2\u0006\b\n\u0000\u001a\u0004\b/\u0010*R\u0017\u00100\u001a\b\u0012\u0004\u0012\u00020,0(\u00a2\u0006\b\n\u0000\u001a\u0004\b1\u0010*R\u0019\u00102\u001a\n\u0012\u0006\u0012\u0004\u0018\u00010\u001b0(\u00a2\u0006\b\n\u0000\u001a\u0004\b3\u0010*R\u000e\u0010\n\u001a\u00020\u000bX\u0082\u0004\u00a2\u0006\u0002\n\u0000R\u000e\u0010\u0004\u001a\u00020\u0005X\u0082\u0004\u00a2\u0006\u0002\n\u0000R\u000e\u0010\u0006\u001a\u00020\u0007X\u0082\u0004\u00a2\u0006\u0002\n\u0000R\u0017\u00104\u001a\b\u0012\u0004\u0012\u00020\u00130(\u00a2\u0006\b\n\u0000\u001a\u0004\b4\u0010*R\u0017\u00105\u001a\b\u0012\u0004\u0012\u00020\u00130(\u00a2\u0006\b\n\u0000\u001a\u0004\b5\u0010*R\u0017\u00106\u001a\b\u0012\u0004\u0012\u00020\u00130(\u00a2\u0006\b\n\u0000\u001a\u0004\b6\u0010*R\u001d\u00107\u001a\u000e\u0012\n\u0012\b\u0012\u0004\u0012\u00020\u001f0\u00150(\u00a2\u0006\b\n\u0000\u001a\u0004\b8\u0010*R\u000e\u0010\u0002\u001a\u00020\u0003X\u0082\u0004\u00a2\u0006\u0002\n\u0000R\u000e\u0010\u000e\u001a\u00020\u000fX\u0082\u0004\u00a2\u0006\u0002\n\u0000R\u000e\u0010\f\u001a\u00020\rX\u0082\u0004\u00a2\u0006\u0002\n\u0000R\u000e\u0010\b\u001a\u00020\tX\u0082\u0004\u00a2\u0006\u0002\n\u0000\u00a8\u0006T"}, d2 = {"Lcom/auramusic/app/presentation/player/PlayerViewModel;", "Landroidx/lifecycle/ViewModel;", "playerRepository", "Lcom/auramusic/app/domain/repository/PlayerRepository;", "getAudioStreamUseCase", "Lcom/auramusic/app/domain/usecase/GetAudioStreamUseCase;", "getLyricsUseCase", "Lcom/auramusic/app/domain/usecase/GetLyricsUseCase;", "toggleFavoriteUseCase", "Lcom/auramusic/app/domain/usecase/ToggleFavoriteUseCase;", "favoritesRepository", "Lcom/auramusic/app/domain/repository/FavoritesRepository;", "streamRepository", "Lcom/auramusic/app/domain/repository/StreamRepository;", "skipHandler", "Lcom/auramusic/app/data/service/SkipHandler;", "(Lcom/auramusic/app/domain/repository/PlayerRepository;Lcom/auramusic/app/domain/usecase/GetAudioStreamUseCase;Lcom/auramusic/app/domain/usecase/GetLyricsUseCase;Lcom/auramusic/app/domain/usecase/ToggleFavoriteUseCase;Lcom/auramusic/app/domain/repository/FavoritesRepository;Lcom/auramusic/app/domain/repository/StreamRepository;Lcom/auramusic/app/data/service/SkipHandler;)V", "_autoplayEnabled", "Lkotlinx/coroutines/flow/MutableStateFlow;", "", "_autoplayQueue", "", "Lcom/auramusic/app/domain/model/Song;", "_currentIndex", "", "_currentSong", "_errorMessage", "", "_ignoringTransition", "_isLoadingStream", "_lyrics", "Lcom/auramusic/app/domain/model/LyricLine;", "_playedSongIds", "", "_playlist", "_prefetchJob", "Lkotlinx/coroutines/Job;", "_prefetchedNextSong", "_skipJob", "autoplayEnabled", "Lkotlinx/coroutines/flow/StateFlow;", "getAutoplayEnabled", "()Lkotlinx/coroutines/flow/StateFlow;", "currentPosition", "", "getCurrentPosition", "currentSong", "getCurrentSong", "duration", "getDuration", "errorMessage", "getErrorMessage", "isFavorite", "isLoadingStream", "isPlaying", "lyrics", "getLyrics", "autoplayNext", "", "(Lkotlin/coroutines/Continuation;)Ljava/lang/Object;", "clearError", "findSongByMediaId", "mediaId", "forceSkipToNext", "getNextSong", "getNextSongFromQueue", "launchPrefetch", "onSongTransitioned", "song", "playInternal", "(Lcom/auramusic/app/domain/model/Song;Lkotlin/coroutines/Continuation;)Ljava/lang/Object;", "playSingleSong", "playSong", "playlist", "prefetchNextSong", "replenishAutoplayQueue", "seekTo", "position", "skipNext", "skipPrevious", "toggleAutoplay", "toggleFavorite", "togglePlayPause", "Companion", "app_debug"})
@kotlin.OptIn(markerClass = {kotlinx.coroutines.ExperimentalCoroutinesApi.class})
@dagger.hilt.android.lifecycle.HiltViewModel()
public final class PlayerViewModel extends androidx.lifecycle.ViewModel {
    @org.jetbrains.annotations.NotNull()
    private final com.auramusic.app.domain.repository.PlayerRepository playerRepository = null;
    @org.jetbrains.annotations.NotNull()
    private final com.auramusic.app.domain.usecase.GetAudioStreamUseCase getAudioStreamUseCase = null;
    @org.jetbrains.annotations.NotNull()
    private final com.auramusic.app.domain.usecase.GetLyricsUseCase getLyricsUseCase = null;
    @org.jetbrains.annotations.NotNull()
    private final com.auramusic.app.domain.usecase.ToggleFavoriteUseCase toggleFavoriteUseCase = null;
    @org.jetbrains.annotations.NotNull()
    private final com.auramusic.app.domain.repository.FavoritesRepository favoritesRepository = null;
    @org.jetbrains.annotations.NotNull()
    private final com.auramusic.app.domain.repository.StreamRepository streamRepository = null;
    @org.jetbrains.annotations.NotNull()
    private final com.auramusic.app.data.service.SkipHandler skipHandler = null;
    @org.jetbrains.annotations.NotNull()
    private static final java.lang.String TAG = "XploitDebug";
    @org.jetbrains.annotations.NotNull()
    private final kotlinx.coroutines.flow.MutableStateFlow<com.auramusic.app.domain.model.Song> _currentSong = null;
    @org.jetbrains.annotations.NotNull()
    private final kotlinx.coroutines.flow.StateFlow<com.auramusic.app.domain.model.Song> currentSong = null;
    @org.jetbrains.annotations.NotNull()
    private final kotlinx.coroutines.flow.MutableStateFlow<java.util.List<com.auramusic.app.domain.model.LyricLine>> _lyrics = null;
    @org.jetbrains.annotations.NotNull()
    private final kotlinx.coroutines.flow.StateFlow<java.util.List<com.auramusic.app.domain.model.LyricLine>> lyrics = null;
    @org.jetbrains.annotations.NotNull()
    private final kotlinx.coroutines.flow.MutableStateFlow<java.util.List<com.auramusic.app.domain.model.Song>> _playlist = null;
    private int _currentIndex = -1;
    @org.jetbrains.annotations.NotNull()
    private final kotlinx.coroutines.flow.MutableStateFlow<java.util.List<com.auramusic.app.domain.model.Song>> _autoplayQueue = null;
    @org.jetbrains.annotations.NotNull()
    private final java.util.Set<java.lang.String> _playedSongIds = null;
    @org.jetbrains.annotations.NotNull()
    private final kotlinx.coroutines.flow.MutableStateFlow<java.lang.Boolean> _autoplayEnabled = null;
    @org.jetbrains.annotations.NotNull()
    private final kotlinx.coroutines.flow.StateFlow<java.lang.Boolean> autoplayEnabled = null;
    @org.jetbrains.annotations.NotNull()
    private final kotlinx.coroutines.flow.MutableStateFlow<java.lang.Boolean> _isLoadingStream = null;
    @org.jetbrains.annotations.NotNull()
    private final kotlinx.coroutines.flow.StateFlow<java.lang.Boolean> isLoadingStream = null;
    @org.jetbrains.annotations.NotNull()
    private final kotlinx.coroutines.flow.MutableStateFlow<java.lang.String> _errorMessage = null;
    @org.jetbrains.annotations.NotNull()
    private final kotlinx.coroutines.flow.StateFlow<java.lang.String> errorMessage = null;
    @org.jetbrains.annotations.Nullable()
    private com.auramusic.app.domain.model.Song _prefetchedNextSong;
    @org.jetbrains.annotations.Nullable()
    private kotlinx.coroutines.Job _prefetchJob;
    @org.jetbrains.annotations.Nullable()
    private kotlinx.coroutines.Job _skipJob;
    private boolean _ignoringTransition = false;
    @org.jetbrains.annotations.NotNull()
    private final kotlinx.coroutines.flow.StateFlow<java.lang.Boolean> isFavorite = null;
    @org.jetbrains.annotations.NotNull()
    private final kotlinx.coroutines.flow.StateFlow<java.lang.Boolean> isPlaying = null;
    @org.jetbrains.annotations.NotNull()
    private final kotlinx.coroutines.flow.StateFlow<java.lang.Long> currentPosition = null;
    @org.jetbrains.annotations.NotNull()
    private final kotlinx.coroutines.flow.StateFlow<java.lang.Long> duration = null;
    @org.jetbrains.annotations.NotNull()
    public static final com.auramusic.app.presentation.player.PlayerViewModel.Companion Companion = null;
    
    @javax.inject.Inject()
    public PlayerViewModel(@org.jetbrains.annotations.NotNull()
    com.auramusic.app.domain.repository.PlayerRepository playerRepository, @org.jetbrains.annotations.NotNull()
    com.auramusic.app.domain.usecase.GetAudioStreamUseCase getAudioStreamUseCase, @org.jetbrains.annotations.NotNull()
    com.auramusic.app.domain.usecase.GetLyricsUseCase getLyricsUseCase, @org.jetbrains.annotations.NotNull()
    com.auramusic.app.domain.usecase.ToggleFavoriteUseCase toggleFavoriteUseCase, @org.jetbrains.annotations.NotNull()
    com.auramusic.app.domain.repository.FavoritesRepository favoritesRepository, @org.jetbrains.annotations.NotNull()
    com.auramusic.app.domain.repository.StreamRepository streamRepository, @org.jetbrains.annotations.NotNull()
    com.auramusic.app.data.service.SkipHandler skipHandler) {
        super();
    }
    
    @org.jetbrains.annotations.NotNull()
    public final kotlinx.coroutines.flow.StateFlow<com.auramusic.app.domain.model.Song> getCurrentSong() {
        return null;
    }
    
    @org.jetbrains.annotations.NotNull()
    public final kotlinx.coroutines.flow.StateFlow<java.util.List<com.auramusic.app.domain.model.LyricLine>> getLyrics() {
        return null;
    }
    
    @org.jetbrains.annotations.NotNull()
    public final kotlinx.coroutines.flow.StateFlow<java.lang.Boolean> getAutoplayEnabled() {
        return null;
    }
    
    @org.jetbrains.annotations.NotNull()
    public final kotlinx.coroutines.flow.StateFlow<java.lang.Boolean> isLoadingStream() {
        return null;
    }
    
    @org.jetbrains.annotations.NotNull()
    public final kotlinx.coroutines.flow.StateFlow<java.lang.String> getErrorMessage() {
        return null;
    }
    
    @org.jetbrains.annotations.NotNull()
    public final kotlinx.coroutines.flow.StateFlow<java.lang.Boolean> isFavorite() {
        return null;
    }
    
    @org.jetbrains.annotations.NotNull()
    public final kotlinx.coroutines.flow.StateFlow<java.lang.Boolean> isPlaying() {
        return null;
    }
    
    @org.jetbrains.annotations.NotNull()
    public final kotlinx.coroutines.flow.StateFlow<java.lang.Long> getCurrentPosition() {
        return null;
    }
    
    @org.jetbrains.annotations.NotNull()
    public final kotlinx.coroutines.flow.StateFlow<java.lang.Long> getDuration() {
        return null;
    }
    
    /**
     * Called when ExoPlayer has auto-transitioned to a new song.
     * Updates the UI state, fetches lyrics, refreshes related songs, and pre-fetches the next song.
     */
    private final void onSongTransitioned(com.auramusic.app.domain.model.Song song) {
    }
    
    /**
     * Tries to find a Song object by mediaId from playlist or autoplay queue.
     */
    private final com.auramusic.app.domain.model.Song findSongByMediaId(java.lang.String mediaId) {
        return null;
    }
    
    public final void playSong(@org.jetbrains.annotations.NotNull()
    com.auramusic.app.domain.model.Song song, @org.jetbrains.annotations.NotNull()
    java.util.List<com.auramusic.app.domain.model.Song> playlist) {
    }
    
    /**
     * Plays a single song WITHOUT queueing a playlist.
     * Used for search results: clears any existing queue, plays the song,
     * and relies on relatedStreams (autoplay) for the next song.
     */
    public final void playSingleSong(@org.jetbrains.annotations.NotNull()
    com.auramusic.app.domain.model.Song song) {
    }
    
    /**
     * Core playback function.
     * CRITICAL FIX (FALLO 4): This must NOT use coroutineScope{} because that
     * waits for ALL children to complete (including slow getRelatedSongs).
     * Instead, we launch background tasks independently in viewModelScope.
     */
    private final java.lang.Object playInternal(com.auramusic.app.domain.model.Song song, kotlin.coroutines.Continuation<? super kotlin.Unit> $completion) {
        return null;
    }
    
    /**
     * Fetches related songs using two sources, then applies Anti-Artist Clustering
     * (round-robin interleaving by artist) for a Spotify-like radio experience.
     *
     * Sources:
     * 1. Piped's relatedStreams (YouTube sidebar recommendations)
     * 2. Genre-based search ("[artist] mix" / "[artist] radio")
     *
     * After fetching, songs are:
     * - De-duplicated
     * - Filtered of already-played songs  
     * - Grouped by artist
     * - Interleaved round-robin (1 song per artist per round)
     * - First song guaranteed to be from a different artist than the current one
     */
    private final java.lang.Object replenishAutoplayQueue(com.auramusic.app.domain.model.Song song, kotlin.coroutines.Continuation<? super kotlin.Unit> $completion) {
        return null;
    }
    
    /**
     * Launches a coroutine to pre-fetch the next song's stream URL
     * and add it to ExoPlayer's queue for near-gapless playback.
     */
    private final void launchPrefetch() {
    }
    
    /**
     * Determines the next song and pre-fetches its stream URL,
     * then adds it to ExoPlayer's native queue.
     */
    private final java.lang.Object prefetchNextSong(kotlin.coroutines.Continuation<? super kotlin.Unit> $completion) {
        return null;
    }
    
    /**
     * Returns the next song in the playlist or autoplay queue, without consuming it.
     */
    private final com.auramusic.app.domain.model.Song getNextSong() {
        return null;
    }
    
    /**
     * Called when there's no pre-fetched item and we need to manually
     * start the next song (trackEnded fallback or skip button).
     */
    private final java.lang.Object autoplayNext(kotlin.coroutines.Continuation<? super kotlin.Unit> $completion) {
        return null;
    }
    
    public final void toggleAutoplay() {
    }
    
    public final void toggleFavorite() {
    }
    
    public final void clearError() {
    }
    
    public final void togglePlayPause() {
    }
    
    public final void seekTo(long position) {
    }
    
    public final void skipNext() {
    }
    
    /**
     * Force-skips to the next song immediately.
     * Called from both the UI skip button and the notification skip handler.
     *
     * This function is designed to be cancellation-safe:
     * - UI is updated IMMEDIATELY (song title, artwork, loading indicator)
     * - Stream URL resolution happens after → if cancelled by another skip, no harm done
     */
    private final java.lang.Object forceSkipToNext(kotlin.coroutines.Continuation<? super kotlin.Unit> $completion) {
        return null;
    }
    
    /**
     * Atomically pulls the next song from playlist or autoplay queue.
     * Returns null if both are exhausted.
     *
     * PROACTIVE REPLENISHMENT: If the remaining queue size drops to ≤ 3,
     * a background coroutine fires to refill the queue before it empties.
     */
    private final com.auramusic.app.domain.model.Song getNextSongFromQueue() {
        return null;
    }
    
    public final void skipPrevious() {
    }
    
    @kotlin.Metadata(mv = {1, 9, 0}, k = 1, xi = 48, d1 = {"\u0000\u0012\n\u0002\u0018\u0002\n\u0002\u0010\u0000\n\u0002\b\u0002\n\u0002\u0010\u000e\n\u0000\b\u0086\u0003\u0018\u00002\u00020\u0001B\u0007\b\u0002\u00a2\u0006\u0002\u0010\u0002R\u000e\u0010\u0003\u001a\u00020\u0004X\u0082T\u00a2\u0006\u0002\n\u0000\u00a8\u0006\u0005"}, d2 = {"Lcom/auramusic/app/presentation/player/PlayerViewModel$Companion;", "", "()V", "TAG", "", "app_debug"})
    public static final class Companion {
        
        private Companion() {
            super();
        }
    }
}