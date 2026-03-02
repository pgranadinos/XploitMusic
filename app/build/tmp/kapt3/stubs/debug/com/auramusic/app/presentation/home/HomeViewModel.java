package com.auramusic.app.presentation.home;

@kotlin.Metadata(mv = {1, 9, 0}, k = 1, xi = 48, d1 = {"\u0000N\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0002\b\u0002\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0000\n\u0002\u0010 \n\u0002\u0018\u0002\n\u0000\n\u0002\u0010\u000e\n\u0002\b\u0004\n\u0002\u0018\u0002\n\u0002\b\u000f\n\u0002\u0010\u0002\n\u0002\b\u0006\b\u0007\u0018\u0000 +2\u00020\u0001:\u0001+B\u001f\b\u0007\u0012\u0006\u0010\u0002\u001a\u00020\u0003\u0012\u0006\u0010\u0004\u001a\u00020\u0005\u0012\u0006\u0010\u0006\u001a\u00020\u0007\u00a2\u0006\u0002\u0010\bJ\b\u0010%\u001a\u00020&H\u0002J\b\u0010\'\u001a\u00020&H\u0002J\u000e\u0010(\u001a\u00020&2\u0006\u0010)\u001a\u00020\u0011J\u0010\u0010*\u001a\u00020&2\u0006\u0010)\u001a\u00020\u0011H\u0002R\u001c\u0010\t\u001a\u0010\u0012\f\u0012\n\u0012\u0004\u0012\u00020\f\u0018\u00010\u000b0\nX\u0082\u0004\u00a2\u0006\u0002\n\u0000R\u001a\u0010\r\u001a\u000e\u0012\n\u0012\b\u0012\u0004\u0012\u00020\u000f0\u000e0\nX\u0082\u0004\u00a2\u0006\u0002\n\u0000R\u0014\u0010\u0010\u001a\b\u0012\u0004\u0012\u00020\u00110\nX\u0082\u0004\u00a2\u0006\u0002\n\u0000R\u0014\u0010\u0012\u001a\b\u0012\u0004\u0012\u00020\u00110\nX\u0082\u0004\u00a2\u0006\u0002\n\u0000R\"\u0010\u0013\u001a\u0016\u0012\u0012\u0012\u0010\u0012\n\u0012\b\u0012\u0004\u0012\u00020\u000f0\u000e\u0018\u00010\u000b0\nX\u0082\u0004\u00a2\u0006\u0002\n\u0000R\u001a\u0010\u0014\u001a\u000e\u0012\n\u0012\b\u0012\u0004\u0012\u00020\u000f0\u000e0\nX\u0082\u0004\u00a2\u0006\u0002\n\u0000R\u001d\u0010\u0015\u001a\u000e\u0012\n\u0012\b\u0012\u0004\u0012\u00020\u000f0\u000e0\u0016\u00a2\u0006\b\n\u0000\u001a\u0004\b\u0017\u0010\u0018R\u000e\u0010\u0004\u001a\u00020\u0005X\u0082\u0004\u00a2\u0006\u0002\n\u0000R\u001f\u0010\u0019\u001a\u0010\u0012\f\u0012\n\u0012\u0004\u0012\u00020\f\u0018\u00010\u000b0\u0016\u00a2\u0006\b\n\u0000\u001a\u0004\b\u001a\u0010\u0018R\u001d\u0010\u001b\u001a\u000e\u0012\n\u0012\b\u0012\u0004\u0012\u00020\u000f0\u000e0\u0016\u00a2\u0006\b\n\u0000\u001a\u0004\b\u001c\u0010\u0018R\u0017\u0010\u001d\u001a\b\u0012\u0004\u0012\u00020\u00110\u0016\u00a2\u0006\b\n\u0000\u001a\u0004\b\u001e\u0010\u0018R\u0017\u0010\u001f\u001a\b\u0012\u0004\u0012\u00020\u00110\u0016\u00a2\u0006\b\n\u0000\u001a\u0004\b \u0010\u0018R%\u0010!\u001a\u0016\u0012\u0012\u0012\u0010\u0012\n\u0012\b\u0012\u0004\u0012\u00020\u000f0\u000e\u0018\u00010\u000b0\u0016\u00a2\u0006\b\n\u0000\u001a\u0004\b\"\u0010\u0018R\u000e\u0010\u0002\u001a\u00020\u0003X\u0082\u0004\u00a2\u0006\u0002\n\u0000R\u000e\u0010\u0006\u001a\u00020\u0007X\u0082\u0004\u00a2\u0006\u0002\n\u0000R\u001d\u0010#\u001a\u000e\u0012\n\u0012\b\u0012\u0004\u0012\u00020\u000f0\u000e0\u0016\u00a2\u0006\b\n\u0000\u001a\u0004\b$\u0010\u0018\u00a8\u0006,"}, d2 = {"Lcom/auramusic/app/presentation/home/HomeViewModel;", "Landroidx/lifecycle/ViewModel;", "searchSongsUseCase", "Lcom/auramusic/app/domain/usecase/SearchSongsUseCase;", "favoritesRepository", "Lcom/auramusic/app/domain/repository/FavoritesRepository;", "streamRepository", "Lcom/auramusic/app/domain/repository/StreamRepository;", "(Lcom/auramusic/app/domain/usecase/SearchSongsUseCase;Lcom/auramusic/app/domain/repository/FavoritesRepository;Lcom/auramusic/app/domain/repository/StreamRepository;)V", "_mixedSearchResults", "Lkotlinx/coroutines/flow/MutableStateFlow;", "Lcom/auramusic/app/core/util/Resource;", "Lcom/auramusic/app/domain/model/SearchResults;", "_recommended", "", "Lcom/auramusic/app/domain/model/Song;", "_recommendedLabel", "", "_searchQuery", "_searchResults", "_trending", "favorites", "Lkotlinx/coroutines/flow/StateFlow;", "getFavorites", "()Lkotlinx/coroutines/flow/StateFlow;", "mixedSearchResults", "getMixedSearchResults", "recommended", "getRecommended", "recommendedLabel", "getRecommendedLabel", "searchQuery", "getSearchQuery", "searchResults", "getSearchResults", "trending", "getTrending", "loadRecommendations", "", "loadTrending", "onSearchQueryChange", "query", "search", "Companion", "app_debug"})
@kotlin.OptIn(markerClass = {kotlinx.coroutines.FlowPreview.class})
@dagger.hilt.android.lifecycle.HiltViewModel()
public final class HomeViewModel extends androidx.lifecycle.ViewModel {
    @org.jetbrains.annotations.NotNull()
    private final com.auramusic.app.domain.usecase.SearchSongsUseCase searchSongsUseCase = null;
    @org.jetbrains.annotations.NotNull()
    private final com.auramusic.app.domain.repository.FavoritesRepository favoritesRepository = null;
    @org.jetbrains.annotations.NotNull()
    private final com.auramusic.app.domain.repository.StreamRepository streamRepository = null;
    @org.jetbrains.annotations.NotNull()
    private static final java.lang.String TAG = "XploitDebug";
    @org.jetbrains.annotations.NotNull()
    private final kotlinx.coroutines.flow.MutableStateFlow<java.lang.String> _searchQuery = null;
    @org.jetbrains.annotations.NotNull()
    private final kotlinx.coroutines.flow.StateFlow<java.lang.String> searchQuery = null;
    @org.jetbrains.annotations.NotNull()
    private final kotlinx.coroutines.flow.MutableStateFlow<com.auramusic.app.core.util.Resource<java.util.List<com.auramusic.app.domain.model.Song>>> _searchResults = null;
    @org.jetbrains.annotations.NotNull()
    private final kotlinx.coroutines.flow.StateFlow<com.auramusic.app.core.util.Resource<java.util.List<com.auramusic.app.domain.model.Song>>> searchResults = null;
    @org.jetbrains.annotations.NotNull()
    private final kotlinx.coroutines.flow.MutableStateFlow<com.auramusic.app.core.util.Resource<com.auramusic.app.domain.model.SearchResults>> _mixedSearchResults = null;
    @org.jetbrains.annotations.NotNull()
    private final kotlinx.coroutines.flow.StateFlow<com.auramusic.app.core.util.Resource<com.auramusic.app.domain.model.SearchResults>> mixedSearchResults = null;
    @org.jetbrains.annotations.NotNull()
    private final kotlinx.coroutines.flow.StateFlow<java.util.List<com.auramusic.app.domain.model.Song>> favorites = null;
    @org.jetbrains.annotations.NotNull()
    private final kotlinx.coroutines.flow.MutableStateFlow<java.util.List<com.auramusic.app.domain.model.Song>> _trending = null;
    @org.jetbrains.annotations.NotNull()
    private final kotlinx.coroutines.flow.StateFlow<java.util.List<com.auramusic.app.domain.model.Song>> trending = null;
    @org.jetbrains.annotations.NotNull()
    private final kotlinx.coroutines.flow.MutableStateFlow<java.util.List<com.auramusic.app.domain.model.Song>> _recommended = null;
    @org.jetbrains.annotations.NotNull()
    private final kotlinx.coroutines.flow.StateFlow<java.util.List<com.auramusic.app.domain.model.Song>> recommended = null;
    @org.jetbrains.annotations.NotNull()
    private final kotlinx.coroutines.flow.MutableStateFlow<java.lang.String> _recommendedLabel = null;
    @org.jetbrains.annotations.NotNull()
    private final kotlinx.coroutines.flow.StateFlow<java.lang.String> recommendedLabel = null;
    @org.jetbrains.annotations.NotNull()
    public static final com.auramusic.app.presentation.home.HomeViewModel.Companion Companion = null;
    
    @javax.inject.Inject()
    public HomeViewModel(@org.jetbrains.annotations.NotNull()
    com.auramusic.app.domain.usecase.SearchSongsUseCase searchSongsUseCase, @org.jetbrains.annotations.NotNull()
    com.auramusic.app.domain.repository.FavoritesRepository favoritesRepository, @org.jetbrains.annotations.NotNull()
    com.auramusic.app.domain.repository.StreamRepository streamRepository) {
        super();
    }
    
    @org.jetbrains.annotations.NotNull()
    public final kotlinx.coroutines.flow.StateFlow<java.lang.String> getSearchQuery() {
        return null;
    }
    
    @org.jetbrains.annotations.NotNull()
    public final kotlinx.coroutines.flow.StateFlow<com.auramusic.app.core.util.Resource<java.util.List<com.auramusic.app.domain.model.Song>>> getSearchResults() {
        return null;
    }
    
    @org.jetbrains.annotations.NotNull()
    public final kotlinx.coroutines.flow.StateFlow<com.auramusic.app.core.util.Resource<com.auramusic.app.domain.model.SearchResults>> getMixedSearchResults() {
        return null;
    }
    
    @org.jetbrains.annotations.NotNull()
    public final kotlinx.coroutines.flow.StateFlow<java.util.List<com.auramusic.app.domain.model.Song>> getFavorites() {
        return null;
    }
    
    @org.jetbrains.annotations.NotNull()
    public final kotlinx.coroutines.flow.StateFlow<java.util.List<com.auramusic.app.domain.model.Song>> getTrending() {
        return null;
    }
    
    @org.jetbrains.annotations.NotNull()
    public final kotlinx.coroutines.flow.StateFlow<java.util.List<com.auramusic.app.domain.model.Song>> getRecommended() {
        return null;
    }
    
    @org.jetbrains.annotations.NotNull()
    public final kotlinx.coroutines.flow.StateFlow<java.lang.String> getRecommendedLabel() {
        return null;
    }
    
    public final void onSearchQueryChange(@org.jetbrains.annotations.NotNull()
    java.lang.String query) {
    }
    
    private final void search(java.lang.String query) {
    }
    
    private final void loadTrending() {
    }
    
    private final void loadRecommendations() {
    }
    
    @kotlin.Metadata(mv = {1, 9, 0}, k = 1, xi = 48, d1 = {"\u0000\u0012\n\u0002\u0018\u0002\n\u0002\u0010\u0000\n\u0002\b\u0002\n\u0002\u0010\u000e\n\u0000\b\u0086\u0003\u0018\u00002\u00020\u0001B\u0007\b\u0002\u00a2\u0006\u0002\u0010\u0002R\u000e\u0010\u0003\u001a\u00020\u0004X\u0082T\u00a2\u0006\u0002\n\u0000\u00a8\u0006\u0005"}, d2 = {"Lcom/auramusic/app/presentation/home/HomeViewModel$Companion;", "", "()V", "TAG", "", "app_debug"})
    public static final class Companion {
        
        private Companion() {
            super();
        }
    }
}