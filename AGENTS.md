# Melodist Engineering Rules

## Architecture
- Follow Clean Architecture boundaries: UI renders state and sends user intents; ViewModels coordinate use cases; repositories/services own data, network, database, player, and download work.
- UI composables must not call backend/network/database APIs directly. If a screen needs new data, expose it through a ViewModel state flow.
- Keep domain models and UI models explicit. Do not leak SQLDelight rows or transport DTO details into composables unless a mapper already makes that the local convention.
- Prefer existing repositories, mappers, CompositionLocals, and player/download helpers before adding new abstractions.
- Put cross-screen behavior in shared components or ViewModels, not copied into each screen.

## State And Side Effects
- State flows should be the source of truth for screen data. Avoid duplicated mutable UI state unless it is purely ephemeral, such as hover, dialog visibility, selected row ids, or text-field drafts.
- Compose side effects (`LaunchedEffect`, `DisposableEffect`) should be small and UI-scoped. Network calls, DB writes, playback actions, and downloads must go through ViewModels/services.
- Keep selection, dialogs, and context menus predictable: clear transient selection after destructive or bulk actions.
- Do not block initial composition with image processing, palette extraction, large list transforms, or synchronous file/network work.

## Performance
- Cache expensive derived data such as artwork palettes and metadata conversions.
- Use stable lazy-list keys based on ids. Avoid index keys unless the item has no stable id.
- Avoid full-screen recomposition for frequent player progress updates. Subscribe narrowly to progress only where progress is drawn.
- Do not upscale artwork or extract dynamic colors in first-render paths unless the user explicitly needs high-resolution art.
- Prefer `remember`, `derivedStateOf`, and precomputed ViewModel state for expensive list filters/grouping.

## UI Quality
- Screens should feel like production music software: dense, calm, responsive, and scan-friendly.
- Avoid decorative cards inside cards. Use surfaces for major panels and cards for repeated media items or dialogs.
- Every visible control should do something useful or be omitted until implemented.
- Buttons with icons need clear content descriptions. Use familiar Material icons when available.
- Loading states should match the target screen layout closely.

## Data And Downloads
- Local playlists must react to song additions/removals: update count, thumbnail, and displayed list consistently.
- Download state must flow from `DownloadViewModel`; UI should not infer download completion from files directly.
- Bulk actions should reuse single-item code paths where possible, so playlist count, thumbnails, and metadata resolution stay consistent.

## Git And Maintenance
- Keep edits scoped to the requested behavior.
- Never revert unrelated local changes.
- Run `compileKotlinJvm` after Kotlin/SQLDelight changes when possible.
