# Graph Report - Duplicatefilefinder  (2026-11-22)

## Corpus Check
- 97 files · ~48,512 words
- Verdict: corpus is large enough that graph structure adds value.

## Summary
- 1190 nodes · 2024 edges · 103 communities (58 shown, 45 thin omitted)
- Extraction: 93% EXTRACTED · 7% INFERRED · 0% AMBIGUOUS · INFERRED: 142 edges (avg confidence: 0.81)
- Token cost: 0 input · 0 output

## Graph Freshness
- Built from commit: `425663b8`
- Run `git rev-parse HEAD` and compare to check if the graph is stale.
- Run `graphify update .` after code changes (no API cost).

## Community Hubs (Navigation)
- Result & Error Handling
- Scan Result Repository
- Delete Screen
- Recycle Bin UI
- App Bootstrap & Dispatchers
- Home Screen
- File Enumeration
- ViewModel Factory Wiring
- Scan Modes & Progress
- Scan Phases & UnionFind
- Recycle Bin Repository
- File Preview Adapter
- Architecture Concepts
- Scan Foreground Service
- File Type Detection
- Results Screen
- Cleanup Screen
- Duplicate Group Adapter
- FileItem
- Settings Screen
- Shared Selection State
- Notification Orchestrator
- Hashing Primitives
- Settings Repository
- Review Delete Screen
- QuotaRepository
- Circular Progress View
- Step Connector View
- Settings Row Keys
- Formatters
- Main Activity
- Keeper Ranker
- Donut Chart View
- Storage Permissions
- Launcher Icons & Manifest
- ScanResult
- BinAdapter
- ScanProgress
- Scan Quota Use Case
- App Languages
- Models.kt
- Fragment Lifecycle Boilerplate
- Performance
- RewardedAdManager
- File Opener
- DHash Algorithm
- Hamming Distance
- Gradle Wrapper Script
- Instrumented Test Scaffold
- Launcher Icon hdpi
- Launcher Icon mdpi Round
- Launcher Icon xhdpi
- Launcher Icon xxxhdpi
- Launcher Icon xxxhdpi Round
- Unit Test Scaffold
- Auto-Scan Scheduling
- Preferences DataStore
- Error Model Concepts
- Launcher Icon hdpi Round
- DataStore Concept
- MainActivity Concept
- Selection ViewModel Concept
- Review Focus
- .scan
- .scan
- glassEffect
- DispatcherProvider
- PerformanceTest
- AppIcons.kt
- CLAUDE.md
- AppContainer (composition root)
- AppViewModelFactory
- AutoScanWorker (WorkManager periodic scan)
- Clean Architecture + MVVM
- DeleteController (singleton in AppContainer)
- DeleteForegroundService
- DeletionEngineImpl
- DHasher (difference hash)
- Dual Perceptual Hash Agreement
- Duplicate-Detection Pipeline
- DuplicateEngine (domain contract)
- DuplicateEngineImpl
- FileEnumeratorImpl (MediaStore queries)
- Foreground Services Own Long-Running Work
- Hamming.distance
- ImageFeatureExtractor (perceptual hashes)
- KeeperRanker (keep-copy recommendation)
- Magic-Byte File Type Verification
- MagicByteSniffer (file-type via magic bytes)
- Fully Manual Dependency Injection
- NotificationOrchestrator (sole owner of notifications)
- Permissions (API-branched storage permissions)
- QuickHasher (partial SHA-256 prune)
- RecycleBinRepositoryImpl
- Result<D, E: AppError>
- Safety-First Deletion
- ScanController (singleton in AppContainer)
- ScanForegroundService
- StreamingHasher (full streamed SHA-256)
- UnionFind (path compression + union by rank)

## God Nodes (most connected - your core abstractions)
1. `GLOBAL DESIGN RULES — AVOID AI-GENERATED LOOK` - 32 edges
2. `FileCategory` - 31 edges
3. `RecycleBinRepositoryImpl` - 28 edges
4. `FileItem` - 25 edges
5. `SharedSelectionViewModel` - 24 edges
6. `HomeViewModel` - 23 edges
7. `SettingsFragment` - 23 edges
8. `FileEnumeratorImpl` - 21 edges
9. `ScanResult` - 18 edges
10. `ScanResultRepositoryImpl` - 17 edges

## Surprising Connections (you probably didn't know these)
- `CleanupScreen()` --calls--> `EmptyState()`  [INFERRED]
  app/src/main/java/com/bunkwise/duplicatefilefinder/presentation/cleanup/CleanupScreen.kt → app/src/main/java/com/bunkwise/duplicatefilefinder/ui/Components.kt
- `CleanupScreen()` --calls--> `PrimaryButton()`  [INFERRED]
  app/src/main/java/com/bunkwise/duplicatefilefinder/presentation/cleanup/CleanupScreen.kt → app/src/main/java/com/bunkwise/duplicatefilefinder/ui/Components.kt
- `CleanupScreen()` --calls--> `ScreenHeader()`  [INFERRED]
  app/src/main/java/com/bunkwise/duplicatefilefinder/presentation/cleanup/CleanupScreen.kt → app/src/main/java/com/bunkwise/duplicatefilefinder/ui/Components.kt
- `SavingsCard()` --calls--> `AppCard()`  [INFERRED]
  app/src/main/java/com/bunkwise/duplicatefilefinder/presentation/cleanup/CleanupScreen.kt → app/src/main/java/com/bunkwise/duplicatefilefinder/ui/Components.kt
- `GroupCard()` --calls--> `AppCard()`  [INFERRED]
  app/src/main/java/com/bunkwise/duplicatefilefinder/presentation/cleanup/CleanupScreen.kt → app/src/main/java/com/bunkwise/duplicatefilefinder/ui/Components.kt

## Import Cycles
- None detected.

## Hyperedges (group relationships)
- **Duplicate-Detection Pipeline Stages** — claude_duplicateengineimpl, claude_fileenumeratorimpl, claude_quickhasher, claude_streaminghasher, claude_unionfind, claude_imagefeatureextractor, claude_dhasher, claude_hamming, claude_keeperranker, claude_magicbytesniffer [EXTRACTED 1.00]
- **Manual DI Composition Root** — claude_appcontainer, claude_appviewmodelfactory, claude_app [EXTRACTED 1.00]
- **Foreground Service Long-Running Work Layer** — claude_scanforegroundservice, claude_scancontroller, claude_deleteforegroundservice, claude_deletecontroller, claude_autoscanscheduler, claude_autoscanworker, claude_notificationorchestrator [EXTRACTED 1.00]

## Communities (103 total, 45 thin omitted)

### Community 0 - "Result & Error Handling"
Cohesion: 0.06
Nodes (32): 10. ICONS, 11. SPACING, 12. ALIGNMENT, 13. BUTTONS, 14. NAVIGATION, 15. SETTINGS SCREENS, 16. EMPTY STATES, 17. ERROR MESSAGES (+24 more)

### Community 1 - "Scan Result Repository"
Cohesion: 0.09
Nodes (15): JSONObject, Long, Set, StateFlow, ScanResultRepositoryImpl, StorageStatsProviderImpl, Int, Long (+7 more)

### Community 2 - "Delete Screen"
Cohesion: 0.09
Nodes (21): DeleteFragment, Bundle, Fragment, LayoutInflater, String, View, ViewGroup, DeleteUi (+13 more)

### Community 3 - "Recycle Bin UI"
Cohesion: 0.08
Nodes (17): Bundle, Fragment, LayoutInflater, View, ViewGroup, RecycleBinFragment, BinMessage, BinUiState (+9 more)

### Community 4 - "App Bootstrap & Dispatchers"
Cohesion: 0.15
Nodes (11): DeleteForegroundService, android, Boolean, Context, IBinder, Int, Intent, Job (+3 more)

### Community 5 - "Home Screen"
Cohesion: 0.08
Nodes (18): HomeFragment, Boolean, Bundle, Fragment, LayoutInflater, View, ViewGroup, HomeEvent (+10 more)

### Community 6 - "File Enumeration"
Cohesion: 0.07
Nodes (31): FileEnumeratorImpl, Boolean, Flow, Int, Long, Map, Pair, Set (+23 more)

### Community 7 - "ViewModel Factory Wiring"
Cohesion: 0.06
Nodes (39): CleanupFragment, Bundle, Fragment, LayoutInflater, Long, View, ViewGroup, BottomNav() (+31 more)

### Community 8 - "Scan Modes & Progress"
Cohesion: 0.05
Nodes (29): AppViewModelFactory, DeleteViewModel, Int, String, ViewModel, Boolean, Bundle, Fragment (+21 more)

### Community 9 - "Scan Phases & UnionFind"
Cohesion: 0.22
Nodes (10): AppError, Delete, ARCHIVE_FOLDER_MISSING, BIN_FULL, FILE_CHANGED, PERMISSION_DENIED, TARGET_MISSING, UNKNOWN (+2 more)

### Community 10 - "Recycle Bin Repository"
Cohesion: 0.16
Nodes (11): Array, Boolean, Int, JSONObject, List, Set, StateFlow, String (+3 more)

### Community 11 - "File Preview Adapter"
Cohesion: 0.07
Nodes (39): androidx, CleanupScreen(), Controls(), GroupCard(), Boolean, Color, String, Pill() (+31 more)

### Community 13 - "Scan Foreground Service"
Cohesion: 0.14
Nodes (13): android, Boolean, Context, IBinder, Int, Intent, Job, Service (+5 more)

### Community 14 - "File Type Detection"
Cohesion: 0.17
Nodes (6): QuotaInfo, FileEnumerator, Flow, Int, Map, QuotaRepository

### Community 15 - "Results Screen"
Cohesion: 0.17
Nodes (11): 10. Suggested order of work, 1. Critical bugs (P0): fix these first, 2. Archive-instead-of-delete: deep dive, 3. Navigation audit, 4. Other bugs and edge cases (P1/P2), 5. Battery and computation: what to change, 6. Design and UX problems (mapped to your two principle docs), 7. Things to remove or simplify (+3 more)

### Community 16 - "Cleanup Screen"
Cohesion: 0.31
Nodes (4): Flow, Int, String, QuotaRepositoryImpl

### Community 17 - "Duplicate Group Adapter"
Cohesion: 0.18
Nodes (12): areContentsTheSame(), areItemsTheSame(), GroupAdapter, Int, ListAdapter, RecyclerView, String, VH (+4 more)

### Community 18 - "FileItem"
Cohesion: 0.19
Nodes (12): DeletionEngineImpl, Boolean, Int, List, Long, Map, String, Uri (+4 more)

### Community 19 - "Settings Screen"
Cohesion: 0.05
Nodes (37): ScanSchedule, DAILY, MONTHLY, OFF, WEEKLY, FolderTarget, ARCHIVE, EXCLUDE (+29 more)

### Community 20 - "Shared Selection State"
Cohesion: 0.06
Nodes (27): Confidence, EXACT, HIGH, MEDIUM, VERY_HIGH, DuplicateGroup, Bundle, Fragment (+19 more)

### Community 21 - "Notification Orchestrator"
Cohesion: 0.19
Nodes (6): android, Boolean, Int, String, NotificationOrchestrator, PendingIntent

### Community 22 - "Hashing Primitives"
Cohesion: 0.18
Nodes (6): Hex, QuickHasher, Canceller, StreamingHasher, MessageDigest, RandomAccessFile

### Community 23 - "Settings Repository"
Cohesion: 0.22
Nodes (5): Boolean, Flow, Set, String, SettingsRepositoryImpl

### Community 24 - "Review Delete Screen"
Cohesion: 0.31
Nodes (4): Boolean, Set, String, SettingsRepository

### Community 25 - "QuotaRepository"
Cohesion: 0.18
Nodes (8): App, AppContainer, Boolean, List, Application, com, ImageLoader, ImageLoaderFactory

### Community 26 - "Circular Progress View"
Cohesion: 0.29
Nodes (4): KeeperRankerTest, Int, Long, String

### Community 27 - "Step Connector View"
Cohesion: 0.40
Nodes (3): String, Uri, TreeUris

### Community 28 - "Settings Row Keys"
Cohesion: 0.50
Nodes (3): announceAsCheckable(), Boolean, String

### Community 29 - "Formatters"
Cohesion: 0.31
Nodes (5): Formatters, Int, Long, String, Locale

### Community 30 - "Main Activity"
Cohesion: 0.07
Nodes (24): ActivityMainBinding, Feature, Boolean, Context, Int, List, Long, StateFlow (+16 more)

### Community 31 - "Keeper Ranker"
Cohesion: 0.07
Nodes (81): DeleteScreen(), AccessCard(), HomeScreen(), Boolean, Modifier, ModePicker(), OrbCaption(), ResultCard() (+73 more)

### Community 32 - "Donut Chart View"
Cohesion: 0.29
Nodes (3): AppSettings, AutoScanScheduler, Long

### Community 33 - "Storage Permissions"
Cohesion: 0.27
Nodes (5): Array, Boolean, Context, String, Permissions

### Community 34 - "Launcher Icons & Manifest"
Cohesion: 0.22
Nodes (9): AndroidManifest.xml, App Launcher Icon (mdpi) — green grid tile with Android robot silhouette, Round App Launcher Icon (xhdpi), App Launcher Icon (xxhdpi), Default Android Robot Icon Artwork, Duplicate File Finder Launcher Icon (Android Mipmap Density Variant), Round App Launcher Icon (xxhdpi) — default Android adaptive icon: white Android robot head (bugdroid) with two green eyes and antennae on a green circular background with a light grid pattern and a diagonal long-shadow; the round launcher icon variant for the Duplicate File Finder app at xxhdpi density (~144x144px), Default Android Robot Logo (+1 more)

### Community 35 - "ScanResult"
Cohesion: 0.25
Nodes (6): KeeperRanker, Boolean, Int, List, Long, String

### Community 36 - "BinAdapter"
Cohesion: 0.20
Nodes (11): areContentsTheSame(), areItemsTheSame(), BinAdapter, Int, ListAdapter, RecyclerView, String, VH (+3 more)

### Community 37 - "ScanProgress"
Cohesion: 0.06
Nodes (32): DuplicateEngine, ScanRequest, ScanMode, DEEP, EXACT, SIMILAR, VERY_SIMILAR, ScanPhase (+24 more)

### Community 38 - "Scan Quota Use Case"
Cohesion: 0.27
Nodes (4): List, Long, Unit, RecycleBinRepository

### Community 39 - "App Languages"
Cohesion: 0.39
Nodes (4): AppLanguages, List, Pair, String

### Community 40 - "Models.kt"
Cohesion: 0.43
Nodes (6): Error, Result, Success, D, E, Nothing

### Community 41 - "Fragment Lifecycle Boilerplate"
Cohesion: 0.27
Nodes (7): ImageFeatureExtractor, ImageHashes, Long, String, Bitmap, Double, IntArray

### Community 42 - "Performance"
Cohesion: 0.26
Nodes (6): Boolean, Context, Long, Performance, Float, Window

### Community 43 - "RewardedAdManager"
Cohesion: 0.28
Nodes (6): Activity, Boolean, Pair, Unit, RewardedAdManager, RewardedAd

### Community 44 - "File Opener"
Cohesion: 0.40
Nodes (3): FileOpener, Context, String

### Community 47 - "Gradle Wrapper Script"
Cohesion: 0.83
Nodes (3): gradlew script, die(), warn()

### Community 49 - "Launcher Icon hdpi"
Cohesion: 0.67
Nodes (3): App Launcher Icon (hdpi) - Android Robot on Green Grid, Default Android Robot Launcher Motif, Duplicate File Finder Android App

### Community 50 - "Launcher Icon mdpi Round"
Cohesion: 0.67
Nodes (3): Round Launcher Icon (mdpi), Android Robot Mascot (default template icon), App Launcher Icon (Duplicate File Finder)

### Community 51 - "Launcher Icon xhdpi"
Cohesion: 0.67
Nodes (3): App Launcher Icon (xhdpi), Default Android Robot Icon Motif (white robot head on teal grid background), Duplicate File Finder Android App

### Community 52 - "Launcher Icon xxxhdpi"
Cohesion: 0.67
Nodes (3): App Launcher Icon (xxxhdpi), Default Android Studio Robot-Head Icon Template, Duplicate File Finder Android App

### Community 53 - "Launcher Icon xxxhdpi Round"
Cohesion: 0.67
Nodes (3): Round App Launcher Icon (xxxhdpi), Default Android Robot Launcher Icon Design, Duplicate File Finder Android App

### Community 65 - "Review Focus"
Cohesion: 0.22
Nodes (8): Compose + Nightmode Design Implementation Plan, Global Constraints, Review Focus, Task 12: Clean up and docs, Task 1: Compose build setup, Task 2: Design system (`ui/`), Task 3: MainActivity shell, Tasks 4–11: One screen per task

### Community 67 - ".scan"
Cohesion: 0.54
Nodes (3): Context, String, ThemePrefs

### Community 68 - "glassEffect"
Cohesion: 0.47
Nodes (5): GoProBadge(), Boolean, Dp, Modifier, ProHeaderBadge()

### Community 70 - "DispatcherProvider"
Cohesion: 0.83
Nodes (3): DefaultDispatcherProvider, DispatcherProvider, CoroutineDispatcher

### Community 75 - "CLAUDE.md"
Cohesion: 0.25
Nodes (6): Architecture, Build & test commands, Design rules (mandatory), graphify, Notes for future changes, Skills (mandatory, automatic)

## Knowledge Gaps
- **163 isolated node(s):** `SCAN_FOLDER`, `EXCLUDE`, `SCHEDULE`, `ARCHIVE`, `SELECT_ALL` (+158 more)
  These have ≤1 connection - possible missing edges or undocumented components.
- **45 thin communities (<3 nodes) omitted from report** — run `graphify query` to explore isolated nodes.

## Suggested Questions
_Questions this graph is uniquely positioned to answer:_

- **Why does `FileItem` connect `FileItem` to `Scan Result Repository`, `ScanResult`, `App Bootstrap & Dispatchers`, `ScanProgress`, `File Enumeration`, `Recycle Bin Repository`, `File Preview Adapter`, `File Type Detection`, `Shared Selection State`, `Circular Progress View`, `Keeper Ranker`?**
  _High betweenness centrality (0.166) - this node is a cross-community bridge._
- **Why does `FileCategory` connect `File Enumeration` to `Scan Result Repository`, `BinAdapter`, `Home Screen`, `File Preview Adapter`, `Scan Foreground Service`, `File Type Detection`, `Shared Selection State`, `Keeper Ranker`?**
  _High betweenness centrality (0.085) - this node is a cross-community bridge._
- **Why does `ScanProgress` connect `ScanProgress` to `Scan Result Repository`, `Scan Foreground Service`, `Notification Orchestrator`, `Keeper Ranker`?**
  _High betweenness centrality (0.069) - this node is a cross-community bridge._
- **What connects `SCAN_FOLDER`, `EXCLUDE`, `SCHEDULE` to the rest of the system?**
  _168 weakly-connected nodes found - possible documentation gaps or missing edges._
- **Should `Result & Error Handling` be split into smaller, more focused modules?**
  _Cohesion score 0.06060606060606061 - nodes in this community are weakly interconnected._
- **Should `Scan Result Repository` be split into smaller, more focused modules?**
  _Cohesion score 0.09176788124156546 - nodes in this community are weakly interconnected._
- **Should `Delete Screen` be split into smaller, more focused modules?**
  _Cohesion score 0.09047619047619047 - nodes in this community are weakly interconnected._