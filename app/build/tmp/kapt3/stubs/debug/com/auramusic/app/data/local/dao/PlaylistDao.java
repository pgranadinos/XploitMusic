package com.auramusic.app.data.local.dao;

@kotlin.Metadata(mv = {1, 9, 0}, k = 1, xi = 48, d1 = {"\u0000@\n\u0002\u0018\u0002\n\u0002\u0010\u0000\n\u0000\n\u0002\u0010\u0002\n\u0000\n\u0002\u0018\u0002\n\u0002\b\u0002\n\u0002\u0010\t\n\u0000\n\u0002\u0018\u0002\n\u0002\b\u0005\n\u0002\u0018\u0002\n\u0002\u0010 \n\u0000\n\u0002\u0018\u0002\n\u0002\b\u0003\n\u0002\u0010\u000e\n\u0002\b\u0002\bg\u0018\u00002\u00020\u0001J\u0016\u0010\u0002\u001a\u00020\u00032\u0006\u0010\u0004\u001a\u00020\u0005H\u00a7@\u00a2\u0006\u0002\u0010\u0006J\u0016\u0010\u0007\u001a\u00020\b2\u0006\u0010\t\u001a\u00020\nH\u00a7@\u00a2\u0006\u0002\u0010\u000bJ\u0016\u0010\f\u001a\u00020\u00032\u0006\u0010\r\u001a\u00020\bH\u00a7@\u00a2\u0006\u0002\u0010\u000eJ\u0014\u0010\u000f\u001a\u000e\u0012\n\u0012\b\u0012\u0004\u0012\u00020\n0\u00110\u0010H\'J\u0014\u0010\u0012\u001a\u000e\u0012\n\u0012\b\u0012\u0004\u0012\u00020\u00130\u00110\u0010H\'J\u0018\u0010\u0014\u001a\n\u0012\u0006\u0012\u0004\u0018\u00010\u00130\u00102\u0006\u0010\r\u001a\u00020\bH\'J\u001e\u0010\u0015\u001a\u00020\u00032\u0006\u0010\r\u001a\u00020\b2\u0006\u0010\u0016\u001a\u00020\u0017H\u00a7@\u00a2\u0006\u0002\u0010\u0018\u00a8\u0006\u0019"}, d2 = {"Lcom/auramusic/app/data/local/dao/PlaylistDao;", "", "addSongToPlaylist", "", "crossRef", "Lcom/auramusic/app/data/local/entity/PlaylistSongCrossRef;", "(Lcom/auramusic/app/data/local/entity/PlaylistSongCrossRef;Lkotlin/coroutines/Continuation;)Ljava/lang/Object;", "createPlaylist", "", "playlist", "Lcom/auramusic/app/data/local/entity/PlaylistEntity;", "(Lcom/auramusic/app/data/local/entity/PlaylistEntity;Lkotlin/coroutines/Continuation;)Ljava/lang/Object;", "deletePlaylist", "playlistId", "(JLkotlin/coroutines/Continuation;)Ljava/lang/Object;", "getAllPlaylists", "Lkotlinx/coroutines/flow/Flow;", "", "getAllPlaylistsWithSongs", "Lcom/auramusic/app/data/local/entity/PlaylistWithSongs;", "getPlaylistWithSongs", "removeSongFromPlaylist", "songId", "", "(JLjava/lang/String;Lkotlin/coroutines/Continuation;)Ljava/lang/Object;", "app_debug"})
@androidx.room.Dao()
public abstract interface PlaylistDao {
    
    @androidx.room.Insert(onConflict = 1)
    @org.jetbrains.annotations.Nullable()
    public abstract java.lang.Object createPlaylist(@org.jetbrains.annotations.NotNull()
    com.auramusic.app.data.local.entity.PlaylistEntity playlist, @org.jetbrains.annotations.NotNull()
    kotlin.coroutines.Continuation<? super java.lang.Long> $completion);
    
    @androidx.room.Query(value = "DELETE FROM playlists WHERE playlistId = :playlistId")
    @org.jetbrains.annotations.Nullable()
    public abstract java.lang.Object deletePlaylist(long playlistId, @org.jetbrains.annotations.NotNull()
    kotlin.coroutines.Continuation<? super kotlin.Unit> $completion);
    
    @androidx.room.Query(value = "SELECT * FROM playlists ORDER BY createdAt DESC")
    @org.jetbrains.annotations.NotNull()
    public abstract kotlinx.coroutines.flow.Flow<java.util.List<com.auramusic.app.data.local.entity.PlaylistEntity>> getAllPlaylists();
    
    @androidx.room.Insert(onConflict = 5)
    @org.jetbrains.annotations.Nullable()
    public abstract java.lang.Object addSongToPlaylist(@org.jetbrains.annotations.NotNull()
    com.auramusic.app.data.local.entity.PlaylistSongCrossRef crossRef, @org.jetbrains.annotations.NotNull()
    kotlin.coroutines.Continuation<? super kotlin.Unit> $completion);
    
    @androidx.room.Query(value = "DELETE FROM playlist_song_cross_ref WHERE playlistId = :playlistId AND songId = :songId")
    @org.jetbrains.annotations.Nullable()
    public abstract java.lang.Object removeSongFromPlaylist(long playlistId, @org.jetbrains.annotations.NotNull()
    java.lang.String songId, @org.jetbrains.annotations.NotNull()
    kotlin.coroutines.Continuation<? super kotlin.Unit> $completion);
    
    @androidx.room.Transaction()
    @androidx.room.Query(value = "SELECT * FROM playlists WHERE playlistId = :playlistId")
    @org.jetbrains.annotations.NotNull()
    public abstract kotlinx.coroutines.flow.Flow<com.auramusic.app.data.local.entity.PlaylistWithSongs> getPlaylistWithSongs(long playlistId);
    
    @androidx.room.Transaction()
    @androidx.room.Query(value = "SELECT * FROM playlists ORDER BY createdAt DESC")
    @org.jetbrains.annotations.NotNull()
    public abstract kotlinx.coroutines.flow.Flow<java.util.List<com.auramusic.app.data.local.entity.PlaylistWithSongs>> getAllPlaylistsWithSongs();
}