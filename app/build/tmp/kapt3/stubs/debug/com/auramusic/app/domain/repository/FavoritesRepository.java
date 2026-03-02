package com.auramusic.app.domain.repository;

@kotlin.Metadata(mv = {1, 9, 0}, k = 1, xi = 48, d1 = {"\u00004\n\u0002\u0018\u0002\n\u0002\u0010\u0000\n\u0000\n\u0002\u0018\u0002\n\u0002\u0010 \n\u0002\u0010\u000e\n\u0000\n\u0002\u0010\b\n\u0000\n\u0002\u0018\u0002\n\u0000\n\u0002\u0010\u000b\n\u0002\b\u0002\n\u0002\u0010\u0002\n\u0002\b\u0004\bf\u0018\u00002\u00020\u0001J\u0014\u0010\u0002\u001a\u000e\u0012\n\u0012\b\u0012\u0004\u0012\u00020\u00050\u00040\u0003H&J\u000e\u0010\u0006\u001a\b\u0012\u0004\u0012\u00020\u00070\u0003H&J\u0014\u0010\b\u001a\u000e\u0012\n\u0012\b\u0012\u0004\u0012\u00020\t0\u00040\u0003H&J\u0016\u0010\n\u001a\b\u0012\u0004\u0012\u00020\u000b0\u00032\u0006\u0010\f\u001a\u00020\u0005H&J\u0016\u0010\r\u001a\u00020\u000e2\u0006\u0010\u000f\u001a\u00020\tH\u00a6@\u00a2\u0006\u0002\u0010\u0010J\u0016\u0010\u0011\u001a\u00020\u000b2\u0006\u0010\u000f\u001a\u00020\tH\u00a6@\u00a2\u0006\u0002\u0010\u0010\u00a8\u0006\u0012"}, d2 = {"Lcom/auramusic/app/domain/repository/FavoritesRepository;", "", "getDistinctFavoriteArtists", "Lkotlinx/coroutines/flow/Flow;", "", "", "getFavoriteCount", "", "getFavorites", "Lcom/auramusic/app/domain/model/Song;", "isFavorite", "", "songId", "recordPlay", "", "song", "(Lcom/auramusic/app/domain/model/Song;Lkotlin/coroutines/Continuation;)Ljava/lang/Object;", "toggleFavorite", "app_debug"})
public abstract interface FavoritesRepository {
    
    /**
     * Observe all favorited songs, ordered by most recently liked
     */
    @org.jetbrains.annotations.NotNull()
    public abstract kotlinx.coroutines.flow.Flow<java.util.List<com.auramusic.app.domain.model.Song>> getFavorites();
    
    /**
     * Observe whether a specific song is favorited
     */
    @org.jetbrains.annotations.NotNull()
    public abstract kotlinx.coroutines.flow.Flow<java.lang.Boolean> isFavorite(@org.jetbrains.annotations.NotNull()
    java.lang.String songId);
    
    /**
     * Toggle the favorite status of a song. Returns the new isFavorite state.
     */
    @org.jetbrains.annotations.Nullable()
    public abstract java.lang.Object toggleFavorite(@org.jetbrains.annotations.NotNull()
    com.auramusic.app.domain.model.Song song, @org.jetbrains.annotations.NotNull()
    kotlin.coroutines.Continuation<? super java.lang.Boolean> $completion);
    
    /**
     * Get distinct artist names from favorites (for recommendation engine)
     */
    @org.jetbrains.annotations.NotNull()
    public abstract kotlinx.coroutines.flow.Flow<java.util.List<java.lang.String>> getDistinctFavoriteArtists();
    
    /**
     * Get total count of favorited songs
     */
    @org.jetbrains.annotations.NotNull()
    public abstract kotlinx.coroutines.flow.Flow<java.lang.Integer> getFavoriteCount();
    
    /**
     * Record that a song was played
     */
    @org.jetbrains.annotations.Nullable()
    public abstract java.lang.Object recordPlay(@org.jetbrains.annotations.NotNull()
    com.auramusic.app.domain.model.Song song, @org.jetbrains.annotations.NotNull()
    kotlin.coroutines.Continuation<? super kotlin.Unit> $completion);
}