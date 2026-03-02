package com.auramusic.app.data.local;

@kotlin.Metadata(mv = {1, 9, 0}, k = 1, xi = 48, d1 = {"\u0000\u0018\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0002\b\u0002\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0000\b\'\u0018\u00002\u00020\u0001B\u0005\u00a2\u0006\u0002\u0010\u0002J\b\u0010\u0003\u001a\u00020\u0004H&J\b\u0010\u0005\u001a\u00020\u0006H&\u00a8\u0006\u0007"}, d2 = {"Lcom/auramusic/app/data/local/AuraDatabase;", "Landroidx/room/RoomDatabase;", "()V", "playlistDao", "Lcom/auramusic/app/data/local/dao/PlaylistDao;", "songDao", "Lcom/auramusic/app/data/local/dao/SongDao;", "app_debug"})
@androidx.room.Database(entities = {com.auramusic.app.data.local.entity.SongEntity.class, com.auramusic.app.data.local.entity.PlaylistEntity.class, com.auramusic.app.data.local.entity.PlaylistSongCrossRef.class}, version = 3, exportSchema = false)
public abstract class AuraDatabase extends androidx.room.RoomDatabase {
    
    public AuraDatabase() {
        super();
    }
    
    @org.jetbrains.annotations.NotNull()
    public abstract com.auramusic.app.data.local.dao.SongDao songDao();
    
    @org.jetbrains.annotations.NotNull()
    public abstract com.auramusic.app.data.local.dao.PlaylistDao playlistDao();
}