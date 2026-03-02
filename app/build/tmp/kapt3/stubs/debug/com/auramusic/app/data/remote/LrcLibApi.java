package com.auramusic.app.data.remote;

@kotlin.Metadata(mv = {1, 9, 0}, k = 1, xi = 48, d1 = {"\u0000(\n\u0002\u0018\u0002\n\u0002\u0010\u0000\n\u0000\n\u0002\u0018\u0002\n\u0000\n\u0002\u0010\u000e\n\u0002\b\u0002\n\u0002\u0010\u0006\n\u0002\b\u0002\n\u0002\u0010 \n\u0002\b\u0003\bf\u0018\u00002\u00020\u0001J,\u0010\u0002\u001a\u00020\u00032\b\b\u0001\u0010\u0004\u001a\u00020\u00052\b\b\u0001\u0010\u0006\u001a\u00020\u00052\b\b\u0001\u0010\u0007\u001a\u00020\bH\u00a7@\u00a2\u0006\u0002\u0010\tJ\u001e\u0010\n\u001a\b\u0012\u0004\u0012\u00020\u00030\u000b2\b\b\u0001\u0010\f\u001a\u00020\u0005H\u00a7@\u00a2\u0006\u0002\u0010\r\u00a8\u0006\u000e"}, d2 = {"Lcom/auramusic/app/data/remote/LrcLibApi;", "", "getLyrics", "Lcom/auramusic/app/data/dto/LrcLibResponse;", "trackName", "", "artistName", "duration", "", "(Ljava/lang/String;Ljava/lang/String;DLkotlin/coroutines/Continuation;)Ljava/lang/Object;", "searchLyrics", "", "query", "(Ljava/lang/String;Lkotlin/coroutines/Continuation;)Ljava/lang/Object;", "app_debug"})
public abstract interface LrcLibApi {
    
    @retrofit2.http.GET(value = "api/get")
    @org.jetbrains.annotations.Nullable()
    public abstract java.lang.Object getLyrics(@retrofit2.http.Query(value = "track_name")
    @org.jetbrains.annotations.NotNull()
    java.lang.String trackName, @retrofit2.http.Query(value = "artist_name")
    @org.jetbrains.annotations.NotNull()
    java.lang.String artistName, @retrofit2.http.Query(value = "duration")
    double duration, @org.jetbrains.annotations.NotNull()
    kotlin.coroutines.Continuation<? super com.auramusic.app.data.dto.LrcLibResponse> $completion);
    
    @retrofit2.http.GET(value = "api/search")
    @org.jetbrains.annotations.Nullable()
    public abstract java.lang.Object searchLyrics(@retrofit2.http.Query(value = "q")
    @org.jetbrains.annotations.NotNull()
    java.lang.String query, @org.jetbrains.annotations.NotNull()
    kotlin.coroutines.Continuation<? super java.util.List<com.auramusic.app.data.dto.LrcLibResponse>> $completion);
}