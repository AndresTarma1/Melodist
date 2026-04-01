# Melodist (Beta)

> Estado: **En desarrollo** · **Beta 1.0.1** · Orientado a **Windows** · Basado en **Metrolist** · Construido con amplio uso de **IA (IAPS)**

Este proyecto es una app de escritorio de streaming de música (Compose Multiplatform) enfocada a Windows, inspirada en [Metrolist](https://github.com/MetrolistGroup/Metrolist) y acelerada con herramientas de IA para iterar el diseño y la lógica.

## Características

- 🎵 **Streaming de YouTube Music** — Reproduce cualquier canción, álbum, playlist o artista
- 🔍 **Búsqueda integrada** — Historial persistente con SQLDelight
- 📚 **Librería personal** — Álbumes guardados, playlists, descargas, favoritos
- 🎨 **Temas dinámicos** — Material Design 3 con colores extraídos de la carátula
- ⬇️ **Descargas en caché** — Chunked/resumable (HTTP Range), sin transcodificación
- 🔊 **VLC embebido** — No requiere instalar VLC en el sistema
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
└── vlc/            ← Binarios de VLC embebidos (libvlc.dll, plugins/)
```

## Tecnologías

| Stack | Librería |
|-------|----------|
| UI | Compose Multiplatform + Material 3 |
| Navegación | Decompose |
| DI | Koin |
| Base de datos | SQLDelight (JDBC SQLite) |
| Streaming | VLCJ (VLC embebido) |
| Imágenes | Coil 3 (memoria + disco) |
| Media keys | JNativeHook |
| HTTP | Ktor Client / OkHttp |
| Serialización | Kotlinx Serialization |

## Estado y alcance
- **Beta 1.0.1**: características en evolución, posibles bugs.
- **Basado en Metrolist**: lógica de streaming y fallback heredada/adaptada.
- **Windows-first**: empaqueta VLC embebido para evitar instalaciones externas.
- **IA-driven**: se apoyó intensamente en asistencia de IA (IAPS) para diseño, UI y refactors.

## Build y ejecución (Desktop / JVM)

- Ejecutar en modo desarrollo:
  ```powershell
  .\gradlew.bat :composeApp:run
  ```
- Empaquetar instalador MSI:
  ```powershell
  .\gradlew.bat :composeApp:packageDistributionForCurrentOS
  ```
  El instalador se genera en `composeApp/build/compose/binaries/main/msi/`

## Build y ejecución (Server)

- Ejecutar en modo desarrollo:
  ```powershell
  .\gradlew.bat :server:run
  ```

## Rutas de datos

| Ruta | Contenido |
|------|-----------|
| `~/.melodist/` | Base de datos, caché de imágenes, canciones descargadas |
| `~/.melodist/cache/songs/` | Audio en caché (m4a/webm) |
| `~/.melodist/image_cache/` | Caché de Coil (miniaturas) |
| `%APPDATA%/melodist/` | Cookie de sesión, configuración |

---
Más info sobre [Kotlin Multiplatform](https://www.jetbrains.com/help/kotlin-multiplatform-dev/get-started.html) y Compose Multiplatform en la documentación oficial.
