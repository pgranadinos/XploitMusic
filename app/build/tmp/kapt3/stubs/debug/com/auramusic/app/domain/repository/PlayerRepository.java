package com.auramusic.app.domain.repository;

@kotlin.Metadata(mv = {1, 9, 0}, k = 1, xi = 48, d1 = {"\u00004\n\u0002\u0018\u0002\n\u0002\u0010\u0000\n\u0000\n\u0002\u0018\u0002\n\u0002\u0010\t\n\u0002\b\u0005\n\u0002\u0010\u000b\n\u0000\n\u0002\u0010\u000e\n\u0002\b\u0002\n\u0002\u0010\u0002\n\u0002\b\u0004\n\u0002\u0018\u0002\n\u0002\b\r\bf\u0018\u00002\u00020\u0001J\u001e\u0010\u0011\u001a\u00020\u000f2\u0006\u0010\u0012\u001a\u00020\f2\u0006\u0010\u0013\u001a\u00020\u0014H\u00a6@\u00a2\u0006\u0002\u0010\u0015J\u000e\u0010\u0016\u001a\u00020\u000fH\u00a6@\u00a2\u0006\u0002\u0010\u0017J\u000e\u0010\u0018\u001a\u00020\nH\u00a6@\u00a2\u0006\u0002\u0010\u0017J\u000e\u0010\u0019\u001a\u00020\u000fH\u00a6@\u00a2\u0006\u0002\u0010\u0017J\u000e\u0010\u001a\u001a\u00020\u000fH\u00a6@\u00a2\u0006\u0002\u0010\u0017J\u001e\u0010\u001b\u001a\u00020\u000f2\u0006\u0010\u0012\u001a\u00020\f2\u0006\u0010\u0013\u001a\u00020\u0014H\u00a6@\u00a2\u0006\u0002\u0010\u0015J\b\u0010\u001c\u001a\u00020\u000fH&J\u0016\u0010\u001d\u001a\u00020\u000f2\u0006\u0010\u001e\u001a\u00020\u0004H\u00a6@\u00a2\u0006\u0002\u0010\u001fJ\u000e\u0010 \u001a\u00020\u000fH\u00a6@\u00a2\u0006\u0002\u0010\u0017R\u0018\u0010\u0002\u001a\b\u0012\u0004\u0012\u00020\u00040\u0003X\u00a6\u0004\u00a2\u0006\u0006\u001a\u0004\b\u0005\u0010\u0006R\u0018\u0010\u0007\u001a\b\u0012\u0004\u0012\u00020\u00040\u0003X\u00a6\u0004\u00a2\u0006\u0006\u001a\u0004\b\b\u0010\u0006R\u0018\u0010\t\u001a\b\u0012\u0004\u0012\u00020\n0\u0003X\u00a6\u0004\u00a2\u0006\u0006\u001a\u0004\b\t\u0010\u0006R\u001a\u0010\u000b\u001a\n\u0012\u0006\u0012\u0004\u0018\u00010\f0\u0003X\u00a6\u0004\u00a2\u0006\u0006\u001a\u0004\b\r\u0010\u0006R\u0018\u0010\u000e\u001a\b\u0012\u0004\u0012\u00020\u000f0\u0003X\u00a6\u0004\u00a2\u0006\u0006\u001a\u0004\b\u0010\u0010\u0006\u00a8\u0006!"}, d2 = {"Lcom/auramusic/app/domain/repository/PlayerRepository;", "", "currentPosition", "Lkotlinx/coroutines/flow/Flow;", "", "getCurrentPosition", "()Lkotlinx/coroutines/flow/Flow;", "duration", "getDuration", "isPlaying", "", "mediaItemTransition", "", "getMediaItemTransition", "trackEnded", "", "getTrackEnded", "addNextMediaItem", "url", "song", "Lcom/auramusic/app/domain/model/Song;", "(Ljava/lang/String;Lcom/auramusic/app/domain/model/Song;Lkotlin/coroutines/Continuation;)Ljava/lang/Object;", "clearQueue", "(Lkotlin/coroutines/Continuation;)Ljava/lang/Object;", "hasNextMediaItem", "pause", "play", "prepare", "release", "seekTo", "position", "(JLkotlin/coroutines/Continuation;)Ljava/lang/Object;", "skipToNext", "app_debug"})
public abstract interface PlayerRepository {
    
    @org.jetbrains.annotations.NotNull()
    public abstract kotlinx.coroutines.flow.Flow<java.lang.Boolean> isPlaying();
    
    @org.jetbrains.annotations.NotNull()
    public abstract kotlinx.coroutines.flow.Flow<java.lang.Long> getCurrentPosition();
    
    @org.jetbrains.annotations.NotNull()
    public abstract kotlinx.coroutines.flow.Flow<java.lang.Long> getDuration();
    
    @org.jetbrains.annotations.NotNull()
    public abstract kotlinx.coroutines.flow.Flow<kotlin.Unit> getTrackEnded();
    
    /**
     * Emits the mediaId of the new track when ExoPlayer transitions between items
     */
    @org.jetbrains.annotations.NotNull()
    public abstract kotlinx.coroutines.flow.Flow<java.lang.String> getMediaItemTransition();
    
    @org.jetbrains.annotations.Nullable()
    public abstract java.lang.Object play(@org.jetbrains.annotations.NotNull()
    kotlin.coroutines.Continuation<? super kotlin.Unit> $completion);
    
    @org.jetbrains.annotations.Nullable()
    public abstract java.lang.Object pause(@org.jetbrains.annotations.NotNull()
    kotlin.coroutines.Continuation<? super kotlin.Unit> $completion);
    
    @org.jetbrains.annotations.Nullable()
    public abstract java.lang.Object seekTo(long position, @org.jetbrains.annotations.NotNull()
    kotlin.coroutines.Continuation<? super kotlin.Unit> $completion);
    
    @org.jetbrains.annotations.Nullable()
    public abstract java.lang.Object prepare(@org.jetbrains.annotations.NotNull()
    java.lang.String url, @org.jetbrains.annotations.NotNull()
    com.auramusic.app.domain.model.Song song, @org.jetbrains.annotations.NotNull()
    kotlin.coroutines.Continuation<? super kotlin.Unit> $completion);
    
    @org.jetbrains.annotations.Nullable()
    public abstract java.lang.Object addNextMediaItem(@org.jetbrains.annotations.NotNull()
    java.lang.String url, @org.jetbrains.annotations.NotNull()
    com.auramusic.app.domain.model.Song song, @org.jetbrains.annotations.NotNull()
    kotlin.coroutines.Continuation<? super kotlin.Unit> $completion);
    
    @org.jetbrains.annotations.Nullable()
    public abstract java.lang.Object clearQueue(@org.jetbrains.annotations.NotNull()
    kotlin.coroutines.Continuation<? super kotlin.Unit> $completion);
    
    @org.jetbrains.annotations.Nullable()
    public abstract java.lang.Object hasNextMediaItem(@org.jetbrains.annotations.NotNull()
    kotlin.coroutines.Continuation<? super java.lang.Boolean> $completion);
    
    @org.jetbrains.annotations.Nullable()
    public abstract java.lang.Object skipToNext(@org.jetbrains.annotations.NotNull()
    kotlin.coroutines.Continuation<? super kotlin.Unit> $completion);
    
    public abstract void release();
}