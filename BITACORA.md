# Bitácora de Cambios - XploitMusic

| Fecha | Subtarea / Cambio | Archivos Modificados |
| :--- | :--- | :--- |
| 2026-10-11 | **0.1 Fix Compilación de Tests**: Inyección de mock de `PlaybackLogManager` en tests de `PlayerViewModel` y `StreamRepositoryImpl`. | `PlayerViewModelSkipRaceTest.kt`, `StreamRepositoryImplTest.kt`, `LatamChartsLiveTest.kt` |
| 2026-10-11 | **0.2 Reintento de Enlace Caducado**: Prevención de carrera de hilos, eliminación de doble descarga, invalidación de caché de NewPipe en HTTP 403, preservación de posición con `seekTo`, límite de 2 reintentos por canción (el contador solo se reinicia al iniciar una reproducción nueva, no al reintentar). | `NewPipeExtractorClient.kt`, `LocalYouTubeExtractorProvider.kt`, `ProviderGateway.kt`, `ProviderGatewayImpl.kt`, `StreamRepository.kt`, `StreamRepositoryImpl.kt`, `PlayerViewModel.kt`, `PlayerViewModelRetryTest.kt` |
| 2026-10-11 | **0.3 Protección de Datos Room**: Se condicionó `fallbackToDestructiveMigration()` únicamente a builds de desarrollo (`BuildConfig.DEBUG`). | `DatabaseModule.kt` |
| 2026-10-11 | **0.4 Limpieza de Logs en Release**: Agregada regla ProGuard `-assumenosideeffects` para remover `Log.v`, `Log.d` e `Log.i` en ejecutable Release. | `proguard-rules.pro` |
| 2026-10-11 | **0.5 Documentación Inicial**: Creación de `BITACORA.md` y `README.md` del proyecto. | `BITACORA.md`, `README.md` |
