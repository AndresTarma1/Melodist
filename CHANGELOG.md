# Changelog

Todas las versiones de PaltaSound. Formato basado en [Keep a Changelog](https://keepachangelog.com/es/1.1.0/).

## [0.7.2] - 2026-08-09

### Fixes

- Se quitó el loading infinito cuando no se encuentran las letras: la búsqueda ahora tiene un timeout (20 s) y cancela correctamente al cambiar de canción, en vez de quedarse esperando para siempre. Cuando no hay letras disponibles se muestra el estado vacío.
- El panel de medios de Windows ahora muestra **PaltaSound** (en vez de "Aplicación Desconocida"): se registra el AppUserModelID del proceso y la app repara su acceso directo del menú Inicio en el arranque para que el panel muestre el nombre correcto.
- El auto-updater vuelve a funcionar para los instaladores `.exe`: el artefacto ahora se nombra `paltasound-<v>-win-x64-nsis.exe` (target `Nsis`), porque el updater selecciona el EXE filtrando por el sufijo `-nsis.` del nombre.

### Compilación

- Los instaladores de **Windows** (`.msi` y `.exe`) se generan en GitHub Actions como binario **GraalVM nativo** (sin JVM).
- **Linux** (`.deb`/`.rpm`) mantiene la distribución JVM (el native-image de GraalVM falla en los runners de GitHub por memoria).
