# Melodist (Beta)

> Estado: **En desarrollo** · **Beta 1.0.5** · Orientado a **Windows** · Basado en **Metrolist** · Construido con amplio uso de **IA (IAPS)**

Este proyecto es una app de escritorio de streaming de música (Compose Multiplatform) enfocada a Windows, inspirada en [Metrolist](https://github.com/MetrolistGroup/Metrolist) y acelerada con herramientas de IA para iterar el diseño y la lógica.

## Características

- 🎵 **Streaming de YouTube Music** — Reproduce cualquier canción, álbum, playlist o artista (Soporte WebM/Opus)
- 🔍 **Búsqueda integrada** — Historial persistente con SQLDelight
- 📚 **Librería personal** — Álbumes guardados, playlists, descargas, favoritos
- 🎨 **Temas dinámicos** — Material Design 3 con colores extraídos de la carátula
- ⬇️ **Descargas en caché** — Chunked/resumable (HTTP Range), sin transcodificación
- 🔊 **MPV Nativo** — Motor de audio ultraligero y potente basado en `libmpv` (Sin VLC)
- ⌨️ **Teclas multimedia** — Play/Pause, Next, Previous, Stop globales (JNativeHook)
- 🔔 **System Tray** — Minimizar a la bandeja con controles de reproducción
- 🪟 **Title bar personalizado** — Sin barra nativa, integrado con el tema M3
- 🍪 **Cookies de YouTube** — Soporte para contenido personalizado (Home, playlists privadas)
- ⚙️ **Configuración** — Calidad de audio, tema, resolución de carátulas, caché de imágenes

## Arquitectura

```
Melodist/
├── composeApp/     ← UI (Compose Desktop, screens, navegación, temas)
├── shared/         ← Lógica de negocio (ViewModels, player, DB, preferencias)
├── innertube/      ← Cliente de YouTube Music (NO MODIFICAR)
├── server/         ← Servidor Ktor (experimental)
└── mpv-resources/  ← Binarios de MPV (libmpv-2.dll)
```

## Tecnologías

| Stack | Librería |
|-------|----------|
| UI | Compose Multiplatform + Material 3 |
| Navegación | Decompose |
| DI | Koin |
| Base de datos | SQLDelight (JDBC SQLite) |
| Streaming | MPV (Nativo vía JNA) |
| Imágenes | Coil 3 (memoria + disco) |
| Media keys | JNativeHook |
| HTTP | Ktor Client / OkHttp |
| Serialización | Kotlinx Serialization |

## Estado y alcance
- **Beta 1.0.3**: características en evolución, posibles bugs.
- **Sin VLC**: migración total a MPV para mayor ligereza y soporte de codecs (Opus/WebM).
- **Rutas Estándar**: almacenamiento unificado en `%LOCALAPPDATA%/Melodist`.
- **IA-driven**: se apoyó intensamente en asistencia de IA (IAPS) para diseño, UI y refactors.

## Build y ejecución (Desktop / JVM)

- Ejecutar en modo desarrollo:
  ```powershell
  .\gradlew.bat :composeApp:run
  ```
- Empaquetar instalador MSI/EXE (vía Conveyor):
  ```powershell
  conveyor make windows-msi
  ```

## Rutas de datos (Windows Estándar)

| Contenido | Ruta |
|-----------|------|
| **Base de Datos** | `%LOCALAPPDATA%/Melodist/melodist.db` |
| **Preferencias** | `%LOCALAPPDATA%/Melodist/data/` |
| **Canciones en Caché** | `%LOCALAPPDATA%/Melodist/cache/songs/` |
| **Caché de Imágenes** | `%LOCALAPPDATA%/Melodist/cache/image_cache/` |
| **Cookies / Logs** | `%LOCALAPPDATA%/Melodist/logs/` |

---
Más info sobre [Kotlin Multiplatform](https://www.jetbrains.com/help/kotlin-multiplatform-dev/get-started.html) y Compose Multiplatform en la documentación oficial.
