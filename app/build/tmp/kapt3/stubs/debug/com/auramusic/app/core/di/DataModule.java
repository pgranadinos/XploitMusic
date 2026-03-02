package com.auramusic.app.core.di;

@dagger.Module()
@kotlin.Metadata(mv = {1, 9, 0}, k = 1, xi = 48, d1 = {"\u00006\n\u0002\u0018\u0002\n\u0002\u0010\u0000\n\u0002\b\u0002\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0000\b\'\u0018\u00002\u00020\u0001B\u0005\u00a2\u0006\u0002\u0010\u0002J\u0010\u0010\u0003\u001a\u00020\u00042\u0006\u0010\u0005\u001a\u00020\u0006H\'J\u0010\u0010\u0007\u001a\u00020\b2\u0006\u0010\u0005\u001a\u00020\tH\'J\u0010\u0010\n\u001a\u00020\u000b2\u0006\u0010\u0005\u001a\u00020\fH\'J\u0010\u0010\r\u001a\u00020\u000e2\u0006\u0010\u0005\u001a\u00020\u000fH\'\u00a8\u0006\u0010"}, d2 = {"Lcom/auramusic/app/core/di/DataModule;", "", "()V", "bindFavoritesRepository", "Lcom/auramusic/app/domain/repository/FavoritesRepository;", "impl", "Lcom/auramusic/app/data/repository/FavoritesRepositoryImpl;", "bindLocalAudioRepository", "Lcom/auramusic/app/domain/repository/LocalAudioRepository;", "Lcom/auramusic/app/data/repository/LocalAudioRepositoryImpl;", "bindPlayerRepository", "Lcom/auramusic/app/domain/repository/PlayerRepository;", "Lcom/auramusic/app/data/repository/PlayerRepositoryImpl;", "bindStreamRepository", "Lcom/auramusic/app/domain/repository/StreamRepository;", "Lcom/auramusic/app/data/repository/StreamRepositoryImpl;", "app_debug"})
@dagger.hilt.InstallIn(value = {dagger.hilt.components.SingletonComponent.class})
public abstract class DataModule {
    
    public DataModule() {
        super();
    }
    
    @dagger.Binds()
    @javax.inject.Singleton()
    @org.jetbrains.annotations.NotNull()
    public abstract com.auramusic.app.domain.repository.PlayerRepository bindPlayerRepository(@org.jetbrains.annotations.NotNull()
    com.auramusic.app.data.repository.PlayerRepositoryImpl impl);
    
    @dagger.Binds()
    @javax.inject.Singleton()
    @org.jetbrains.annotations.NotNull()
    public abstract com.auramusic.app.domain.repository.StreamRepository bindStreamRepository(@org.jetbrains.annotations.NotNull()
    com.auramusic.app.data.repository.StreamRepositoryImpl impl);
    
    @dagger.Binds()
    @javax.inject.Singleton()
    @org.jetbrains.annotations.NotNull()
    public abstract com.auramusic.app.domain.repository.FavoritesRepository bindFavoritesRepository(@org.jetbrains.annotations.NotNull()
    com.auramusic.app.data.repository.FavoritesRepositoryImpl impl);
    
    @dagger.Binds()
    @javax.inject.Singleton()
    @org.jetbrains.annotations.NotNull()
    public abstract com.auramusic.app.domain.repository.LocalAudioRepository bindLocalAudioRepository(@org.jetbrains.annotations.NotNull()
    com.auramusic.app.data.repository.LocalAudioRepositoryImpl impl);
}