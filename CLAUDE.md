# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Build & test commands

No standalone JDK is on PATH — use the Android Studio JBR as JAVA_HOME (Git Bash):

```bash
export JAVA_HOME="C:/Program Files/Android/Android Studio/jbr"
./gradlew :app:compileDebugKotlin :app:compileDebugJavaWithJavac --console=plain   # fast check
./gradlew :app:assembleDebug --console=plain                                       # full debug APK
```

Single-module app (`:app`), SDK at `C:/Users/madha/AppData/Local/Android/Sdk` (see local.properties).

Run tests (only default scaffolding exists today — no real unit/instrumented tests yet):

```bash
./gradlew testDebugUnitTest --tests "com.bunkwise.duplicatefilefinder.ExampleUnitTest.addition_isCorrect"
./gradlew connectedDebugAndroidTest   # requires a connected device/emulator
```

## Architecture

Package root: `com.bunkwise.duplicatefilefinder`. XML layouts + ViewBinding + Navigation Component (Fragments) — **not** Jetpack Compose. Clean Architecture + MVVM, with fully manual dependency injection (no Hilt/Dagger/Koin, no Room/SQL).

**Composition root**: `di/AppContainer.kt` builds every repository/engine/controller as a lazy singleton; `di/AppViewModelFactory.kt` is a manual `ViewModelProvider.Factory` mapping each ViewModel to constructor args from `AppContainer`. `App.kt` owns the single `AppContainer` instance and is reachable via `context.appContainer`.

**Layers**:
- `core/domain/` — pure Kotlin contracts: `engine/DuplicateEngine`, `model/Models.kt` (`FileItem`, `DuplicateGroup`, `ScanMode`, `Confidence`, etc.), `repository/Repositories.kt` (interfaces only), `error/AppError.kt` (sealed error hierarchy used instead of exceptions, paired with `core/common/Result.kt`'s `Result<D, E: AppError>`).
- `core/data/` — Android-backed repository implementations: `FileEnumeratorImpl` (MediaStore queries), `DataStore.kt` (Preferences DataStore — the *only* persistence store; no SQL DB), `RecycleBinRepositoryImpl` (hand-rolled JSON index + physically moved files), others per interface in `core/domain/repository`.
- `core/engine/` — the duplicate-detection pipeline (Kotlin orchestration over Java primitives in `core/engine/algo/`): see pipeline below.
- `presentation/` — one package per screen (`onboarding/`, `home/`, `scan/`, `results/`, `cleanup/`, `reviewdelete/`, `delete/`, `settings/`, `premium/`), each a Fragment + ViewModel pair wired through a single `NavHostFragment` (`res/navigation/nav_main.xml`) hosted by `MainActivity`. `presentation/shared/SharedSelectionViewModel` holds cross-fragment selection state. `presentation/common/Permissions.kt` handles the API-version-branched storage permission logic (media perms on 33+ vs `READ_EXTERNAL_STORAGE` below, plus `MANAGE_EXTERNAL_STORAGE` for actual delete/archive).
- `service/` — foreground services own long-running work so it survives Activity lifecycle: `ScanForegroundService`/`ScanController` and `DeleteForegroundService`/`DeleteController` are singletons living in `AppContainer`; the Scan/Delete ViewModels are thin wrappers that just observe them. `AutoScanScheduler`/`AutoScanWorker` run WorkManager `PeriodicWorkRequest`s for premium scheduled scans (scan-and-notify only, never auto-deletes). `NotificationOrchestrator` is the sole owner of all notification building/posting.

**Duplicate-detection pipeline** (`core/engine/DuplicateEngineImpl.kt`):
1. Enumerate candidate files via MediaStore (`FileEnumeratorImpl`), excluding the recycle bin, `Android/data`, `.thumbnails`; honors user-configured scan/exclude folders.
2. Bucket by file size, then cheaply prune with `QuickHasher` (partial SHA-256 over first/middle/last 16KB), then fully hash survivors with `StreamingHasher` (streamed SHA-256, parallel IO workers).
3. Merge exact-hash matches with `UnionFind.java` (path compression + union by rank).
4. For SIMILAR/VERY_SIMILAR/DEEP scan modes, `ImageFeatureExtractor` computes two independent perceptual hashes per image (`DHasher` + average hash); `Hamming.distance` with per-mode thresholds clusters near-duplicates via LSH-band grouping, requiring both hashes to agree to avoid false positives.
5. `UnionFind.componentsMinSize2()` produces `DuplicateGroup`s with confidence tiers; `KeeperRanker` recommends which copy to keep (resolution → size → recency → source-folder heuristics → filename length).
6. File type is verified via magic bytes (`MagicByteSniffer.java`), not trusted from extensions.

**Deletion** (`core/engine/` / `core/data/DeletionEngineImpl.kt`): moves files to a UUID-named recycle bin (30-day retention) or archives to a dated ZIP (SAF tree URI or app-private `archives/`) before removing originals; per-file atomic outcome — a failure on one file never aborts the batch.

## Notes for future changes
- Code comments cite `ARCHITECTURE §…` and `NOTIFICATION.TXT §…` section numbers (e.g. in `AndroidManifest.xml`, `AppContainer.kt`, `Permissions.kt`, `NotificationOrchestrator.kt`) as if referencing a design spec — those documents are not present in this checkout, so treat the code/comments themselves as the authoritative record.
- No Kotlin Gradle plugin is explicitly applied (only `com.android.application`), which is unusual for a ~89%-Kotlin codebase — be aware if adding new Gradle configuration.

## graphify

This project has a knowledge graph at graphify-out/ with god nodes, community structure, and cross-file relationships.

Rules:
- For codebase questions, first run `graphify query "<question>"` when graphify-out/graph.json exists. Use `graphify path "<A>" "<B>"` for relationships and `graphify explain "<concept>"` for focused concepts. These return a scoped subgraph, usually much smaller than GRAPH_REPORT.md or raw grep output.
- If graphify-out/wiki/index.md exists, use it for broad navigation instead of raw source browsing.
- Read graphify-out/GRAPH_REPORT.md only for broad architecture review or when query/path/explain do not surface enough context.
- After modifying code, run `graphify update .` to keep the graph current (AST-only, no API cost).
