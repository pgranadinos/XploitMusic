package com.auramusic.app.domain.usecase;

@kotlin.Metadata(mv = {1, 9, 0}, k = 1, xi = 48, d1 = {"\u0000 \n\u0002\u0018\u0002\n\u0002\u0010\u0000\n\u0000\n\u0002\u0018\u0002\n\u0002\b\u0002\n\u0002\u0010\u000b\n\u0000\n\u0002\u0018\u0002\n\u0002\b\u0002\u0018\u00002\u00020\u0001B\u000f\b\u0007\u0012\u0006\u0010\u0002\u001a\u00020\u0003\u00a2\u0006\u0002\u0010\u0004J\u0016\u0010\u0005\u001a\u00020\u00062\u0006\u0010\u0007\u001a\u00020\bH\u0086B\u00a2\u0006\u0002\u0010\tR\u000e\u0010\u0002\u001a\u00020\u0003X\u0082\u0004\u00a2\u0006\u0002\n\u0000\u00a8\u0006\n"}, d2 = {"Lcom/auramusic/app/domain/usecase/ToggleFavoriteUseCase;", "", "favoritesRepository", "Lcom/auramusic/app/domain/repository/FavoritesRepository;", "(Lcom/auramusic/app/domain/repository/FavoritesRepository;)V", "invoke", "", "song", "Lcom/auramusic/app/domain/model/Song;", "(Lcom/auramusic/app/domain/model/Song;Lkotlin/coroutines/Continuation;)Ljava/lang/Object;", "app_debug"})
public final class ToggleFavoriteUseCase {
    @org.jetbrains.annotations.NotNull()
    private final com.auramusic.app.domain.repository.FavoritesRepository favoritesRepository = null;
    
    @javax.inject.Inject()
    public ToggleFavoriteUseCase(@org.jetbrains.annotations.NotNull()
    com.auramusic.app.domain.repository.FavoritesRepository favoritesRepository) {
        super();
    }
    
    /**
     * Toggles favorite and returns the new isFavorite state
     */
    @org.jetbrains.annotations.Nullable()
    public final java.lang.Object invoke(@org.jetbrains.annotations.NotNull()
    com.auramusic.app.domain.model.Song song, @org.jetbrains.annotations.NotNull()
    kotlin.coroutines.Continuation<? super java.lang.Boolean> $completion) {
        return null;
    }
}