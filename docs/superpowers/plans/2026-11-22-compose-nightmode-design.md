# Compose + Nightmode Design Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Rebuild every Duplicatefilefinder screen in Jetpack Compose using the Night Mode app's design system: glass cards over a glow backdrop, slate surfaces with a blue accent, the animated Lottie Pro badge and lite mode for low-end phones.

**Architecture:** Fragments, Navigation and ViewModels stay. Each Fragment's `onCreateView` returns a `ComposeView` that renders a stateless `XxxScreen(state, callbacks)` composable inside `DfTheme`. That keeps permission launchers, the AdMob flow and the services untouched. `MainActivity` swaps its `BottomNavigationView` for a Compose floating capsule. One shared design system (`ui/`) is ported from Nightmode, and no screen styles anything on its own.

**Tech Stack:** Kotlin, Compose BOM 2024.12.01, Material3, material-icons (Rounded), lottie-compose 6.6.2, coil-compose 2.7.0 (thumbnails), AGP 9.2.1 with `org.jetbrains.kotlin.plugin.compose` 2.1.0. These are the same versions as Nightmode.

**Spec:** The user's decisions in this session (2026-11-22). Reference implementation, **read-only**: `C:\Users\madha\AndroidStudioProjects\Nightmode` (files `ui/Theme.kt`, `ui/Components.kt`, `ui/Glass.kt`, `Performance.kt`, `ui/SettingsShared.kt` `GoProBadge`, `ui/App.kt`/`HomeScreen.kt` `BottomNav`, `ui/ProScreen.kt`).

## Global Constraints

- NEVER edit, create or delete anything under `C:\Users\madha\AndroidStudioProjects\Nightmode`. Reading and copying content *out* of it is fine.
- Never run an emulator. Verify by building, then install on the phone: `adb -s e5299fb3 install -r app/build/outputs/apk/debug/app-debug.apk`.
- Build: `export JAVA_HOME="C:/Program Files/Android/Android Studio/jbr"; ./gradlew :app:assembleDebug --console=plain`.
- Colors: copy Nightmode's `DarkNm`/`LightNm` values exactly (accent `#4493F8` dark / `#2A85FF` light). Use no Color literals or raw dp in screens; read everything from `Df.c`, `Spacing`, `Radius`.
- Glass: cards use `glass` fill with a `glassEdge→line` vertical border, and every page uses `glassBackdrop()`. The glows use fixed `glowA`/`glowB` colours (this app has no Sunset/sky logic).
- Lite mode (`Performance.lite`): on for a low-RAM flag or under 3 GB RAM, or after two 2 s spans below `min(55, 0.6×refresh)` fps. In lite mode: no blur, the Pro badge plays once, and there are no infinite animations.
- Animated values are read only in `graphicsLayer`/`drawBehind`, never in composition.
- Every user-facing string goes in `res/values/strings.xml` (reuse existing keys), with plurals for counts. Touch targets are at least 48dp. Icons are `Icons.Rounded`, named by role in `ui/icons/AppIcons.kt`.
- Copy rules from DESIGN_RULES.md §7, §8, §17, §18 still apply: direct copy, no emoji, no hype.
- minSdk stays 24. Blur needs API 31+, and below that it falls back to a flat tint.

## Review Focus

- Font scale 1.3+ or a large Display size: rows must not clip. `DfTheme` pins density to `DENSITY_DEVICE_STABLE` and fontScale to 0.9–1.15 (Task 2).
- Light theme chosen in-app on a dark-mode phone: status and nav bar icons must stay readable. `DfTheme` sets `isAppearanceLight*Bars` from the app theme (Task 2).
- 5,000+ duplicate groups in Cleanup: scrolling must not jank. Use `LazyColumn` with `key = group.id`, and keep the derived state the ViewModel already computes off the main thread (Task 6).
- Rotation or process death mid-scan or mid-delete: the screens must re-attach to `ScanController`/`DeleteController` state and not restart work (Tasks 5, 8). Collect state with `collectAsStateWithLifecycle`, never start work from composition.
- Android 7–11 (API 24–30): no `RenderEffect` call may run. Gate it on `SDK_INT >= 31`, as in `Glass.kt` (Task 2).

---

### Task 1: Compose build setup

**Files:**
- Modify: `gradle/libs.versions.toml`, `app/build.gradle.kts`, `build.gradle.kts` (root)
- Create: `app/src/main/res/raw/pro_badge.json` (copy of Nightmode's file)

- [ ] Add these versions and libraries: `kotlin = "2.1.0"`, `composeBom = "2024.12.01"`, `activityCompose = "1.10.1"`, `lottie = "6.6.2"`, `coil = "2.7.0"`. The libraries are compose-bom, ui, ui-graphics, ui-tooling-preview, material3, material-icons-extended, activity-compose, lifecycle-runtime-compose (version.ref `lifecycle`), lifecycle-viewmodel-compose, lottie-compose and coil-compose. Add the plugin `kotlin-compose = { id = "org.jetbrains.kotlin.plugin.compose", version.ref = "kotlin" }`.
- [ ] In `app/build.gradle.kts`: apply `alias(libs.plugins.kotlin.compose)`, set `buildFeatures { compose = true; viewBinding = true }` and add the dependencies. Root: `alias(libs.plugins.kotlin.compose) apply false`.
- [ ] `cp` Nightmode's `app/src/main/res/raw/pro_badge.json` → this app's `res/raw/`.
- [ ] Run the build. Expected: `BUILD SUCCESSFUL`.
- [ ] Commit `build: add Compose, Lottie, Coil`.

### Task 2: Design system (`ui/`)

**Files (all under `app/src/main/java/com/bunkwise/duplicatefilefinder/`):**
- Create: `ui/Theme.kt`, `ui/Glass.kt`, `ui/Components.kt`, `ui/BottomNav.kt`, `ui/ProBadge.kt`, `ui/icons/AppIcons.kt`, `Performance.kt`
- Test: `app/src/test/java/com/bunkwise/duplicatefilefinder/PerformanceTest.kt`

**Interfaces (produces):**
- `class DfColors` with fields identical to Nightmode `NmColors`. `object Df { val c: DfColors @Composable }`. `object Spacing`, `object Radius`, `object Motion`, `DfTypography`, `val Mono`.
- `@Composable fun DfTheme(content: @Composable () -> Unit)`: dark/light comes from `ThemePrefs` (existing `presentation/common/ThemePrefs.kt`), with the density cap and bar icons as in Nightmode.
- `Modifier.glassBackdrop()` (fixed glows, no Sunset). `Glass`, `rememberGlass()`, `Modifier.glassSource()`, `Modifier.glassEffect(...)`.
- Components with the same signatures as Nightmode: `AppCard`, `RowCard`, `RowDivider`, `SectionLabel`, `InfoNote`, `IconBox`, `ListRow`, `SwitchRow(locked, onLockedTap)`, `StatusRow`, `SelectableRow`, `DfSwitch`, `PrimaryButton`, `TonalButton`, `SmallAccentButton`, `DangerButton`, `Disclosure`, `DfSheet`, `Chip`, `Tag`, `ProBadge(isPremium: Boolean)`, `LockChip`, `EmptyState`, `Modifier.navBarPadding()`, `IconSize`, `IconSizeSmall`, `MinTouchTarget`, and `@Composable fun ScreenHeader(title: String, onBack: (() -> Unit)?, trailing: @Composable RowScope.() -> Unit = {})`.
- `@Composable fun GoProBadge(height: Dp)` (Lottie, `iterations = if (Performance.lite) 1 else IterateForever`) and `@Composable fun ProHeaderBadge(isPremium: Boolean, onClick: () -> Unit)`.
- `@Composable fun BottomNav(selected: Tab, onSelect: (Tab) -> Unit)`, where `enum class Tab { HOME, CLEANUP, SETTINGS }` is a dark capsule with a dot under the active tab. It uses the plain `navBg` tint because Fragment content isn't in the same Compose tree, so there is no page blur behind the nav.
- `object Performance { val lite: Boolean; fun init(context); fun watch(window); fun unwatch(window); internal fun isLowEnd(lowRam: Boolean, totalMem: Long): Boolean; internal fun isSlow(fps: Float, refresh: Float): Boolean }`. Drop Nightmode's `Track` calls.

- [ ] **Write the failing test** `PerformanceTest`:
```kotlin
@Test fun lowEnd() {
    assertTrue(Performance.isLowEnd(lowRam = true, totalMem = 8L shl 30))
    assertTrue(Performance.isLowEnd(false, (3L shl 30) - 1))
    assertFalse(Performance.isLowEnd(false, 3L shl 30))
}
@Test fun slow() {
    assertTrue(Performance.isSlow(fps = 50f, refresh = 120f))   // < min(55, 72)
    assertFalse(Performance.isSlow(fps = 37f, refresh = 60f))   // threshold 36
    assertTrue(Performance.isSlow(fps = 35f, refresh = 60f))
}
```
- [ ] Run `./gradlew testDebugUnitTest --tests "*PerformanceTest*"`. Expected: FAIL (unresolved).
- [ ] Port the files from Nightmode, renaming `Nm`→`Df`. Replace `DimState.proUnlocked` with an `isPremium` parameter, and `Haptics` with `LocalView.current.performHapticFeedback`.
- [ ] Run the test. Expected: PASS. Run the build. Expected: `BUILD SUCCESSFUL`.
- [ ] Commit `feat(ui): port Nightmode design system to Compose`.

### Task 3: MainActivity shell

**Files:** Modify `MainActivity.kt` and `res/layout/activity_main.xml` (replace `bottomNav` and `bottomNavDivider` with a `ComposeView` `@+id/bottomNavCompose` that overlays the NavHost at the bottom). Also modify `App.kt` (`Performance.init(this)`) and `res/values*/themes.xml` (parent `Theme.Material3.DayNight.NoActionBar`, transparent bars, window background `bg`).

- [ ] Hold the current tab in `mutableStateOf<Tab?>`, set it from `addOnDestinationChangedListener` (null hides the nav), and call `selectTab()` on tap. Call `Performance.watch(window)` in onResume and `unwatch` in onPause. Tab-root screens add bottom padding `LocalBottomNavClearance = 88.dp`.
- [ ] Build, install, open each tab. Expected: the capsule shows on the 3 tabs only and the back button behaves as before.
- [ ] Commit `feat(ui): Compose bottom nav capsule`.

### Tasks 4–11: One screen per task

Each screen task follows the same steps:
1. Create `presentation/<pkg>/<Name>Screen.kt` with a stateless `@Composable fun <Name>Screen(state: …, on…: () -> Unit, …)`. Its root is `Box(Modifier.fillMaxSize().glassBackdrop())`, using `ScreenHeader` and the components from Task 2 only.
2. Change the Fragment's `onCreateView` to return `ComposeView(requireContext()).apply { setViewCompositionStrategy(DisposeOnViewTreeLifecycleDestroyed); setContent { DfTheme { … } } }`. Collect state with `collectAsStateWithLifecycle()`, and keep the existing event, permission, dialog and nav code in the Fragment.
3. Delete the screen's layout XML and any RecyclerView adapter it used.
4. Build, install and walk the screen's flows on the phone. Expected: every action from the old screen still works.
5. Commit `feat(ui): <Name> in Compose`.

| Task | Fragment | Screen content | Removes |
|---|---|---|---|
| 4 | HomeFragment | Title and `ProHeaderBadge` header. Scan-mode `SelectableRow`s in a `RowCard`. File-type checks. Storage status as `StatusRow`. `PrimaryButton` "Scan". The existing All-files and notification dialogs become `DfSheet`. | fragment_home.xml, item_check_row, item_radio_row |
| 5 | ScanFragment | Mono progress figures (files and bytes), linear progress, current path, `TonalButton` Stop. | fragment_scan.xml |
| 6 | CleanupFragment | Search field, filter `Chip`s with counts, sort menu, `LazyColumn(key=id)` of group `AppCard`s (Coil thumbnail, keeper marked, checkbox), and a docked "Review N · size" `PrimaryButton`. `EmptyState` when empty. | fragment_cleanup.xml, item_group_card, GroupAdapter |
| 7 | PreviewFragment | List of group files with thumbnail, path, size and date, a per-file checkbox, and a Keep tag on the keeper. Shows the toast when `toggleFile` returns false. | fragment_preview.xml, item_preview_file, PreviewFileAdapter |
| 8 | ReviewDeleteFragment | Summary figures, kept vs removed lists, blocker `InfoNote`s (permission and scan running), and a `DangerButton`/`PrimaryButton` to confirm. | fragment_reviewdelete.xml, item_file_row |
| 9 | DeleteFragment | Progress, then the result: freed bytes (mono), skipped list, Undo `TonalButton`, Done. | fragment_delete.xml |
| 10 | SettingsFragment + RecycleBinFragment | Settings as `SectionLabel` + `RowCard` of `ListRow`/`SwitchRow`, with premium items shown as `locked` and a `LockChip`. Theme and language pickers in `DfSheet` with `SelectableRow`. Recycle bin as `LazyColumn` rows with restore/delete and an `EmptyState`. | fragment_settings.xml, fragment_recyclebin.xml, item_settings_row, item_section_label, item_bin_entry, BinAdapter |
| 11 | PremiumFragment | Nightmode Pro-page layout: a `GoProBadge(40.dp)` hero, feature `ListRow`s with `IconBox`, a plan `AppCard`, and a docked `PrimaryButton`. The existing purchase/ad hook is unchanged (billing is out of scope). | fragment_premium.xml |

### Task 12: Clean up and docs

- [ ] Remove `glide`, `recyclerview`, `swiperefreshlayout` and `constraintlayout` if `grep -r` finds no remaining usages. Remove ViewBinding if only `ActivityMainBinding` is left (inflate it manually instead).
- [ ] Update `DESIGN_RULES.md`: add a section at the top on glass cards, the backdrop glows and nav capsule blur, which are allowed only through `ui/Components.kt`. Update the "Architecture" section of `CLAUDE.md` (Compose screens in Fragments, the `ui/` design system, Performance lite mode).
- [ ] Run `graphify update .`, build, install, and do a full flow on the phone: scan → cleanup → review → delete → undo → recycle bin → settings → premium, in both themes.
- [ ] Commit `chore: drop View-era deps, document Compose design system`.
