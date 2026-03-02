package com.auramusic.app.domain.usecase;

@kotlin.Metadata(mv = {1, 9, 0}, k = 1, xi = 48, d1 = {"\u0000(\n\u0002\u0018\u0002\n\u0002\u0010\u0000\n\u0000\n\u0002\u0018\u0002\n\u0002\b\u0002\n\u0002\u0018\u0002\n\u0002\u0010 \n\u0002\u0018\u0002\n\u0000\n\u0002\u0010\u000e\n\u0002\b\u0002\u0018\u00002\u00020\u0001B\u000f\b\u0007\u0012\u0006\u0010\u0002\u001a\u00020\u0003\u00a2\u0006\u0002\u0010\u0004J\"\u0010\u0005\u001a\u000e\u0012\n\u0012\b\u0012\u0004\u0012\u00020\b0\u00070\u00062\u0006\u0010\t\u001a\u00020\nH\u0086B\u00a2\u0006\u0002\u0010\u000bR\u000e\u0010\u0002\u001a\u00020\u0003X\u0082\u0004\u00a2\u0006\u0002\n\u0000\u00a8\u0006\f"}, d2 = {"Lcom/auramusic/app/domain/usecase/SearchSongsUseCase;", "", "repository", "Lcom/auramusic/app/domain/repository/StreamRepository;", "(Lcom/auramusic/app/domain/repository/StreamRepository;)V", "invoke", "Lcom/auramusic/app/core/util/Resource;", "", "Lcom/auramusic/app/domain/model/Song;", "query", "", "(Ljava/lang/String;Lkotlin/coroutines/Continuation;)Ljava/lang/Object;", "app_debug"})
public final class SearchSongsUseCase {
    @org.jetbrains.annotations.NotNull()
    private final com.auramusic.app.domain.repository.StreamRepository repository = null;
    
    @javax.inject.Inject()
    public SearchSongsUseCase(@org.jetbrains.annotations.NotNull()
    com.auramusic.app.domain.repository.StreamRepository repository) {
        super();
    }
    
    @org.jetbrains.annotations.Nullable()
    public final java.lang.Object invoke(@org.jetbrains.annotations.NotNull()
    java.lang.String query, @org.jetbrains.annotations.NotNull()
    kotlin.coroutines.Continuation<? super com.auramusic.app.core.util.Resource<java.util.List<com.auramusic.app.domain.model.Song>>> $completion) {
        return null;
    }
}