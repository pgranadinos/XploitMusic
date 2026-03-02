package com.auramusic.app.data.repository;

@kotlin.Metadata(mv = {1, 9, 0}, k = 1, xi = 48, d1 = {"\u0000D\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0002\b\u0002\n\u0002\u0018\u0002\n\u0002\u0010 \n\u0002\u0010\u000e\n\u0000\n\u0002\u0010\b\n\u0000\n\u0002\u0018\u0002\n\u0000\n\u0002\u0010\u000b\n\u0002\b\u0002\n\u0002\u0010\u0002\n\u0002\b\u0004\n\u0002\u0018\u0002\n\u0002\b\u0002\u0018\u00002\u00020\u0001B\u000f\b\u0007\u0012\u0006\u0010\u0002\u001a\u00020\u0003\u00a2\u0006\u0002\u0010\u0004J\u0014\u0010\u0005\u001a\u000e\u0012\n\u0012\b\u0012\u0004\u0012\u00020\b0\u00070\u0006H\u0016J\u000e\u0010\t\u001a\b\u0012\u0004\u0012\u00020\n0\u0006H\u0016J\u0014\u0010\u000b\u001a\u000e\u0012\n\u0012\b\u0012\u0004\u0012\u00020\f0\u00070\u0006H\u0016J\u0016\u0010\r\u001a\b\u0012\u0004\u0012\u00020\u000e0\u00062\u0006\u0010\u000f\u001a\u00020\bH\u0016J\u0016\u0010\u0010\u001a\u00020\u00112\u0006\u0010\u0012\u001a\u00020\fH\u0096@\u00a2\u0006\u0002\u0010\u0013J\u0016\u0010\u0014\u001a\u00020\u000e2\u0006\u0010\u0012\u001a\u00020\fH\u0096@\u00a2\u0006\u0002\u0010\u0013J\f\u0010\u0015\u001a\u00020\f*\u00020\u0016H\u0002J\u0014\u0010\u0017\u001a\u00020\u0016*\u00020\f2\u0006\u0010\r\u001a\u00020\u000eH\u0002R\u000e\u0010\u0002\u001a\u00020\u0003X\u0082\u0004\u00a2\u0006\u0002\n\u0000\u00a8\u0006\u0018"}, d2 = {"Lcom/auramusic/app/data/repository/FavoritesRepositoryImpl;", "Lcom/auramusic/app/domain/repository/FavoritesRepository;", "songDao", "Lcom/auramusic/app/data/local/dao/SongDao;", "(Lcom/auramusic/app/data/local/dao/SongDao;)V", "getDistinctFavoriteArtists", "Lkotlinx/coroutines/flow/Flow;", "", "", "getFavoriteCount", "", "getFavorites", "Lcom/auramusic/app/domain/model/Song;", "isFavorite", "", "songId", "recordPlay", "", "song", "(Lcom/auramusic/app/domain/model/Song;Lkotlin/coroutines/Continuation;)Ljava/lang/Object;", "toggleFavorite", "toDomain", "Lcom/auramusic/app/data/local/entity/SongEntity;", "toEntity", "app_debug"})
public final class FavoritesRepositoryImpl implements com.auramusic.app.domain.repository.FavoritesRepository {
    @org.jetbrains.annotations.NotNull()
    private final com.auramusic.app.data.local.dao.SongDao songDao = null;
    
    @javax.inject.Inject()
    public FavoritesRepositoryImpl(@org.jetbrains.annotations.NotNull()
    com.auramusic.app.data.local.dao.SongDao songDao) {
        super();
    }
    
    @java.lang.Override()
    @org.jetbrains.annotations.NotNull()
    public kotlinx.coroutines.flow.Flow<java.util.List<com.auramusic.app.domain.model.Song>> getFavorites() {
        return null;
    }
    
    @java.lang.Override()
    @org.jetbrains.annotations.NotNull()
    public kotlinx.coroutines.flow.Flow<java.lang.Boolean> isFavorite(@org.jetbrains.annotations.NotNull()
    java.lang.String songId) {
        return null;
    }
    
    @java.lang.Override()
    @org.jetbrains.annotations.Nullable()
    public java.lang.Object toggleFavorite(@org.jetbrains.annotations.NotNull()
    com.auramusic.app.domain.model.Song song, @org.jetbrains.annotations.NotNull()
    kotlin.coroutines.Continuation<? super java.lang.Boolean> $completion) {
        return null;
    }
    
    @java.lang.Override()
    @org.jetbrains.annotations.NotNull()
    public kotlinx.coroutines.flow.Flow<java.util.List<java.lang.String>> getDistinctFavoriteArtists() {
        return null;
    }
    
    @java.lang.Override()
    @org.jetbrains.annotations.NotNull()
    public kotlinx.coroutines.flow.Flow<java.lang.Integer> getFavoriteCount() {
        return null;
    }
    
    @java.lang.Override()
    @org.jetbrains.annotations.Nullable()
    public java.lang.Object recordPlay(@org.jetbrains.annotations.NotNull()
    com.auramusic.app.domain.model.Song song, @org.jetbrains.annotations.NotNull()
    kotlin.coroutines.Continuation<? super kotlin.Unit> $completion) {
        return null;
    }
    
    private final com.auramusic.app.domain.model.Song toDomain(com.auramusic.app.data.local.entity.SongEntity $this$toDomain) {
        return null;
    }
    
    private final com.auramusic.app.data.local.entity.SongEntity toEntity(com.auramusic.app.domain.model.Song $this$toEntity, boolean isFavorite) {
        return null;
    }
}