package com.auramusic.app.domain.repository;

@kotlin.Metadata(mv = {1, 9, 0}, k = 1, xi = 48, d1 = {"\u0000(\n\u0002\u0018\u0002\n\u0002\u0010\u0000\n\u0000\n\u0002\u0018\u0002\n\u0002\u0010 \n\u0002\u0018\u0002\n\u0000\n\u0002\u0010\u000e\n\u0002\b\u0005\n\u0002\u0018\u0002\n\u0002\b\u0003\bf\u0018\u00002\u00020\u0001J\"\u0010\u0002\u001a\u000e\u0012\n\u0012\b\u0012\u0004\u0012\u00020\u00050\u00040\u00032\u0006\u0010\u0006\u001a\u00020\u0007H\u00a6@\u00a2\u0006\u0002\u0010\bJ\u001c\u0010\t\u001a\b\u0012\u0004\u0012\u00020\u00070\u00032\u0006\u0010\u0006\u001a\u00020\u0007H\u00a6@\u00a2\u0006\u0002\u0010\bJ\u001a\u0010\n\u001a\u000e\u0012\n\u0012\b\u0012\u0004\u0012\u00020\u00050\u00040\u0003H\u00a6@\u00a2\u0006\u0002\u0010\u000bJ\u001c\u0010\f\u001a\b\u0012\u0004\u0012\u00020\r0\u00032\u0006\u0010\u000e\u001a\u00020\u0007H\u00a6@\u00a2\u0006\u0002\u0010\bJ\"\u0010\u000f\u001a\u000e\u0012\n\u0012\b\u0012\u0004\u0012\u00020\u00050\u00040\u00032\u0006\u0010\u000e\u001a\u00020\u0007H\u00a6@\u00a2\u0006\u0002\u0010\b\u00a8\u0006\u0010"}, d2 = {"Lcom/auramusic/app/domain/repository/StreamRepository;", "", "getRelatedSongs", "Lcom/auramusic/app/core/util/Resource;", "", "Lcom/auramusic/app/domain/model/Song;", "songId", "", "(Ljava/lang/String;Lkotlin/coroutines/Continuation;)Ljava/lang/Object;", "getStreamInfo", "getTrending", "(Lkotlin/coroutines/Continuation;)Ljava/lang/Object;", "searchMixed", "Lcom/auramusic/app/domain/model/SearchResults;", "query", "searchSongs", "app_debug"})
public abstract interface StreamRepository {
    
    @org.jetbrains.annotations.Nullable()
    public abstract java.lang.Object searchSongs(@org.jetbrains.annotations.NotNull()
    java.lang.String query, @org.jetbrains.annotations.NotNull()
    kotlin.coroutines.Continuation<? super com.auramusic.app.core.util.Resource<java.util.List<com.auramusic.app.domain.model.Song>>> $completion);
    
    @org.jetbrains.annotations.Nullable()
    public abstract java.lang.Object searchMixed(@org.jetbrains.annotations.NotNull()
    java.lang.String query, @org.jetbrains.annotations.NotNull()
    kotlin.coroutines.Continuation<? super com.auramusic.app.core.util.Resource<com.auramusic.app.domain.model.SearchResults>> $completion);
    
    @org.jetbrains.annotations.Nullable()
    public abstract java.lang.Object getStreamInfo(@org.jetbrains.annotations.NotNull()
    java.lang.String songId, @org.jetbrains.annotations.NotNull()
    kotlin.coroutines.Continuation<? super com.auramusic.app.core.util.Resource<java.lang.String>> $completion);
    
    @org.jetbrains.annotations.Nullable()
    public abstract java.lang.Object getRelatedSongs(@org.jetbrains.annotations.NotNull()
    java.lang.String songId, @org.jetbrains.annotations.NotNull()
    kotlin.coroutines.Continuation<? super com.auramusic.app.core.util.Resource<java.util.List<com.auramusic.app.domain.model.Song>>> $completion);
    
    @org.jetbrains.annotations.Nullable()
    public abstract java.lang.Object getTrending(@org.jetbrains.annotations.NotNull()
    kotlin.coroutines.Continuation<? super com.auramusic.app.core.util.Resource<java.util.List<com.auramusic.app.domain.model.Song>>> $completion);
}