package com.auramusic.app.data.repository;

@javax.inject.Singleton()
@kotlin.Metadata(mv = {1, 9, 0}, k = 1, xi = 48, d1 = {"\u0000R\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0002\b\u0002\n\u0002\u0018\u0002\n\u0002\u0010\t\n\u0002\b\u0005\n\u0002\u0010\u000b\n\u0000\n\u0002\u0018\u0002\n\u0002\b\u0003\n\u0002\u0018\u0002\n\u0000\n\u0002\u0010\u000e\n\u0002\b\u0002\n\u0002\u0010\u0002\n\u0002\b\u0004\n\u0002\u0018\u0002\n\u0002\b\u0002\n\u0002\u0018\u0002\n\u0002\b\u000e\b\u0007\u0018\u0000 -2\u00020\u0001:\u0001-B\u0011\b\u0007\u0012\b\b\u0001\u0010\u0002\u001a\u00020\u0003\u00a2\u0006\u0002\u0010\u0004J\u001e\u0010\u001a\u001a\u00020\u00182\u0006\u0010\u001b\u001a\u00020\u00152\u0006\u0010\u001c\u001a\u00020\u001dH\u0096@\u00a2\u0006\u0002\u0010\u001eJ\u0018\u0010\u001f\u001a\u00020 2\u0006\u0010\u001b\u001a\u00020\u00152\u0006\u0010\u001c\u001a\u00020\u001dH\u0002J\u000e\u0010!\u001a\u00020\u0018H\u0096@\u00a2\u0006\u0002\u0010\"J\u0010\u0010#\u001a\u0004\u0018\u00010\u000fH\u0082@\u00a2\u0006\u0002\u0010\"J\u000e\u0010$\u001a\u00020\rH\u0096@\u00a2\u0006\u0002\u0010\"J\u000e\u0010%\u001a\u00020\u0018H\u0096@\u00a2\u0006\u0002\u0010\"J\u000e\u0010&\u001a\u00020\u0018H\u0096@\u00a2\u0006\u0002\u0010\"J\u001e\u0010\'\u001a\u00020\u00182\u0006\u0010\u001b\u001a\u00020\u00152\u0006\u0010\u001c\u001a\u00020\u001dH\u0096@\u00a2\u0006\u0002\u0010\u001eJ\b\u0010(\u001a\u00020\u0018H\u0016J\u0016\u0010)\u001a\u00020\u00182\u0006\u0010*\u001a\u00020\u0007H\u0096@\u00a2\u0006\u0002\u0010+J\u000e\u0010,\u001a\u00020\u0018H\u0096@\u00a2\u0006\u0002\u0010\"R\u000e\u0010\u0002\u001a\u00020\u0003X\u0082\u0004\u00a2\u0006\u0002\n\u0000R\u001a\u0010\u0005\u001a\b\u0012\u0004\u0012\u00020\u00070\u0006X\u0096\u0004\u00a2\u0006\b\n\u0000\u001a\u0004\b\b\u0010\tR\u001a\u0010\n\u001a\b\u0012\u0004\u0012\u00020\u00070\u0006X\u0096\u0004\u00a2\u0006\b\n\u0000\u001a\u0004\b\u000b\u0010\tR\u001a\u0010\f\u001a\b\u0012\u0004\u0012\u00020\r0\u0006X\u0096\u0004\u00a2\u0006\b\n\u0000\u001a\u0004\b\f\u0010\tR\u0016\u0010\u000e\u001a\u0004\u0018\u00010\u000f8BX\u0082\u0004\u00a2\u0006\u0006\u001a\u0004\b\u0010\u0010\u0011R\u0016\u0010\u0012\u001a\n\u0012\u0004\u0012\u00020\u000f\u0018\u00010\u0013X\u0082\u000e\u00a2\u0006\u0002\n\u0000R\u001c\u0010\u0014\u001a\n\u0012\u0006\u0012\u0004\u0018\u00010\u00150\u0006X\u0096\u0004\u00a2\u0006\b\n\u0000\u001a\u0004\b\u0016\u0010\tR\u001a\u0010\u0017\u001a\b\u0012\u0004\u0012\u00020\u00180\u0006X\u0096\u0004\u00a2\u0006\b\n\u0000\u001a\u0004\b\u0019\u0010\t\u00a8\u0006."}, d2 = {"Lcom/auramusic/app/data/repository/PlayerRepositoryImpl;", "Lcom/auramusic/app/domain/repository/PlayerRepository;", "context", "Landroid/content/Context;", "(Landroid/content/Context;)V", "currentPosition", "Lkotlinx/coroutines/flow/Flow;", "", "getCurrentPosition", "()Lkotlinx/coroutines/flow/Flow;", "duration", "getDuration", "isPlaying", "", "mediaController", "Landroidx/media3/session/MediaController;", "getMediaController", "()Landroidx/media3/session/MediaController;", "mediaControllerFuture", "Lcom/google/common/util/concurrent/ListenableFuture;", "mediaItemTransition", "", "getMediaItemTransition", "trackEnded", "", "getTrackEnded", "addNextMediaItem", "url", "song", "Lcom/auramusic/app/domain/model/Song;", "(Ljava/lang/String;Lcom/auramusic/app/domain/model/Song;Lkotlin/coroutines/Continuation;)Ljava/lang/Object;", "buildMediaItem", "Landroidx/media3/common/MediaItem;", "clearQueue", "(Lkotlin/coroutines/Continuation;)Ljava/lang/Object;", "getController", "hasNextMediaItem", "pause", "play", "prepare", "release", "seekTo", "position", "(JLkotlin/coroutines/Continuation;)Ljava/lang/Object;", "skipToNext", "Companion", "app_debug"})
public final class PlayerRepositoryImpl implements com.auramusic.app.domain.repository.PlayerRepository {
    @org.jetbrains.annotations.NotNull()
    private final android.content.Context context = null;
    @org.jetbrains.annotations.NotNull()
    private static final java.lang.String TAG = "XploitDebug";
    @org.jetbrains.annotations.Nullable()
    private com.google.common.util.concurrent.ListenableFuture<androidx.media3.session.MediaController> mediaControllerFuture;
    @org.jetbrains.annotations.NotNull()
    private final kotlinx.coroutines.flow.Flow<java.lang.Boolean> isPlaying = null;
    @org.jetbrains.annotations.NotNull()
    private final kotlinx.coroutines.flow.Flow<java.lang.Long> currentPosition = null;
    @org.jetbrains.annotations.NotNull()
    private final kotlinx.coroutines.flow.Flow<java.lang.Long> duration = null;
    
    /**
     * Emits Unit every time a track reaches STATE_ENDED
     */
    @org.jetbrains.annotations.NotNull()
    private final kotlinx.coroutines.flow.Flow<kotlin.Unit> trackEnded = null;
    
    /**
     * Emits the mediaId of the new track when ExoPlayer transitions between items
     */
    @org.jetbrains.annotations.NotNull()
    private final kotlinx.coroutines.flow.Flow<java.lang.String> mediaItemTransition = null;
    @org.jetbrains.annotations.NotNull()
    public static final com.auramusic.app.data.repository.PlayerRepositoryImpl.Companion Companion = null;
    
    @javax.inject.Inject()
    public PlayerRepositoryImpl(@dagger.hilt.android.qualifiers.ApplicationContext()
    @org.jetbrains.annotations.NotNull()
    android.content.Context context) {
        super();
    }
    
    private final androidx.media3.session.MediaController getMediaController() {
        return null;
    }
    
    private final java.lang.Object getController(kotlin.coroutines.Continuation<? super androidx.media3.session.MediaController> $completion) {
        return null;
    }
    
    /**
     * Builds a MediaItem with full metadata so the system notification
     * displays the song title, artist, and artwork.
     */
    private final androidx.media3.common.MediaItem buildMediaItem(java.lang.String url, com.auramusic.app.domain.model.Song song) {
        return null;
    }
    
    @java.lang.Override()
    @org.jetbrains.annotations.NotNull()
    public kotlinx.coroutines.flow.Flow<java.lang.Boolean> isPlaying() {
        return null;
    }
    
    @java.lang.Override()
    @org.jetbrains.annotations.NotNull()
    public kotlinx.coroutines.flow.Flow<java.lang.Long> getCurrentPosition() {
        return null;
    }
    
    @java.lang.Override()
    @org.jetbrains.annotations.NotNull()
    public kotlinx.coroutines.flow.Flow<java.lang.Long> getDuration() {
        return null;
    }
    
    /**
     * Emits Unit every time a track reaches STATE_ENDED
     */
    @java.lang.Override()
    @org.jetbrains.annotations.NotNull()
    public kotlinx.coroutines.flow.Flow<kotlin.Unit> getTrackEnded() {
        return null;
    }
    
    /**
     * Emits the mediaId of the new track when ExoPlayer transitions between items
     */
    @java.lang.Override()
    @org.jetbrains.annotations.NotNull()
    public kotlinx.coroutines.flow.Flow<java.lang.String> getMediaItemTransition() {
        return null;
    }
    
    @java.lang.Override()
    @org.jetbrains.annotations.Nullable()
    public java.lang.Object play(@org.jetbrains.annotations.NotNull()
    kotlin.coroutines.Continuation<? super kotlin.Unit> $completion) {
        return null;
    }
    
    @java.lang.Override()
    @org.jetbrains.annotations.Nullable()
    public java.lang.Object pause(@org.jetbrains.annotations.NotNull()
    kotlin.coroutines.Continuation<? super kotlin.Unit> $completion) {
        return null;
    }
    
    @java.lang.Override()
    @org.jetbrains.annotations.Nullable()
    public java.lang.Object seekTo(long position, @org.jetbrains.annotations.NotNull()
    kotlin.coroutines.Continuation<? super kotlin.Unit> $completion) {
        return null;
    }
    
    @java.lang.Override()
    @org.jetbrains.annotations.Nullable()
    public java.lang.Object prepare(@org.jetbrains.annotations.NotNull()
    java.lang.String url, @org.jetbrains.annotations.NotNull()
    com.auramusic.app.domain.model.Song song, @org.jetbrains.annotations.NotNull()
    kotlin.coroutines.Continuation<? super kotlin.Unit> $completion) {
        return null;
    }
    
    @java.lang.Override()
    @org.jetbrains.annotations.Nullable()
    public java.lang.Object addNextMediaItem(@org.jetbrains.annotations.NotNull()
    java.lang.String url, @org.jetbrains.annotations.NotNull()
    com.auramusic.app.domain.model.Song song, @org.jetbrains.annotations.NotNull()
    kotlin.coroutines.Continuation<? super kotlin.Unit> $completion) {
        return null;
    }
    
    @java.lang.Override()
    @org.jetbrains.annotations.Nullable()
    public java.lang.Object clearQueue(@org.jetbrains.annotations.NotNull()
    kotlin.coroutines.Continuation<? super kotlin.Unit> $completion) {
        return null;
    }
    
    @java.lang.Override()
    @org.jetbrains.annotations.Nullable()
    public java.lang.Object hasNextMediaItem(@org.jetbrains.annotations.NotNull()
    kotlin.coroutines.Continuation<? super java.lang.Boolean> $completion) {
        return null;
    }
    
    @java.lang.Override()
    @org.jetbrains.annotations.Nullable()
    public java.lang.Object skipToNext(@org.jetbrains.annotations.NotNull()
    kotlin.coroutines.Continuation<? super kotlin.Unit> $completion) {
        return null;
    }
    
    @java.lang.Override()
    public void release() {
    }
    
    @kotlin.Metadata(mv = {1, 9, 0}, k = 1, xi = 48, d1 = {"\u0000\u0012\n\u0002\u0018\u0002\n\u0002\u0010\u0000\n\u0002\b\u0002\n\u0002\u0010\u000e\n\u0000\b\u0086\u0003\u0018\u00002\u00020\u0001B\u0007\b\u0002\u00a2\u0006\u0002\u0010\u0002R\u000e\u0010\u0003\u001a\u00020\u0004X\u0082T\u00a2\u0006\u0002\n\u0000\u00a8\u0006\u0005"}, d2 = {"Lcom/auramusic/app/data/repository/PlayerRepositoryImpl$Companion;", "", "()V", "TAG", "", "app_debug"})
    public static final class Companion {
        
        private Companion() {
            super();
        }
    }
}