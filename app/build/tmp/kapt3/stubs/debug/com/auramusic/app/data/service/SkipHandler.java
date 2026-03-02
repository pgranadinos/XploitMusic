package com.auramusic.app.data.service;

/**
 * Shared event bus for media notification skip actions.
 * The ForwardingPlayer in AuraMediaService emits skip events,
 * and the PlayerViewModel collects them to trigger forceSkipToNext/Previous.
 *
 * This bridges the gap between ExoPlayer (which only knows about queued MediaItems)
 * and our custom autoplay queue (managed by PlayerViewModel).
 */
@javax.inject.Singleton()
@kotlin.Metadata(mv = {1, 9, 0}, k = 1, xi = 48, d1 = {"\u0000&\n\u0002\u0018\u0002\n\u0002\u0010\u0000\n\u0002\b\u0002\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0002\b\u0003\n\u0002\u0010\u0002\n\u0002\b\u0003\b\u0007\u0018\u00002\u00020\u0001:\u0001\rB\u0007\b\u0007\u00a2\u0006\u0002\u0010\u0002J\u0006\u0010\n\u001a\u00020\u000bJ\u0006\u0010\f\u001a\u00020\u000bR\u0014\u0010\u0003\u001a\b\u0012\u0004\u0012\u00020\u00050\u0004X\u0082\u0004\u00a2\u0006\u0002\n\u0000R\u0017\u0010\u0006\u001a\b\u0012\u0004\u0012\u00020\u00050\u0007\u00a2\u0006\b\n\u0000\u001a\u0004\b\b\u0010\t\u00a8\u0006\u000e"}, d2 = {"Lcom/auramusic/app/data/service/SkipHandler;", "", "()V", "_skipRequests", "Lkotlinx/coroutines/flow/MutableSharedFlow;", "Lcom/auramusic/app/data/service/SkipHandler$SkipDirection;", "skipRequests", "Lkotlinx/coroutines/flow/SharedFlow;", "getSkipRequests", "()Lkotlinx/coroutines/flow/SharedFlow;", "requestSkipNext", "", "requestSkipPrevious", "SkipDirection", "app_debug"})
public final class SkipHandler {
    @org.jetbrains.annotations.NotNull()
    private final kotlinx.coroutines.flow.MutableSharedFlow<com.auramusic.app.data.service.SkipHandler.SkipDirection> _skipRequests = null;
    @org.jetbrains.annotations.NotNull()
    private final kotlinx.coroutines.flow.SharedFlow<com.auramusic.app.data.service.SkipHandler.SkipDirection> skipRequests = null;
    
    @javax.inject.Inject()
    public SkipHandler() {
        super();
    }
    
    @org.jetbrains.annotations.NotNull()
    public final kotlinx.coroutines.flow.SharedFlow<com.auramusic.app.data.service.SkipHandler.SkipDirection> getSkipRequests() {
        return null;
    }
    
    public final void requestSkipNext() {
    }
    
    public final void requestSkipPrevious() {
    }
    
    @kotlin.Metadata(mv = {1, 9, 0}, k = 1, xi = 48, d1 = {"\u0000\f\n\u0002\u0018\u0002\n\u0002\u0010\u0010\n\u0002\b\u0004\b\u0086\u0081\u0002\u0018\u00002\b\u0012\u0004\u0012\u00020\u00000\u0001B\u0007\b\u0002\u00a2\u0006\u0002\u0010\u0002j\u0002\b\u0003j\u0002\b\u0004\u00a8\u0006\u0005"}, d2 = {"Lcom/auramusic/app/data/service/SkipHandler$SkipDirection;", "", "(Ljava/lang/String;I)V", "NEXT", "PREVIOUS", "app_debug"})
    public static enum SkipDirection {
        /*public static final*/ NEXT /* = new NEXT() */,
        /*public static final*/ PREVIOUS /* = new PREVIOUS() */;
        
        SkipDirection() {
        }
        
        @org.jetbrains.annotations.NotNull()
        public static kotlin.enums.EnumEntries<com.auramusic.app.data.service.SkipHandler.SkipDirection> getEntries() {
            return null;
        }
    }
}