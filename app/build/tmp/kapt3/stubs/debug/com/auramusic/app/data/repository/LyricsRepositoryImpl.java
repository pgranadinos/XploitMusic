package com.auramusic.app.data.repository;

@kotlin.Metadata(mv = {1, 9, 0}, k = 1, xi = 48, d1 = {"\u0000,\n\u0002\u0018\u0002\n\u0002\u0010\u0000\n\u0000\n\u0002\u0018\u0002\n\u0002\b\u0002\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0000\n\u0002\u0010\u000e\n\u0002\b\u0002\n\u0002\u0010\u0006\n\u0002\b\u0002\u0018\u00002\u00020\u0001B\u000f\b\u0007\u0012\u0006\u0010\u0002\u001a\u00020\u0003\u00a2\u0006\u0002\u0010\u0004J,\u0010\u0005\u001a\b\u0012\u0004\u0012\u00020\u00070\u00062\u0006\u0010\b\u001a\u00020\t2\u0006\u0010\n\u001a\u00020\t2\u0006\u0010\u000b\u001a\u00020\fH\u0086@\u00a2\u0006\u0002\u0010\rR\u000e\u0010\u0002\u001a\u00020\u0003X\u0082\u0004\u00a2\u0006\u0002\n\u0000\u00a8\u0006\u000e"}, d2 = {"Lcom/auramusic/app/data/repository/LyricsRepositoryImpl;", "", "api", "Lcom/auramusic/app/data/remote/LrcLibApi;", "(Lcom/auramusic/app/data/remote/LrcLibApi;)V", "getLyrics", "Lcom/auramusic/app/core/util/Resource;", "Lcom/auramusic/app/domain/model/Lyrics;", "trackName", "", "artistName", "duration", "", "(Ljava/lang/String;Ljava/lang/String;DLkotlin/coroutines/Continuation;)Ljava/lang/Object;", "app_debug"})
public final class LyricsRepositoryImpl {
    @org.jetbrains.annotations.NotNull()
    private final com.auramusic.app.data.remote.LrcLibApi api = null;
    
    @javax.inject.Inject()
    public LyricsRepositoryImpl(@org.jetbrains.annotations.NotNull()
    com.auramusic.app.data.remote.LrcLibApi api) {
        super();
    }
    
    @org.jetbrains.annotations.Nullable()
    public final java.lang.Object getLyrics(@org.jetbrains.annotations.NotNull()
    java.lang.String trackName, @org.jetbrains.annotations.NotNull()
    java.lang.String artistName, double duration, @org.jetbrains.annotations.NotNull()
    kotlin.coroutines.Continuation<? super com.auramusic.app.core.util.Resource<com.auramusic.app.domain.model.Lyrics>> $completion) {
        return null;
    }
}