package com.auramusic.app.data.service;

@dagger.hilt.android.AndroidEntryPoint()
@kotlin.Metadata(mv = {1, 9, 0}, k = 1, xi = 48, d1 = {"\u00002\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0002\b\u0002\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0002\b\u0005\n\u0002\u0018\u0002\n\u0002\b\u0005\n\u0002\u0010\u0002\n\u0002\b\u0003\n\u0002\u0018\u0002\n\u0002\b\u0002\b\u0007\u0018\u00002\u00020\u0001:\u0001\u0017B\u0005\u00a2\u0006\u0002\u0010\u0002J\b\u0010\u0011\u001a\u00020\u0012H\u0016J\b\u0010\u0013\u001a\u00020\u0012H\u0016J\u0012\u0010\u0014\u001a\u0004\u0018\u00010\u00042\u0006\u0010\u0015\u001a\u00020\u0016H\u0016R\u0010\u0010\u0003\u001a\u0004\u0018\u00010\u0004X\u0082\u000e\u00a2\u0006\u0002\n\u0000R\u001e\u0010\u0005\u001a\u00020\u00068\u0006@\u0006X\u0087.\u00a2\u0006\u000e\n\u0000\u001a\u0004\b\u0007\u0010\b\"\u0004\b\t\u0010\nR\u001e\u0010\u000b\u001a\u00020\f8\u0006@\u0006X\u0087.\u00a2\u0006\u000e\n\u0000\u001a\u0004\b\r\u0010\u000e\"\u0004\b\u000f\u0010\u0010\u00a8\u0006\u0018"}, d2 = {"Lcom/auramusic/app/data/service/AuraMediaService;", "Landroidx/media3/session/MediaLibraryService;", "()V", "mediaSession", "Landroidx/media3/session/MediaLibraryService$MediaLibrarySession;", "player", "Landroidx/media3/exoplayer/ExoPlayer;", "getPlayer", "()Landroidx/media3/exoplayer/ExoPlayer;", "setPlayer", "(Landroidx/media3/exoplayer/ExoPlayer;)V", "skipHandler", "Lcom/auramusic/app/data/service/SkipHandler;", "getSkipHandler", "()Lcom/auramusic/app/data/service/SkipHandler;", "setSkipHandler", "(Lcom/auramusic/app/data/service/SkipHandler;)V", "onCreate", "", "onDestroy", "onGetSession", "controllerInfo", "Landroidx/media3/session/MediaSession$ControllerInfo;", "MediaLibrarySessionCallback", "app_debug"})
public final class AuraMediaService extends androidx.media3.session.MediaLibraryService {
    @javax.inject.Inject()
    public androidx.media3.exoplayer.ExoPlayer player;
    @javax.inject.Inject()
    public com.auramusic.app.data.service.SkipHandler skipHandler;
    @org.jetbrains.annotations.Nullable()
    private androidx.media3.session.MediaLibraryService.MediaLibrarySession mediaSession;
    
    public AuraMediaService() {
        super();
    }
    
    @org.jetbrains.annotations.NotNull()
    public final androidx.media3.exoplayer.ExoPlayer getPlayer() {
        return null;
    }
    
    public final void setPlayer(@org.jetbrains.annotations.NotNull()
    androidx.media3.exoplayer.ExoPlayer p0) {
    }
    
    @org.jetbrains.annotations.NotNull()
    public final com.auramusic.app.data.service.SkipHandler getSkipHandler() {
        return null;
    }
    
    public final void setSkipHandler(@org.jetbrains.annotations.NotNull()
    com.auramusic.app.data.service.SkipHandler p0) {
    }
    
    @java.lang.Override()
    public void onCreate() {
    }
    
    @java.lang.Override()
    @org.jetbrains.annotations.Nullable()
    public androidx.media3.session.MediaLibraryService.MediaLibrarySession onGetSession(@org.jetbrains.annotations.NotNull()
    androidx.media3.session.MediaSession.ControllerInfo controllerInfo) {
        return null;
    }
    
    @java.lang.Override()
    public void onDestroy() {
    }
    
    @kotlin.Metadata(mv = {1, 9, 0}, k = 1, xi = 48, d1 = {"\u0000\u001e\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0002\b\u0002\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0000\b\u0082\u0004\u0018\u00002\u00020\u0001B\u0005\u00a2\u0006\u0002\u0010\u0002J\u0018\u0010\u0003\u001a\u00020\u00042\u0006\u0010\u0005\u001a\u00020\u00062\u0006\u0010\u0007\u001a\u00020\bH\u0016\u00a8\u0006\t"}, d2 = {"Lcom/auramusic/app/data/service/AuraMediaService$MediaLibrarySessionCallback;", "Landroidx/media3/session/MediaLibraryService$MediaLibrarySession$Callback;", "(Lcom/auramusic/app/data/service/AuraMediaService;)V", "onConnect", "Landroidx/media3/session/MediaSession$ConnectionResult;", "session", "Landroidx/media3/session/MediaSession;", "controller", "Landroidx/media3/session/MediaSession$ControllerInfo;", "app_debug"})
    final class MediaLibrarySessionCallback implements androidx.media3.session.MediaLibraryService.MediaLibrarySession.Callback {
        
        public MediaLibrarySessionCallback() {
            super();
        }
        
        @java.lang.Override()
        @org.jetbrains.annotations.NotNull()
        public androidx.media3.session.MediaSession.ConnectionResult onConnect(@org.jetbrains.annotations.NotNull()
        androidx.media3.session.MediaSession session, @org.jetbrains.annotations.NotNull()
        androidx.media3.session.MediaSession.ControllerInfo controller) {
            return null;
        }
    }
}