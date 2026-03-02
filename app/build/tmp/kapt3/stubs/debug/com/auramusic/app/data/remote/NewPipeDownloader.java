package com.auramusic.app.data.remote;

@kotlin.Metadata(mv = {1, 9, 0}, k = 1, xi = 48, d1 = {"\u0000 \n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0002\b\u0002\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0002\b\u0002\u0018\u0000 \t2\u00020\u0001:\u0001\tB\r\u0012\u0006\u0010\u0002\u001a\u00020\u0003\u00a2\u0006\u0002\u0010\u0004J\u0010\u0010\u0005\u001a\u00020\u00062\u0006\u0010\u0007\u001a\u00020\bH\u0016R\u000e\u0010\u0002\u001a\u00020\u0003X\u0082\u0004\u00a2\u0006\u0002\n\u0000\u00a8\u0006\n"}, d2 = {"Lcom/auramusic/app/data/remote/NewPipeDownloader;", "Lorg/schabi/newpipe/extractor/downloader/Downloader;", "client", "Lokhttp3/OkHttpClient;", "(Lokhttp3/OkHttpClient;)V", "execute", "Lorg/schabi/newpipe/extractor/downloader/Response;", "request", "Lorg/schabi/newpipe/extractor/downloader/Request;", "Companion", "app_debug"})
public final class NewPipeDownloader extends org.schabi.newpipe.extractor.downloader.Downloader {
    @org.jetbrains.annotations.NotNull()
    private final okhttp3.OkHttpClient client = null;
    @org.jetbrains.annotations.NotNull()
    private static final java.lang.String TAG = "AuraDebug-Downloader";
    @org.jetbrains.annotations.NotNull()
    private static final java.lang.String USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; rv:102.0) Gecko/20100101 Firefox/102.0";
    @org.jetbrains.annotations.NotNull()
    public static final com.auramusic.app.data.remote.NewPipeDownloader.Companion Companion = null;
    
    public NewPipeDownloader(@org.jetbrains.annotations.NotNull()
    okhttp3.OkHttpClient client) {
        super();
    }
    
    @java.lang.Override()
    @org.jetbrains.annotations.NotNull()
    public org.schabi.newpipe.extractor.downloader.Response execute(@org.jetbrains.annotations.NotNull()
    org.schabi.newpipe.extractor.downloader.Request request) {
        return null;
    }
    
    @kotlin.Metadata(mv = {1, 9, 0}, k = 1, xi = 48, d1 = {"\u0000\u0014\n\u0002\u0018\u0002\n\u0002\u0010\u0000\n\u0002\b\u0002\n\u0002\u0010\u000e\n\u0002\b\u0002\b\u0086\u0003\u0018\u00002\u00020\u0001B\u0007\b\u0002\u00a2\u0006\u0002\u0010\u0002R\u000e\u0010\u0003\u001a\u00020\u0004X\u0082T\u00a2\u0006\u0002\n\u0000R\u000e\u0010\u0005\u001a\u00020\u0004X\u0082T\u00a2\u0006\u0002\n\u0000\u00a8\u0006\u0006"}, d2 = {"Lcom/auramusic/app/data/remote/NewPipeDownloader$Companion;", "", "()V", "TAG", "", "USER_AGENT", "app_debug"})
    public static final class Companion {
        
        private Companion() {
            super();
        }
    }
}