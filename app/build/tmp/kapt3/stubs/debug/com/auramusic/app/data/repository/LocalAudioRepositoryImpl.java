package com.auramusic.app.data.repository;

@kotlin.Metadata(mv = {1, 9, 0}, k = 1, xi = 48, d1 = {"\u0000*\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0002\b\u0002\n\u0002\u0010 \n\u0002\u0018\u0002\n\u0002\b\u0002\n\u0002\u0018\u0002\n\u0002\u0010\u000e\n\u0002\b\u0002\u0018\u0000 \f2\u00020\u0001:\u0001\fB\u000f\b\u0007\u0012\u0006\u0010\u0002\u001a\u00020\u0003\u00a2\u0006\u0002\u0010\u0004J\u0014\u0010\u0005\u001a\b\u0012\u0004\u0012\u00020\u00070\u0006H\u0096@\u00a2\u0006\u0002\u0010\bJ\f\u0010\t\u001a\u00020\n*\u00020\u000bH\u0002R\u000e\u0010\u0002\u001a\u00020\u0003X\u0082\u0004\u00a2\u0006\u0002\n\u0000\u00a8\u0006\r"}, d2 = {"Lcom/auramusic/app/data/repository/LocalAudioRepositoryImpl;", "Lcom/auramusic/app/domain/repository/LocalAudioRepository;", "contentResolver", "Landroid/content/ContentResolver;", "(Landroid/content/ContentResolver;)V", "getLocalSongs", "", "Lcom/auramusic/app/domain/model/Song;", "(Lkotlin/coroutines/Continuation;)Ljava/lang/Object;", "toAndroidUri", "Landroid/net/Uri;", "", "Companion", "app_debug"})
public final class LocalAudioRepositoryImpl implements com.auramusic.app.domain.repository.LocalAudioRepository {
    @org.jetbrains.annotations.NotNull()
    private final android.content.ContentResolver contentResolver = null;
    @org.jetbrains.annotations.NotNull()
    private static final java.lang.String TAG = "XploitDebug";
    @org.jetbrains.annotations.NotNull()
    public static final com.auramusic.app.data.repository.LocalAudioRepositoryImpl.Companion Companion = null;
    
    @javax.inject.Inject()
    public LocalAudioRepositoryImpl(@org.jetbrains.annotations.NotNull()
    android.content.ContentResolver contentResolver) {
        super();
    }
    
    @java.lang.Override()
    @org.jetbrains.annotations.Nullable()
    public java.lang.Object getLocalSongs(@org.jetbrains.annotations.NotNull()
    kotlin.coroutines.Continuation<? super java.util.List<com.auramusic.app.domain.model.Song>> $completion) {
        return null;
    }
    
    private final android.net.Uri toAndroidUri(java.lang.String $this$toAndroidUri) {
        return null;
    }
    
    @kotlin.Metadata(mv = {1, 9, 0}, k = 1, xi = 48, d1 = {"\u0000\u0012\n\u0002\u0018\u0002\n\u0002\u0010\u0000\n\u0002\b\u0002\n\u0002\u0010\u000e\n\u0000\b\u0086\u0003\u0018\u00002\u00020\u0001B\u0007\b\u0002\u00a2\u0006\u0002\u0010\u0002R\u000e\u0010\u0003\u001a\u00020\u0004X\u0082T\u00a2\u0006\u0002\n\u0000\u00a8\u0006\u0005"}, d2 = {"Lcom/auramusic/app/data/repository/LocalAudioRepositoryImpl$Companion;", "", "()V", "TAG", "", "app_debug"})
    public static final class Companion {
        
        private Companion() {
            super();
        }
    }
}