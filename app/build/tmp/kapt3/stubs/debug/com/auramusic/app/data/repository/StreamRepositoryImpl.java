package com.auramusic.app.data.repository;

@kotlin.Metadata(mv = {1, 9, 0}, k = 1, xi = 48, d1 = {"\u00000\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0002\b\u0002\n\u0002\u0018\u0002\n\u0002\u0010 \n\u0002\u0018\u0002\n\u0000\n\u0002\u0010\u000e\n\u0002\b\u0005\n\u0002\u0018\u0002\n\u0002\b\u0006\u0018\u0000 \u00152\u00020\u0001:\u0001\u0015B\u000f\b\u0007\u0012\u0006\u0010\u0002\u001a\u00020\u0003\u00a2\u0006\u0002\u0010\u0004J\"\u0010\u0005\u001a\u000e\u0012\n\u0012\b\u0012\u0004\u0012\u00020\b0\u00070\u00062\u0006\u0010\t\u001a\u00020\nH\u0096@\u00a2\u0006\u0002\u0010\u000bJ\u001c\u0010\f\u001a\b\u0012\u0004\u0012\u00020\n0\u00062\u0006\u0010\t\u001a\u00020\nH\u0096@\u00a2\u0006\u0002\u0010\u000bJ\u001a\u0010\r\u001a\u000e\u0012\n\u0012\b\u0012\u0004\u0012\u00020\b0\u00070\u0006H\u0096@\u00a2\u0006\u0002\u0010\u000eJ\u001c\u0010\u000f\u001a\b\u0012\u0004\u0012\u00020\u00100\u00062\u0006\u0010\u0011\u001a\u00020\nH\u0096@\u00a2\u0006\u0002\u0010\u000bJ\"\u0010\u0012\u001a\u000e\u0012\n\u0012\b\u0012\u0004\u0012\u00020\b0\u00070\u00062\u0006\u0010\u0011\u001a\u00020\nH\u0096@\u00a2\u0006\u0002\u0010\u000bJ\u001c\u0010\u0013\u001a\b\u0012\u0004\u0012\u00020\n0\u00062\u0006\u0010\t\u001a\u00020\nH\u0082@\u00a2\u0006\u0002\u0010\u000bJ\u001c\u0010\u0014\u001a\b\u0012\u0004\u0012\u00020\n0\u00062\u0006\u0010\t\u001a\u00020\nH\u0082@\u00a2\u0006\u0002\u0010\u000bR\u000e\u0010\u0002\u001a\u00020\u0003X\u0082\u0004\u00a2\u0006\u0002\n\u0000\u00a8\u0006\u0016"}, d2 = {"Lcom/auramusic/app/data/repository/StreamRepositoryImpl;", "Lcom/auramusic/app/domain/repository/StreamRepository;", "api", "Lcom/auramusic/app/data/remote/PipedApi;", "(Lcom/auramusic/app/data/remote/PipedApi;)V", "getRelatedSongs", "Lcom/auramusic/app/core/util/Resource;", "", "Lcom/auramusic/app/domain/model/Song;", "songId", "", "(Ljava/lang/String;Lkotlin/coroutines/Continuation;)Ljava/lang/Object;", "getStreamInfo", "getTrending", "(Lkotlin/coroutines/Continuation;)Ljava/lang/Object;", "searchMixed", "Lcom/auramusic/app/domain/model/SearchResults;", "query", "searchSongs", "tryNewPipeExtraction", "tryPipedApi", "Companion", "app_debug"})
public final class StreamRepositoryImpl implements com.auramusic.app.domain.repository.StreamRepository {
    @org.jetbrains.annotations.NotNull()
    private final com.auramusic.app.data.remote.PipedApi api = null;
    @org.jetbrains.annotations.NotNull()
    private static final java.lang.String TAG = "XploitDebug";
    @org.jetbrains.annotations.NotNull()
    public static final com.auramusic.app.data.repository.StreamRepositoryImpl.Companion Companion = null;
    
    @javax.inject.Inject()
    public StreamRepositoryImpl(@org.jetbrains.annotations.NotNull()
    com.auramusic.app.data.remote.PipedApi api) {
        super();
    }
    
    @java.lang.Override()
    @org.jetbrains.annotations.Nullable()
    public java.lang.Object searchSongs(@org.jetbrains.annotations.NotNull()
    java.lang.String query, @org.jetbrains.annotations.NotNull()
    kotlin.coroutines.Continuation<? super com.auramusic.app.core.util.Resource<java.util.List<com.auramusic.app.domain.model.Song>>> $completion) {
        return null;
    }
    
    @java.lang.Override()
    @org.jetbrains.annotations.Nullable()
    public java.lang.Object searchMixed(@org.jetbrains.annotations.NotNull()
    java.lang.String query, @org.jetbrains.annotations.NotNull()
    kotlin.coroutines.Continuation<? super com.auramusic.app.core.util.Resource<com.auramusic.app.domain.model.SearchResults>> $completion) {
        return null;
    }
    
    @java.lang.Override()
    @org.jetbrains.annotations.Nullable()
    public java.lang.Object getStreamInfo(@org.jetbrains.annotations.NotNull()
    java.lang.String songId, @org.jetbrains.annotations.NotNull()
    kotlin.coroutines.Continuation<? super com.auramusic.app.core.util.Resource<java.lang.String>> $completion) {
        return null;
    }
    
    @java.lang.Override()
    @org.jetbrains.annotations.Nullable()
    public java.lang.Object getRelatedSongs(@org.jetbrains.annotations.NotNull()
    java.lang.String songId, @org.jetbrains.annotations.NotNull()
    kotlin.coroutines.Continuation<? super com.auramusic.app.core.util.Resource<java.util.List<com.auramusic.app.domain.model.Song>>> $completion) {
        return null;
    }
    
    @java.lang.Override()
    @org.jetbrains.annotations.Nullable()
    public java.lang.Object getTrending(@org.jetbrains.annotations.NotNull()
    kotlin.coroutines.Continuation<? super com.auramusic.app.core.util.Resource<java.util.List<com.auramusic.app.domain.model.Song>>> $completion) {
        return null;
    }
    
    private final java.lang.Object tryNewPipeExtraction(java.lang.String songId, kotlin.coroutines.Continuation<? super com.auramusic.app.core.util.Resource<java.lang.String>> $completion) {
        return null;
    }
    
    private final java.lang.Object tryPipedApi(java.lang.String songId, kotlin.coroutines.Continuation<? super com.auramusic.app.core.util.Resource<java.lang.String>> $completion) {
        return null;
    }
    
    @kotlin.Metadata(mv = {1, 9, 0}, k = 1, xi = 48, d1 = {"\u0000\u0012\n\u0002\u0018\u0002\n\u0002\u0010\u0000\n\u0002\b\u0002\n\u0002\u0010\u000e\n\u0000\b\u0086\u0003\u0018\u00002\u00020\u0001B\u0007\b\u0002\u00a2\u0006\u0002\u0010\u0002R\u000e\u0010\u0003\u001a\u00020\u0004X\u0082T\u00a2\u0006\u0002\n\u0000\u00a8\u0006\u0005"}, d2 = {"Lcom/auramusic/app/data/repository/StreamRepositoryImpl$Companion;", "", "()V", "TAG", "", "app_debug"})
    public static final class Companion {
        
        private Companion() {
            super();
        }
    }
}