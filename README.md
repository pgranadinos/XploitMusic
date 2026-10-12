# XploitMusic 🎵

**XploitMusic** es una aplicación nativa de reproducción y streaming de música para Android diseñada para uso personal. Su objetivo es brindar una experiencia fluida e intuitiva inspirada en Spotify, ofreciendo reproducción continua en segundo plano, soporte para dispositivos Bluetooth y extracción de audio multi-proveedor con alta disponibilidad.

---

## 🛠️ Requisitos de Compilación

- **JDK**: Java Development Kit 17 (JDK 17)
- **IDE**: Android Studio (Iguana / Jellyfish / Ladybug o superior)
- **Android SDK**: `compileSdk = 34`, `minSdk = 24`, `targetSdk = 34`
- **Herramienta de procesamiento de anotaciones**: KSP (Kotlin Symbol Processing)

---

## ⚙️ Configuración del Entorno (`local.properties`)

Crea o edita el archivo `local.properties` en la raíz del proyecto para definir variables de entorno sin incluir claves sensibles en el repositorio:

```properties
# Credenciales opcionales para integración con Spotify
SPOTIFY_CLIENT_ID=tu_spotify_client_id_aqui

# Configuración del servidor BFF (Backend For Frontend) opcional
BFF_BASE_URL=http://10.0.2.2:8080/api/v1/
USE_LOCALHOST_BFF=false
```

---

## 🚀 Compilación y Ejecución

Para compilar e instalar la aplicación en un emulador o dispositivo conectado vía ADB:

```powershell
# Compilar la aplicación en modo Debug
.\gradlew.bat assembleDebug

# Instalar e iniciar la aplicación en el dispositivo conectado
.\gradlew.bat app:installDebug
```

---

## 🧪 Ejecución de Pruebas Unitarias

Para correr la suite completa de pruebas unitarias:

```powershell
.\gradlew.bat testDebugUnitTest
```

---

## 🏗️ Arquitectura y Tecnologías
- **Patrón de Arquitectura**: Clean Architecture + MVI/MVVM (Capas `data`, `domain`, `presentation`).
- **UI**: Jetpack Compose con Material 3 y Navigation Compose.
- **Inyección de Dependencias**: Hilt con KSP.
- **Reproducción de Audio**: Jetpack Media3 (ExoPlayer + MediaLibraryService).
- **Extracción de Audio**: Multi-Proveedor con fallback automático (YouTube Music, NewPipe Extractor local, Piped, Invidious).
- **Persistencia**: Room Database v10 con migraciones explícitas.
