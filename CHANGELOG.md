# Changelog

Todas las versiones de PaltaSound. Formato basado en [Keep a Changelog](https://keepachangelog.com/es/1.1.0/).

## [0.8.0] - 2026-08-09

### Añadido

- **Diseño Material 3 Expressive**: nueva escala de formas (`AppShapes` 4–28dp + xLarge), motion con muelles expresivos (overshoot moderado) en transiciones de pantalla, mini reproductor y tabs de Now Playing, y armazón redondeado en todos los modos de layout (el modo Cuadrado conserva sus bordes separadores con esquinas suaves).
- **Mini reproductor flotante**: nuevo estilo "Flotante" (tarjeta sobre el contenido de las rutas, con padding inferior automático en las screens para que nada quede oculto) además de la barra pegada.
- **Fondos del mini reproductor flotante**: color sólido, de la carátula (desenfocada con gradiente de sus colores) y semitransparente con **desenfoque real del contenido de la ruta** detrás de la tarjeta (integración de la librería Haze, como SimpMusic).
- **Navegación en la barra de título** estilo web: botones de atrás (se desactiva en la raíz) y recargar. El botón de recarga recrea la pantalla activa (detalle) o fuerza la recarga del ViewModel (Home/Search/Library). Se eliminaron los botones de atrás duplicados en Álbum/Playlist/Artista/Búsqueda de YouTube y el botón de refrescar del Home.
- **Ajustes reorganizados por dominio**: Audio, Apariencia, Mini reproductor, Now Playing, Sincronización, Overlay, Aplicación, Avanzado y Soporte, cada uno con su propio ViewModel (se eliminó el `SettingsViewModel` gigante). Nuevas opciones:
  - Widget del reproductor en la barra de tareas de Windows (opcional).
  - Guardar la cola de reproducción al cerrar (opcional) — con restauración completa de la cola, orden, aleatorio y repetición.
  - Estilo de animación, tamaño y espaciado de letras, y romanización de letras.
  - Normalización de volumen (LUFS).
- **Estadísticas de reproducción**: tiempo escuchado, canciones/álbumes/artistas más reproducidos (contadores persistidos en la base de datos).

### Corregido

- Al reproducir tras restaurar la cola persistente, la primera canción quedaba en 0:00/0:00: ahora el play resuelve y reproduce el stream de la canción actual cuando el reproductor aún no tiene medio cargado.
- La búsqueda de canciones similares devolvía vacío siempre: `YouTube.related` ahora envía `endpoint.params` al browse.
- El efecto de desenfoque (Haze) del mini reproductor quedaba "stuck" (tarjeta transparente sin blur) al cambiar de estilo de fondo: se fuerza la invalidación por pre-draw en desktop.
- `hazeSource` solo se activa cuando el mini reproductor flotante está en modo translúcido (menos coste de render al hacer scroll).
- El botón de recarga del TitleBar ahora recarga Home, Search y Library a través de sus ViewModels (antes no hacían nada al ser singletons).
- Se quitó el loading infinito cuando no se encuentran las letras: la búsqueda ahora tiene un timeout (20 s) y cancela correctamente al cambiar de canción, en vez de quedarse esperando para siempre. Cuando no hay letras disponibles se muestra el estado vacío.
- El panel de medios de Windows ahora muestra **PaltaSound** (en vez de "Aplicación Desconocida"): se registra el AppUserModelID del proceso y la app repara su acceso directo del menú Inicio en el arranque para que el panel muestre el nombre correcto.
- El auto-updater vuelve a funcionar para los instaladores `.exe`: el artefacto ahora se nombra `paltasound-<v>-win-x64-nsis.exe` (target `Nsis`), porque el updater selecciona el EXE filtrando por el sufijo `-nsis.` del nombre.
- El empaquetado GraalVM de Windows copia explícitamente `libmpv-2.dll` y `yt-dlp.exe`; así los builds limpios de GitHub Actions no generan instaladores incompletos.
- El instalador EXE (NSIS) ahora es asistido: muestra la licencia **GPL-3.0** con botón de aceptación y permite elegir la carpeta de instalación.

### Cambiado

- Se eliminó la opción "Canciones similares" del menú contextual de canciones; el menú vuelve a su agrupación original (Radio / Me gusta / Descargar).

### Compilación

- Los instaladores de **Windows** (`.msi` y `.exe`) se generan en GitHub Actions como binario **GraalVM nativo** (sin JVM).
- Se restaura el archivo `LICENSE` (GNU GPL-3.0) en la raíz del repo.
- **Linux** (`.deb`/`.rpm`) mantiene la distribución JVM (el native-image de GraalVM falla en los runners de GitHub por memoria).
