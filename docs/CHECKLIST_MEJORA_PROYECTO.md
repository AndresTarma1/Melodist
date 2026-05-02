# Checklist de mejora del proyecto Melodist

## Alcance de este analisis

Se auditaron los modulos y flujos principales del proyecto:

- Build y configuracion: `build.gradle.kts`, `settings.gradle.kts`, `composeApp/build.gradle.kts`, `shared/build.gradle.kts`, `gradle/libs.versions.toml`, `conveyor.conf`.
- UI Desktop (Compose): navegacion, pantallas y shell principal (`composeApp/src/jvmMain/...`).
- Capa de estado y logica: ViewModels (`shared/src/commonMain/.../viewmodels` y `shared/src/jvmMain/.../viewmodels`).
- Datos e infraestructura: repositorios, API wrapper, SQLDelight DAO y servicios de player/download.
- Estado de validacion tecnica actual:
  - Compilacion OK: `:shared:compileKotlinJvm` y `:composeApp:compileKotlinJvm`.
  - Tests OK (pero minimos): `test`.
  - Conteo aproximado: 224 archivos Kotlin y solo 2 archivos de test.

## Diagnostico ejecutivo

- El proyecto ya tiene buena separacion modular (`composeApp`, `shared`, `innertube`), pero aun esta en una arquitectura por capas acopladas, no en Clean Architecture.
- Hay logica de negocio y acceso remoto directo en ViewModels (uso de `YouTube.*`), lo cual dificulta testeo, mantenibilidad y evolucion.
- La UI en general esta mejorando hacia pattern Route + Screen state/actions, pero hay pantallas muy grandes con responsabilidades mezcladas (render + reglas + orchestration).
- El area de mayor riesgo tecnico actual es la falta de pruebas automatizadas sobre casos de uso y ViewModels.

## Avance realizado en esta sesión

- [x] Se creó la primera capa `domain` piloto para búsqueda en `shared`.
- [x] `SearchViewModel` dejó de depender directamente de `YouTube.*` y del repositorio concreto.
- [x] `SearchScreen` pasó a usar modelos de dominio para filtro e historial.
- [x] `AppModule` registra los nuevos contratos y casos de uso.
- [x] Se añadieron tests de casos de uso para búsqueda e historial.
- [x] Se inició el piloto de `Home` con `LoadHomeUseCase` y `HomeRemoteDataSource`.
- [x] `SearchScreen` usa `YoutubeItemList` como alias de `items.kt`.
- [x] Se inició el piloto de `Playlist` con `LoadPlaylistUseCase` y `LoadPlaylistContinuationUseCase`.
- [x] `PlaylistViewModel` fue adelgazado con helpers de carga para playlists locales, caché y remotas.
- [x] Se reforzó el refresco del artwork del `mediaSession` al cambiar de canción.
- [x] `YoutubeListItem` fue alineado visualmente con el hover de `SongListItem`.
- [x] `AlbumViewModel` fue refactorizado con el mismo patrón de helpers de carga.
- [x] Se agregó `LoadAlbumContinuationUseCase` para eliminar dependencia directa a YouTube.
- [x] Se extrajo lógica de sincronización de metadatos del `PlayerViewModel` en `PlaybackMetadataService` y `UpdatePlaybackMetadataUseCase`.
- [x] `PlayerViewModel` ahora usa use cases para actualizar metadatos en Windows MediaSession.
- [x] Completada cobertura de `LibraryViewModel`: agregado `getLibraryLikedSongs()` a `LibraryRemoteDataSource`.
- [x] `SearchViewModel` y `ArtistViewModel` ratificados como correctamente estructurados (ya usan use cases).

---

## Prioridad P0 (critico - hacer primero)

### 1) Migrar gradualmente a casos de uso (Use Cases)

- [ ] Crear capa `domain` en `shared` con entidades y casos de uso por feature (Home, Search, Library, Playlist, Album, Account, Player).
- [ ] Introducir interfaces de repositorio en `domain` y mover implementaciones concretas a `data`.
- [ ] Hacer que ViewModels dependan de Use Cases y no de `YouTube.*` ni de SQLDelight directamente.

**Evidencia:**
- Acoplamiento directo a `YouTube` en `HomeViewModel`, `SearchViewModel`, `PlaylistViewModel`, `LibraryViewModel`, `AccountViewModel`, `PlayerViewModel`.
- Wrapper `ApiService` existe, pero no se usa de forma consistente en todos los flujos.

### 2) Reducir responsabilidades de ViewModels muy grandes

- [ ] Dividir `PlayerViewModel` en casos de uso: `PlaySong`, `BuildQueue`, `ResolveStream`, `SyncMediaSession`, `PrefetchRelated`.
- [ ] Dividir `PlaylistViewModel` y `AlbumViewModel` en use cases de carga, paginacion, guardado y play-all.
- [ ] Evitar callbacks de UI dentro de ViewModels para acciones core (usar intents/eventos + estado derivado).

**Evidencia:**
- `PlayerViewModel` concentra cola, reproduccion, metadata, session de Windows, resolucion remota y errores (archivo >500 lineas).
- `PlaylistViewModel` mezcla reglas de playlist local/remota, cache, paginacion, guardado y descarga.

### 3) Introducir estrategia formal de manejo de errores/resultados

- [ ] Definir `AppError` tipado (network, auth, parsing, db, unknown).
- [ ] Reemplazar `String` sueltos en estados por tipos de error de dominio.
- [ ] Homogeneizar reintentos/backoff/cancelacion en red y descarga.

**Evidencia:**
- Errores por texto en multiples estados (`Error(message: String)`), manejo heterogeneo y poco trazable.

### 4) Subir cobertura minima de tests antes de refactors grandes

- [ ] Meta inicial: 15-20 tests de ViewModel + UseCase (Home/Search/Playlist/Player).
- [ ] Mockear repositorios e interfaces (no `YouTube` directo).
- [ ] Agregar tests de regresion para paginacion, shuffle/repeat y guardado local.

**Evidencia:**
- Solo tests placeholder en `composeApp/src/jvmTest/.../ComposeAppDesktopTest.kt` y `shared/src/commonTest/.../SharedCommonTest.kt`.

---

## Prioridad P1 (alto impacto)

### 5) Adelgazar UI grande y estandarizar presentacion

- [ ] Particionar pantallas gigantes en secciones mas pequenas (por ejemplo `AccountScreen.kt`, `SearchScreen.kt`, `SettingsScreen.kt`).
- [ ] Mantener `Route -> State/Actions -> Screen` en todas las pantallas sin excepcion.
- [ ] Crear mappers UI-state para reducir logica de transformacion en composables.

**Evidencia:**
- Archivos UI con mucho volumen y responsabilidades combinadas, por ejemplo `AccountScreen.kt`.

### 6) Unificar source of truth para datos remotos

- [ ] Centralizar acceso remoto en repositorios de `data`.
- [ ] Dejar `ApiService` como data source interno, no como dependencia opcional/inconsistente.
- [ ] Eliminar duplicidad entre llamadas directas `YouTube.*` y wrappers.

### 7) Endurecer capas de datos y sincronizacion

- [ ] Revisar `SyncUtils` para mover operaciones a casos de uso de sincronizacion y resultados tipados.
- [ ] Definir politicas de sync incremental y deteccion de conflictos (playlists locales vs remotas).
- [ ] Versionar estrategias de migracion de datos para cambios de schema o formato.

### 8) Seguridad y privacidad de autenticacion

- [ ] Evaluar cifrado del archivo de cookie local (`AccountManager`) o al menos proteccion adicional de acceso.
- [ ] Definir politica de expiracion/renovacion y logs sin exponer datos sensibles.
- [ ] Agregar checklist de hardening para release.

---

## Prioridad P2 (deuda tecnica y optimizacion continua)

### 9) Rendimiento y consumo

- [ ] Instrumentar tiempos de arranque, latencia de reproduccion y tiempos de busqueda.
- [ ] Reducir recomposiciones en listas extensas (estabilizar modelos y keys donde aplique).
- [ ] Revisar backpressure en flujos frecuentes de player/progreso y descarga.
- [ ] Validar uso de memoria en cache de imagenes y descarga con escenarios largos.

### 10) Observabilidad

- [ ] Unificar logging (Napier/Logger) con niveles, contexto y formato consistente.
- [ ] Agregar IDs de correlacion para operaciones criticas (download, stream resolve, sync).
- [ ] Definir eventos de telemetria local para errores top y acciones de usuario clave.

### 11) Calidad de codigo y consistencia

- [ ] Introducir reglas de estilo y analisis estatico (ktlint/detekt).
- [ ] Eliminar codigo muerto/duplicado (por ejemplo keys duplicadas o utilidades no usadas).
- [ ] Documentar convenciones por capa (UI, presentation, domain, data).

### 12) Release engineering

- [ ] Agregar pipeline CI (build + test + static analysis + artefactos).
- [ ] Añadir smoke tests de empaquetado (`compose` y `conveyor`) para Windows.
- [ ] Definir matriz de compatibilidad (JDK, Windows version, dependencias nativas mpv).

---

## Ruta recomendada para pasar a Clean Architecture (sin frenar desarrollo)

### Fase 1 (1-2 semanas) - Fundaciones

- [ ] Crear paquetes por capa: `presentation`, `domain`, `data` en `shared`.
- [ ] Definir contratos de repositorio en `domain`.
- [ ] Migrar 1 feature piloto completa: Search (ViewModel -> UseCases -> Repository).

### Fase 2 (2-4 semanas) - Core flows

- [ ] Migrar Home + Library + Playlist/Album.
- [ ] Introducir `Result<AppError, T>` (o similar) en todos los use cases.
- [ ] Consolidar mappers entre DTO/DB/domain/UI.

### Fase 3 (4-8 semanas) - Player y sincronizacion

- [ ] Extraer orquestacion de `PlayerViewModel` a casos de uso.
- [ ] Aislar capa de infraestructura nativa (mpv/media session/download) detras de interfaces.
- [ ] Cubrir regresiones con tests de comportamiento.

### Fase 4 (continuo)

- [ ] Medir KPIs tecnicos: crash-free, tiempo de arranque, tiempo a primer play, cobertura tests.
- [ ] Refinar con observabilidad y tuning de performance.

---

## Checklist accionable de corto plazo (proximos 7 dias)

- [ ] Definir ADR corto: decision de adopcion incremental de Clean Architecture.
- [ ] Elegir feature piloto para migrar (recomendado: Search).
- [ ] Crear 5-8 tests de ViewModel para Search y Playlist.
- [ ] Extraer primer set de use cases: `SearchSongs`, `GetSearchSuggestions`, `LoadSearchContinuation`.
- [ ] Estandarizar manejo de error en esos use cases.
- [ ] Configurar ktlint/detekt en CI.

---

## Riesgos si no se atiende

- Mayor costo de cada feature nueva por acoplamiento y efectos colaterales.
- Regresiones silenciosas en reproduccion/sincronizacion por baja cobertura.
- Dificultad creciente para depurar incidencias en produccion.
- Menor velocidad de entrega en releases de escritorio.

---

## Criterio de exito (Definition of Done de la mejora)

- ViewModels consumen use cases, no servicios concretos de red/DB.
- Cobertura de tests en capa de presentacion y dominio para flujos criticos.
- Errores tipados y trazables de punta a punta.
- UI con composables pequenos y responsabilidades claras.
- Pipeline CI ejecutando build + tests + analisis estatico en cada PR.



