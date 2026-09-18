# Graph Report - Duplicatefilefinder  (2026-07-11)

## Corpus Check
- 82 files · ~27,367 words
- Verdict: corpus is large enough that graph structure adds value.

## Summary
- 1034 nodes · 1579 edges · 100 communities (57 shown, 43 thin omitted)
- Extraction: 98% EXTRACTED · 2% INFERRED · 0% AMBIGUOUS · INFERRED: 36 edges (avg confidence: 0.83)
- Token cost: 0 input · 0 output

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
- Bouncing Dots View
- Folder Picker Targets
- Cleanup Filters & Sort
- Scan Quota Use Case
- App Languages
- UiText Abstraction
- Fragment Lifecycle Boilerplate
- Standard Folders
- Byte Comparator
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
- Result
- .scan
- RecycleBinRepository
- RewardedAdManager
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
1. `FileCategory` - 32 edges
2. `SettingsFragment` - 26 edges
3. `SharedSelectionViewModel` - 24 edges
4. `RecycleBinRepositoryImpl` - 22 edges
5. `FileItem` - 21 edges
6. `HomeFragment` - 21 edges
7. `DetectedType` - 20 edges
8. `ScanResult` - 18 edges
9. `HomeViewModel` - 18 edges
10. `FileEnumeratorImpl` - 17 edges

## Surprising Connections (you probably didn't know these)
- `Round App Launcher Icon (xxhdpi) — default Android adaptive icon: white Android robot head (bugdroid) with two green eyes and antennae on a green circular background with a light grid pattern and a diagonal long-shadow; the round launcher icon variant for the Duplicate File Finder app at xxhdpi density (~144x144px)` --semantically_similar_to--> `App Launcher Icon (xxhdpi)`  [INFERRED] [semantically similar]
  app/src/main/res/mipmap-xxhdpi/ic_launcher_round.webp → app/src/main/res/mipmap-xxhdpi/ic_launcher.webp
- `FileEnumeratorImpl` --implements--> `FileEnumerator`  [EXTRACTED]
  app/src/main/java/com/bunkwise/duplicatefilefinder/core/data/FileEnumeratorImpl.kt → app/src/main/java/com/bunkwise/duplicatefilefinder/core/domain/repository/Repositories.kt
- `QuotaRepositoryImpl` --implements--> `QuotaRepository`  [EXTRACTED]
  app/src/main/java/com/bunkwise/duplicatefilefinder/core/data/QuotaRepositoryImpl.kt → app/src/main/java/com/bunkwise/duplicatefilefinder/core/domain/repository/Repositories.kt
- `RecycleBinRepositoryImpl` --implements--> `RecycleBinRepository`  [EXTRACTED]
  app/src/main/java/com/bunkwise/duplicatefilefinder/core/data/RecycleBinRepositoryImpl.kt → app/src/main/java/com/bunkwise/duplicatefilefinder/core/domain/repository/Repositories.kt
- `AppContainer` --references--> `RecycleBinRepositoryImpl`  [EXTRACTED]
  app/src/main/java/com/bunkwise/duplicatefilefinder/di/AppContainer.kt → app/src/main/java/com/bunkwise/duplicatefilefinder/core/data/RecycleBinRepositoryImpl.kt

## Import Cycles
- None detected.

## Hyperedges (group relationships)
- **Duplicate-Detection Pipeline Stages** — claude_duplicateengineimpl, claude_fileenumeratorimpl, claude_quickhasher, claude_streaminghasher, claude_unionfind, claude_imagefeatureextractor, claude_dhasher, claude_hamming, claude_keeperranker, claude_magicbytesniffer [EXTRACTED 1.00]
- **Manual DI Composition Root** — claude_appcontainer, claude_appviewmodelfactory, claude_app [EXTRACTED 1.00]
- **Foreground Service Long-Running Work Layer** — claude_scanforegroundservice, claude_scancontroller, claude_deleteforegroundservice, claude_deletecontroller, claude_autoscanscheduler, claude_autoscanworker, claude_notificationorchestrator [EXTRACTED 1.00]

## Communities (100 total, 43 thin omitted)

### Community 0 - "Result & Error Handling"
Cohesion: 0.11
Nodes (17): Delete, BIN_FULL, FILE_CHANGED, FILE_IN_USE, PERMISSION_DENIED, TARGET_MISSING, UNKNOWN, Local (+9 more)

### Community 1 - "Scan Result Repository"
Cohesion: 0.05
Nodes (31): Flow, Int, String, QuotaRepositoryImpl, Long, Set, StateFlow, ScanResultRepositoryImpl (+23 more)

### Community 2 - "Delete Screen"
Cohesion: 0.06
Nodes (27): DeleteFragment, Bundle, Fragment, LayoutInflater, String, View, ViewGroup, Completed (+19 more)

### Community 3 - "Recycle Bin UI"
Cohesion: 0.06
Nodes (25): areContentsTheSame(), areItemsTheSame(), BinAdapter, Int, ListAdapter, RecyclerView, String, VH (+17 more)

### Community 4 - "App Bootstrap & Dispatchers"
Cohesion: 0.53
Nodes (5): areContentsTheSame(), areItemsTheSame(), GroupAdapter, ListAdapter, GroupUi

### Community 5 - "Home Screen"
Cohesion: 0.07
Nodes (19): CatMeta, HomeFragment, Bundle, Fragment, Int, LayoutInflater, View, ViewGroup (+11 more)

### Community 6 - "File Enumeration"
Cohesion: 0.13
Nodes (21): FileEnumeratorImpl, Boolean, Flow, Int, Long, Map, Set, String (+13 more)

### Community 7 - "ViewModel Factory Wiring"
Cohesion: 0.05
Nodes (24): AppViewModelFactory, DeleteViewModel, ViewModel, Bundle, Fragment, LayoutInflater, View, ViewGroup (+16 more)

### Community 8 - "Scan Modes & Progress"
Cohesion: 0.08
Nodes (22): ScanMode, DEEP, EXACT, SIMILAR, VERY_SIMILAR, ScanProgress, Bundle, Fragment (+14 more)

### Community 9 - "Scan Phases & UnionFind"
Cohesion: 0.08
Nodes (19): DuplicateEngine, ScanRequest, ScanPhase, COMPARING, DONE, ENUMERATING, GROUPING, HASHING (+11 more)

### Community 10 - "Recycle Bin Repository"
Cohesion: 0.13
Nodes (12): Array, Boolean, Int, List, Long, Set, StateFlow, String (+4 more)

### Community 11 - "File Preview Adapter"
Cohesion: 0.10
Nodes (18): areContentsTheSame(), areItemsTheSame(), Int, ListAdapter, RecyclerView, VH, ViewGroup, PreviewFileAdapter (+10 more)

### Community 13 - "Scan Foreground Service"
Cohesion: 0.15
Nodes (12): android, Context, IBinder, Int, Intent, Job, Service, Set (+4 more)

### Community 14 - "File Type Detection"
Cohesion: 0.09
Nodes (18): DetectedType, AUDIO, BINARY, BMP, DOCX, GIF, HEIF, JPEG (+10 more)

### Community 15 - "Results Screen"
Cohesion: 0.14
Nodes (11): Bundle, Fragment, Int, LayoutInflater, View, ViewGroup, ResultsFragment, ViewModel (+3 more)

### Community 16 - "Cleanup Screen"
Cohesion: 0.14
Nodes (8): CleanupFragment, Bundle, Fragment, LayoutInflater, Long, View, ViewGroup, FragmentCleanupBinding

### Community 17 - "Duplicate Group Adapter"
Cohesion: 0.24
Nodes (7): com, Int, RecyclerView, VH, ViewGroup, VH, ImageView

### Community 18 - "FileItem"
Cohesion: 0.23
Nodes (12): Result, DeletionEngineImpl, Boolean, List, String, Uri, AppError, FileItem (+4 more)

### Community 19 - "Settings Screen"
Cohesion: 0.06
Nodes (32): AppSettings, FolderTarget, ARCHIVE, EXCLUDE, SCAN, Boolean, Bundle, Fragment (+24 more)

### Community 20 - "Shared Selection State"
Cohesion: 0.16
Nodes (7): Boolean, List, Long, StateFlow, String, ViewModel, SharedSelectionViewModel

### Community 21 - "Notification Orchestrator"
Cohesion: 0.20
Nodes (6): android, Boolean, Int, String, NotificationOrchestrator, PendingIntent

### Community 22 - "Hashing Primitives"
Cohesion: 0.18
Nodes (6): Hex, QuickHasher, Canceller, StreamingHasher, MessageDigest, RandomAccessFile

### Community 23 - "Settings Repository"
Cohesion: 0.22
Nodes (5): Boolean, Flow, Set, String, SettingsRepositoryImpl

### Community 24 - "Review Delete Screen"
Cohesion: 0.20
Nodes (7): Bundle, Fragment, LayoutInflater, View, ViewGroup, ReviewDeleteFragment, FragmentReviewdeleteBinding

### Community 25 - "QuotaRepository"
Cohesion: 0.12
Nodes (9): FileEnumerator, Boolean, Flow, Int, Map, Set, String, QuotaRepository (+1 more)

### Community 26 - "Circular Progress View"
Cohesion: 0.17
Nodes (8): CircularProgressView, Boolean, Canvas, Float, Int, View, SweepGradient, ValueAnimator

### Community 27 - "Step Connector View"
Cohesion: 0.21
Nodes (8): Boolean, Canvas, Float, Int, Unit, View, StepConnectorView, MotionEvent

### Community 28 - "Settings Row Keys"
Cohesion: 0.13
Nodes (10): Bundle, Fragment, LayoutInflater, View, ViewGroup, OnboardingFragment, String, ViewModel (+2 more)

### Community 29 - "Formatters"
Cohesion: 0.26
Nodes (6): Formatters, Boolean, Int, Long, String, Locale

### Community 30 - "Main Activity"
Cohesion: 0.23
Nodes (7): ActivityMainBinding, Boolean, Bundle, Intent, MainActivity, AppCompatActivity, NavController

### Community 31 - "Keeper Ranker"
Cohesion: 0.25
Nodes (6): KeeperRanker, Boolean, Int, List, Long, String

### Community 32 - "Donut Chart View"
Cohesion: 0.27
Nodes (7): DonutChartView, Canvas, Float, Int, List, View, Slice

### Community 33 - "Storage Permissions"
Cohesion: 0.31
Nodes (5): Array, Boolean, Context, String, Permissions

### Community 34 - "Launcher Icons & Manifest"
Cohesion: 0.22
Nodes (9): AndroidManifest.xml, App Launcher Icon (mdpi) — green grid tile with Android robot silhouette, Round App Launcher Icon (xhdpi), App Launcher Icon (xxhdpi), Default Android Robot Icon Artwork, Duplicate File Finder Launcher Icon (Android Mipmap Density Variant), Round App Launcher Icon (xxhdpi) — default Android adaptive icon: white Android robot head (bugdroid) with two green eyes and antennae on a green circular background with a light grid pattern and a diagonal long-shadow; the round launcher icon variant for the Duplicate File Finder app at xxhdpi density (~144x144px), Default Android Robot Logo (+1 more)

### Community 35 - "Bouncing Dots View"
Cohesion: 0.22
Nodes (4): BouncingDotsView, Canvas, Int, View

### Community 36 - "Folder Picker Targets"
Cohesion: 0.19
Nodes (9): App, DefaultDispatcherProvider, DispatcherProvider, AppContainer, Boolean, com, List, Application (+1 more)

### Community 37 - "Cleanup Filters & Sort"
Cohesion: 0.25
Nodes (6): CleanupState, Filters, SortMode, DUPLICATES, NAME, SIZE

### Community 38 - "Scan Quota Use Case"
Cohesion: 0.48
Nodes (5): Allowed, CheckScanQuotaUseCase, Exceeds, Set, QuotaDecision

### Community 39 - "App Languages"
Cohesion: 0.38
Nodes (4): AppLanguages, List, Pair, String

### Community 40 - "UiText Abstraction"
Cohesion: 0.38
Nodes (5): Context, String, Raw, Res, UiText

### Community 41 - "Fragment Lifecycle Boilerplate"
Cohesion: 0.28
Nodes (7): ImageFeatureExtractor, ImageHashes, Long, String, Bitmap, Double, IntArray

### Community 42 - "Standard Folders"
Cohesion: 0.50
Nodes (3): Folder, List, StandardFolders

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

### Community 66 - "Result"
Cohesion: 0.40
Nodes (5): Error, Success, D, E, Nothing

### Community 67 - ".scan"
Cohesion: 0.54
Nodes (3): Context, String, ThemePrefs

### Community 68 - "RecycleBinRepository"
Cohesion: 0.24
Nodes (4): List, Long, Unit, RecycleBinRepository

### Community 69 - "RewardedAdManager"
Cohesion: 0.33
Nodes (4): Activity, Boolean, RewardedAdManager, RewardedInterstitialAd

### Community 75 - "CLAUDE.md"
Cohesion: 0.33
Nodes (4): Architecture, Build & test commands, graphify, Notes for future changes

## Knowledge Gaps
- **124 isolated node(s):** `PrefKeys`, `PERMISSION_LOST`, `STORAGE_UNAVAILABLE`, `QUOTA_EXCEEDED`, `CANCELLED` (+119 more)
  These have ≤1 connection - possible missing edges or undocumented components.
- **43 thin communities (<3 nodes) omitted from report** — run `graphify query` to explore isolated nodes.

## Suggested Questions
_Questions this graph is uniquely positioned to answer:_

- **Why does `FileItem` connect `FileItem` to `Scan Result Repository`, `File Enumeration`, `Scan Phases & UnionFind`, `File Preview Adapter`, `Cleanup Screen`, `Duplicate Group Adapter`, `Shared Selection State`, `QuotaRepository`, `Keeper Ranker`?**
  _High betweenness centrality (0.149) - this node is a cross-community bridge._
- **Why does `SharedSelectionViewModel` connect `Shared Selection State` to `Cleanup Filters & Sort`, `ViewModel Factory Wiring`, `File Preview Adapter`, `Results Screen`, `Cleanup Screen`, `Review Delete Screen`?**
  _High betweenness centrality (0.117) - this node is a cross-community bridge._
- **Why does `FileCategory` connect `File Enumeration` to `Scan Result Repository`, `Recycle Bin UI`, `Home Screen`, `Scan Quota Use Case`, `Scan Foreground Service`, `File Type Detection`, `Results Screen`, `Shared Selection State`, `QuotaRepository`?**
  _High betweenness centrality (0.102) - this node is a cross-community bridge._
- **What connects `PrefKeys`, `PERMISSION_LOST`, `STORAGE_UNAVAILABLE` to the rest of the system?**
  _129 weakly-connected nodes found - possible documentation gaps or missing edges._
- **Should `Result & Error Handling` be split into smaller, more focused modules?**
  _Cohesion score 0.1111111111111111 - nodes in this community are weakly interconnected._
- **Should `Scan Result Repository` be split into smaller, more focused modules?**
  _Cohesion score 0.05446853516657853 - nodes in this community are weakly interconnected._
- **Should `Delete Screen` be split into smaller, more focused modules?**
  _Cohesion score 0.0603921568627451 - nodes in this community are weakly interconnected._