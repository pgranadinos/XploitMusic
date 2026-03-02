package com.auramusic.app.data.local.dao;

@kotlin.Metadata(mv = {1, 9, 0}, k = 1, xi = 48, d1 = {"\u0000@\n\u0002\u0018\u0002\n\u0002\u0010\u0000\n\u0000\n\u0002\u0010\u0002\n\u0000\n\u0002\u0010\u000e\n\u0002\b\u0002\n\u0002\u0018\u0002\n\u0002\u0010 \n\u0000\n\u0002\u0010\b\n\u0000\n\u0002\u0018\u0002\n\u0002\b\u0006\n\u0002\u0010\u000b\n\u0002\b\u0002\n\u0002\u0010\t\n\u0002\b\u0005\bg\u0018\u00002\u00020\u0001J\u0016\u0010\u0002\u001a\u00020\u00032\u0006\u0010\u0004\u001a\u00020\u0005H\u00a7@\u00a2\u0006\u0002\u0010\u0006J\u0014\u0010\u0007\u001a\u000e\u0012\n\u0012\b\u0012\u0004\u0012\u00020\u00050\t0\bH\'J\u000e\u0010\n\u001a\b\u0012\u0004\u0012\u00020\u000b0\bH\'J\u0014\u0010\f\u001a\u000e\u0012\n\u0012\b\u0012\u0004\u0012\u00020\r0\t0\bH\'J\u0014\u0010\u000e\u001a\u000e\u0012\n\u0012\b\u0012\u0004\u0012\u00020\r0\t0\bH\'J\u0018\u0010\u000f\u001a\u0004\u0018\u00010\r2\u0006\u0010\u0004\u001a\u00020\u0005H\u00a7@\u00a2\u0006\u0002\u0010\u0006J\u0016\u0010\u0010\u001a\u00020\u00032\u0006\u0010\u0011\u001a\u00020\rH\u00a7@\u00a2\u0006\u0002\u0010\u0012J\u0018\u0010\u0013\u001a\n\u0012\u0006\u0012\u0004\u0018\u00010\u00140\b2\u0006\u0010\u0004\u001a\u00020\u0005H\'J(\u0010\u0015\u001a\u00020\u00032\u0006\u0010\u0004\u001a\u00020\u00052\u0006\u0010\u0013\u001a\u00020\u00142\b\u0010\u0016\u001a\u0004\u0018\u00010\u0017H\u00a7@\u00a2\u0006\u0002\u0010\u0018J\u001e\u0010\u0019\u001a\u00020\u00032\u0006\u0010\u0004\u001a\u00020\u00052\u0006\u0010\u001a\u001a\u00020\u0017H\u00a7@\u00a2\u0006\u0002\u0010\u001b\u00a8\u0006\u001c"}, d2 = {"Lcom/auramusic/app/data/local/dao/SongDao;", "", "deleteSong", "", "songId", "", "(Ljava/lang/String;Lkotlin/coroutines/Continuation;)Ljava/lang/Object;", "getDistinctFavoriteArtists", "Lkotlinx/coroutines/flow/Flow;", "", "getFavoriteCount", "", "getFavorites", "Lcom/auramusic/app/data/local/entity/SongEntity;", "getRecents", "getSongById", "insertSong", "song", "(Lcom/auramusic/app/data/local/entity/SongEntity;Lkotlin/coroutines/Continuation;)Ljava/lang/Object;", "isFavorite", "", "setFavorite", "likedAt", "", "(Ljava/lang/String;ZLjava/lang/Long;Lkotlin/coroutines/Continuation;)Ljava/lang/Object;", "updateLastPlayed", "timestamp", "(Ljava/lang/String;JLkotlin/coroutines/Continuation;)Ljava/lang/Object;", "app_debug"})
@androidx.room.Dao()
public abstract interface SongDao {
    
    @androidx.room.Query(value = "SELECT * FROM songs WHERE isFavorite = 1 ORDER BY likedAt DESC")
    @org.jetbrains.annotations.NotNull()
    public abstract kotlinx.coroutines.flow.Flow<java.util.List<com.auramusic.app.data.local.entity.SongEntity>> getFavorites();
    
    @androidx.room.Query(value = "SELECT isFavorite FROM songs WHERE id = :songId")
    @org.jetbrains.annotations.NotNull()
    public abstract kotlinx.coroutines.flow.Flow<java.lang.Boolean> isFavorite(@org.jetbrains.annotations.NotNull()
    java.lang.String songId);
    
    @androidx.room.Query(value = "SELECT COUNT(*) FROM songs WHERE isFavorite = 1")
    @org.jetbrains.annotations.NotNull()
    public abstract kotlinx.coroutines.flow.Flow<java.lang.Integer> getFavoriteCount();
    
    @androidx.room.Query(value = "SELECT DISTINCT artist FROM songs WHERE isFavorite = 1 ORDER BY likedAt DESC")
    @org.jetbrains.annotations.NotNull()
    public abstract kotlinx.coroutines.flow.Flow<java.util.List<java.lang.String>> getDistinctFavoriteArtists();
    
    @androidx.room.Query(value = "SELECT * FROM songs ORDER BY lastPlayedAt DESC LIMIT 20")
    @org.jetbrains.annotations.NotNull()
    public abstract kotlinx.coroutines.flow.Flow<java.util.List<com.auramusic.app.data.local.entity.SongEntity>> getRecents();
    
    @androidx.room.Insert(onConflict = 1)
    @org.jetbrains.annotations.Nullable()
    public abstract java.lang.Object insertSong(@org.jetbrains.annotations.NotNull()
    com.auramusic.app.data.local.entity.SongEntity song, @org.jetbrains.annotations.NotNull()
    kotlin.coroutines.Continuation<? super kotlin.Unit> $completion);
    
    @androidx.room.Query(value = "DELETE FROM songs WHERE id = :songId")
    @org.jetbrains.annotations.Nullable()
    public abstract java.lang.Object deleteSong(@org.jetbrains.annotations.NotNull()
    java.lang.String songId, @org.jetbrains.annotations.NotNull()
    kotlin.coroutines.Continuation<? super kotlin.Unit> $completion);
    
    @androidx.room.Query(value = "UPDATE songs SET isFavorite = :isFavorite, likedAt = :likedAt WHERE id = :songId")
    @org.jetbrains.annotations.Nullable()
    public abstract java.lang.Object setFavorite(@org.jetbrains.annotations.NotNull()
    java.lang.String songId, boolean isFavorite, @org.jetbrains.annotations.Nullable()
    java.lang.Long likedAt, @org.jetbrains.annotations.NotNull()
    kotlin.coroutines.Continuation<? super kotlin.Unit> $completion);
    
    @androidx.room.Query(value = "UPDATE songs SET lastPlayedAt = :timestamp WHERE id = :songId")
    @org.jetbrains.annotations.Nullable()
    public abstract java.lang.Object updateLastPlayed(@org.jetbrains.annotations.NotNull()
    java.lang.String songId, long timestamp, @org.jetbrains.annotations.NotNull()
    kotlin.coroutines.Continuation<? super kotlin.Unit> $completion);
    
    @androidx.room.Query(value = "SELECT * FROM songs WHERE id = :songId")
    @org.jetbrains.annotations.Nullable()
    public abstract java.lang.Object getSongById(@org.jetbrains.annotations.NotNull()
    java.lang.String songId, @org.jetbrains.annotations.NotNull()
    kotlin.coroutines.Continuation<? super com.auramusic.app.data.local.entity.SongEntity> $completion);
}