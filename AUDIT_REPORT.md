# Duplicate File Finder: App Audit (2026-09-18)

**Severity key:** **P0** = can lose user data, or blocks release · **P1** = a feature is broken or behaves wrongly · **P2** = polish or efficiency.
All paths are relative to `app/src/main/java/com/bunkwise/duplicatefilefinder/` unless they start with `res/`.

## 1. Critical bugs (P0): fix these first

| # | Bug | Where | What happens | Fix |
|---|-----|-------|--------------|-----|
| 1 | **Archive deletes originals before the zip is complete** | `core/data/DeletionEngineImpl.kt:98-117` | Each original is deleted (`src.delete()`, line 109) right after its entry is written, but the zip's central directory is only written by `zip.close()` at the end. If the app is killed, the user force-stops it, the phone reboots, or storage fills up mid-batch, the zip can't be opened **and the originals are already gone**. If storage fills, `close()` throws, the whole batch reports "failed", and results are not updated, yet files were deleted. | Two phases: (1) write every entry, close the zip, re-open it and verify entry count and sizes; (2) only then delete the originals. |
| 2 | **"Keep at least one copy" can be bypassed, so every copy gets deleted** | `presentation/shared/SharedSelectionViewModel.kt:145-171` | In Preview, the user marks the *keeper* (allowed, because another copy is still unmarked). Back in Cleanup they tap the group checkbox. `toggleGroup` sees no non-keepers marked and marks all of them. Now **every file in the group is marked**, and Review & Delete / DeletionEngine don't re-check. | Enforce the invariant in one place. `toggleGroup` should add non-keepers and remove the keeper. `ReviewDeleteFragment.proceed()` (or `selectedFiles()`) should drop the last survivor of any fully-marked group. |
| 3 | **Hidden storage leak in the recycle bin** | `core/data/RecycleBinRepositoryImpl.kt:176-181` | Rename fails (no All-files access, or a different volume). The code copies the file into the bin, then `src.delete()` fails. The function returns `false`, but **the copy stays in `recycle_bin/` with no index entry**. The file is invisible in the bin UI, is never purged, and permanently wastes space. | If `src.delete()` fails, delete `dest` too. |
| 4 | **Keeper recommendation usually picks the copy, not the original** | `core/engine/KeeperRanker.kt:31` | For exact duplicates, resolution and size tie, so **newest modified time wins**. Copies (WhatsApp/Download, `photo (1).jpg`) are almost always newer than the DCIM/Camera original. The folder and copy-name rules (4 and 5) are almost never reached because timestamps rarely tie. Smart Cleanup then suggests deleting the originals. | For exact groups: folder score, then copy-name, then **oldest** first, then name length. Keep resolution/size first only for similar-image groups. |
| 5 | **Premium is a free test toggle** | `presentation/premium/PremiumFragment.kt:33`, `strings.xml:215` | Anyone can tap "Activate premium (test)". No billing and no restore purchases. | Integrate Play Billing before release. Remove the test button in release builds. |
| 6 | **Privacy claim contradicts the ads SDK** | `res/values/strings.xml:77, 80, 232`, `AndroidManifest.xml:23`, `App.kt:31` | The copy says "Nothing ever leaves your phone", but AdMob plus INTERNET sends device/ad data. That's a trust problem and a Play Data-safety/policy risk. There is also no UMP consent (GDPR) flow. | Reword to "Your files never leave your phone". Add the UMP consent form. Fill in Data safety accurately. |
| 7 | **Scan state can get stuck forever** | `service/ScanForegroundService.kt:73, 165-169` | If the service is destroyed without reaching a terminal status (OEM kill, "stop app" from the notification shade), `onDestroy` cancels the job but never calls `scanController.onStopped()`. `isRunning` stays `true`, so **every later scan is silently ignored** (the "idempotent" return) and the Scan screen spins forever until the process dies. | In `onDestroy`: `if (container.scanController.isRunning) container.scanController.onStopped()`. Do the same for `DeleteForegroundService`. |
| 8 | **Test AdMob IDs** | `ads/RewardedAdManager.kt:86`, `AndroidManifest.xml:42` | Already tracked in memory; listed here for completeness. | Swap to real IDs at release. |

## 2. Archive-instead-of-delete: deep dive

How it works today: Review & Delete → `DeleteForegroundService` → `DeletionEngineImpl.archiveAll()`. It opens a zip in the SAF folder (or the fallback `Android/data/<pkg>/files/archives/`), and for each file writes an entry and then deletes the original.

Problems, in order of impact:
1. **Data loss on interruption** (P0 #1 above).
2. **Duplicate names break archiving (P1).** Entries are named `file.displayName` (line 106). Duplicates *very often share a name* (e.g. `IMG_2041.jpg` in DCIM and in WhatsApp). The second `putNextEntry` throws `ZipException: duplicate entry`, so the file is skipped as UNKNOWN. **Archive mode fails on exactly the files it's meant for.** Fix: use a unique entry path, e.g. `<index>_<parentFolder>/<name>`, or the original relative path.
3. **No way to restore (P1).** The zip holds no record of where files came from, and the app has no "restore from archive" screen. Fix: write a `manifest.json` entry (original path, size, sha256, date) and add an Archives list with a restore option (see features).
4. **Fallback folder frees almost no space and disappears on uninstall (P1).** If archive mode is on but no SAF folder is set (the user cancelled the picker, `SettingsFragment.kt:218-222`), zips go into `Android/data/.../archives`. That's the same storage, JPEG/MP4 hardly compress, so it saves ~0 bytes. It's **wiped when the app is uninstalled** and can't be browsed on Android 11+. The UI still says "X freed". Fix: require a folder when archive mode is on (turn the toggle back off if the picker is cancelled) and remove the silent fallback.
5. **Gallery shows ghost thumbnails (P1).** After archiving, `MediaScannerConnection.scanFile` is never called for the removed originals. The bin path does call it. Fix: call `bin.scan(paths)` or share one helper.
6. **Files that can't be deleted end up both archived and still on disk (P1).** Without All-files access, `src.delete()` fails *after* the file was written into the zip. Fix: check write access (`Permissions.hasAllFilesAccess()`) before starting and block with an explanation.
7. **No free-space check for the SAF destination (P2).** The check only runs for the fallback folder (line 89-90).
8. **Wasted CPU and battery (P2).** `ZipOutputStream` defaults to DEFLATE, and compressing already-compressed media does nothing useful. Use `zip.setLevel(Deflater.NO_COMPRESSION)` (or STORED entries).
9. **Wrong error text (P2).** When archiving fails, `DeleteFragment.kt:74` shows "Scan could not finish". There's also no retry/back button, and the progress ring stays visible.
10. **Progress only moves after each whole file (P2).** A 2 GB video looks frozen. Report bytes copied.
11. **Old SAF grants are never released.** Picking a new archive folder keeps the old persisted permission (`SettingsFragment.kt:236-240`). Scan/Exclude picks also take persistable grants they don't need, because they're converted to paths.

## 3. Navigation audit

**What works:** tab switching (manual wiring in `MainActivity.kt:117-128` avoids the old "tab does nothing" bug), the bottom bar hiding on overlays, selected-tab sync, back from overlays, the notification deep-link to Results, and the Scan/Delete back-press guards.

**Problems:**
1. **Home is destroyed and rebuilt on every Home tab tap (confirms your suspicion).** The Home tab navigates with `popUpTo(homeFragment) { inclusive = true }` (`MainActivity.kt:121`), and so do `ResultsFragment.kt:54` and `DeleteFragment.kt:101`. That pops the Home entry, which clears `HomeViewModel` (it's fragment-scoped). The 60 s size cache (`HomeViewModel.kt:60`) is lost, so **every return to Home re-sweeps the entire MediaStore** and replays the ring animation. Even when Home survives (system back), the cache expires after 60 s and `onResume` recomputes. Fix: for Home use `navController.popBackStack(R.id.homeFragment, false)`, and move the cache to the app-scoped container (see §5).
2. **Cleanup tab with no scan is a blank screen.** `CleanupFragment.kt:124` only shows the empty text when `hasResult` is true. With no scan you see four "0" stat cards and nothing to tap. Add an empty state with a "Start a scan" button.
3. **Results empty state says "Go Home".** The action should be "Start scan" (information scent).
4. **You can't leave the Scan screen without stopping the scan.** Back means Stop (`ScanFragment.kt:48-52`) and the bottom bar is hidden, although the foreground service already supports running in the background. Add "Run in background" plus a "Scanning… 42%" chip on Home.
5. **Stale status race when starting a new scan.** `HomeFragment.startScan` starts the service and navigates straight away. If `scanController.status` still holds an old `Completed` (e.g. the user left the last scan through the notification, so it was never consumed), `ScanFragment` handles it **before** the service's `onStart` sets `Running`, jumps to Results with the old data, and the real scan continues invisibly. Fix: call `scanController.reset()`/`onStart` synchronously before navigating.
6. **After process death, Scan/Delete screens come back as dead spinners.** Navigation restores `ScanFragment`/`DeleteFragment`, but the controllers are fresh `Idle`. The user must tap Stop to escape. Fix: in `onViewCreated`, if `status is Idle && !isRunning`, pop back.
7. **A notification tap during a running delete** pops `DeleteFragment` (`MainActivity.kt:99-105`), so the user can't see the delete finish. Better: don't deep-link while Scan/Delete is on screen.
8. **The Recycle Bin is hard to find:** Settings → row 3. It's the main safety net and deserves a top-level spot (see §6).

## 4. Other bugs and edge cases (P1/P2)

**Selection and results**
- **Stale selection after deleting (P1):** `SharedSelectionViewModel.kt:78-87` only resets the selection when `finishedAtMs` changes, and `removeFiles` keeps the same stamp. `markedFiles = selected.size` (line 111) still counts deleted ids, so Cleanup shows "12 files selected · 0 B" with the button enabled. Fix: prune ids not in the result, or clear on `DeleteStatus.Completed`.
- If the keeper is deleted elsewhere, `removeFiles` picks `kept.first()` (`ScanResultRepositoryImpl.kt:57`) instead of re-ranking with `KeeperRanker`.
- Results never go stale-checked. Files changed or deleted outside the app remain until the next scan. Show "Scanned 3 days ago" and check existence when opening Preview.
- The group checkbox shows "checked" when *any* file is marked. It needs a partial (tri-state) look.
- The group header shows `totalBytes`, but sorting uses `wastedBytes`, so the order looks wrong.
- Smart Cleanup (`selectAllGroups`) ignores the active category filter.

**Scanning engine**
- **Similar/Very Similar/Deep modes over-promise (P1):** candidates come from 4 bands of 16 bits (`DuplicateEngineImpl.kt:246-264`), which only *guarantees* finding pairs with Hamming distance ≤ 3. Thresholds of 10 (Very Similar) and 14 (Deep) are mostly never reached, so those modes find little more than Similar. Fix: 8 bands × 8 bits (guarantees ≤ 7) or multi-probe, *or* drop a mode (see §7).
- EXIF orientation is ignored (`ImageFeatureExtractor.kt:103-124`), so a rotated copy of the same photo isn't matched.
- `MagicByteSniffer` is **never called**. CLAUDE.md says types are verified by magic bytes, but categories come from MIME/extension only. Either wire it in or delete it (and fix CLAUDE.md).
- Files under 1 KB are silently skipped (`FileEnumeratorImpl.kt:31`). Fine, but say so in About/FAQ.
- The "duplicates found" counter during a scan counts *files in groups* and then jumps to *duplicates* at the end.

**Quota and permissions**
- **"Scan within my free limit (0 files)" (P1):** when `remaining == 0` the sheet still offers it, and `coerceAtLeast(1)` scans a single file (`HomeViewModel.kt:139-143`). Hide that option when remaining is 0.
- **Rotation while the notification-permission dialog is open loses the scan (P1):** `pendingScan` (`HomeFragment.kt:56`) is a plain field, so after recreation the result callback finds `null` and the scan never starts. Save it in the ViewModel/SavedStateHandle.
- **Permanently denied storage permission means Grant does nothing (P1):** `requestStorageAccess` (`HomeFragment.kt:219-223`) relaunches the system dialog, which Android suppresses. Detect `!shouldShowRequestPermissionRationale` after a denial and open App Settings.
- Audio can't be picked on Home. The chip list (`HomeFragment.kt:46-52`) has no AUDIO, though Cleanup filters by it.
- The +500-file ad bonus is tiny against a 10,000/day limit. A 25k-file phone would need 30 ads, so the option is mostly noise (see §6 on quota).
- Ad callbacks use `requireContext()` after the ad closes (`HomeFragment.kt:288`), which crashes if the fragment was detached.

**Scheduled scan (premium)**
- **It can run at the same time as a manual scan and overwrite the result the user is reviewing.** That resets their selection mid-cleanup (`AutoScanWorker.kt:36-38`). Skip the run if `scanController.isRunning`, and don't replace a result that has an active selection (or store it separately).
- On Android 11+ it requires All-files access (`AutoScanWorker.kt:47-48`), unlike the in-app check. Users with media-only access **never get scheduled scans and aren't told**.
- A `CoroutineWorker` is capped at ~10 min, so big scans get cancelled → `Result.retry()`, and the heavy work repeats. Use `setForeground` (a long-running worker) or keep it small with the hash cache (§5).

**Settings**
- `treeUriToPath` (`SettingsFragment.kt:256-269`) mishandles the "Documents" root (`home:`), the Downloads provider (`raw:`/`msf:`) and any non-`externalstorage` authority. Folder scans then silently find nothing. Check the authority and reject unknown ones with a message.
- There's no way to remove a single excluded folder (only "Clear"). The scan folder allows only one, though the model is a Set. Include and exclude can conflict without a warning.
- Tapping the *row* of Auto Cleanup/Archive (not the switch) does nothing (`SettingsFragment.kt:168`). Make the whole row toggle the switch.
- The Rate Us fallback just toasts "Rate Us". Fall back to the `https://play.google.com/store/apps/details?id=` URL.

**Data and storage**
- **The recycle bin and fallback archives live in `Android/data`**, so an **uninstall wipes them** with no warning. Say so in the bin screen and on uninstall-sensitive copy.
- `allowBackup="true"` with the default rules (`AndroidManifest.xml:27`, `res/xml/backup_rules.xml`) backs up `bin_index.json`, `last_scan.json` and DataStore (including `is_premium`). A restore on a new phone creates ghost bin entries and stale results. Exclude those files.
- `last_scan.json` is parsed on the main thread on first use (`ScanResultRepositoryImpl.kt:33`, lazily from UI code) and written non-atomically (line 42). Use `AtomicFile` and load on IO.
- `MainActivity.kt:45` uses `runBlocking` for DataStore on the main thread at every launch.
- The bin entry id `now + binName.hashCode()` (`RecycleBinRepositoryImpl.kt:84`) can collide. Use a counter or UUID-derived long.
- Restore/delete from the bin gives no feedback. `restoreMany` returns a count that's ignored (`RecycleBinViewModel.kt:47-52`). Show a snackbar like "8 restored, 1 failed (folder missing)".

**Release config**
- `optimization.enable = false` for release (`app/build.gradle.kts`), so there's no R8 shrinking and the APK is larger. Enable it with keep rules for AdMob/Glide.
- About 7 new strings (the theme picker) are missing from all 9 translations. The onboarding title is hard-coded "Duplicate / File Finder" (`fragment_onboarding.xml:58,66`) and doesn't match `app_name` ("Duplicate Finder").

## 5. Battery and computation: what to change

1. **Home storage recomputation (your example, confirmed).** Two causes: Home is recreated on each tab tap (§3.1), and the cache lives in a fragment-scoped VM with a 60 s TTL. On top of that, `sizeByCategory` (`FileEnumeratorImpl.kt:116-149`) runs **four full MediaStore queries**, and the `Files` query walks *every* row, including all images/videos/audio it then ignores. Fix:
   - Put a `CategorySizeCache` in `AppContainer` (app-scoped). Invalidate it only on scan complete, delete complete, bin restore, and scan-folder settings change. On API 30+ also compare `MediaStore.getGeneration(context, VOLUME_EXTERNAL)` against the value stored with the cache; if unchanged, skip the query entirely.
   - Do **one** pass over `MediaStore.Files` projecting `MEDIA_TYPE`, and classify from that. That removes 3 of the 4 queries.
   - Compute the **file counts in the same pass** so `estimateCount` (another full sweep on every Start Scan tap, `FileEnumeratorImpl.kt:103-114`) can reuse them.
2. **Cleanup list inflates everything at once.** In `res/layout/fragment_cleanup.xml:61,176` the `RecyclerView` sits inside a `NestedScrollView`, which disables recycling. With 2,000 groups that's 2,000 cards and **6,000 Glide thumbnail loads at once**: memory spikes, jank, battery drain. Make the RecyclerView the scrolling root and put the chips/stats in a header (`ConcatAdapter`).
3. **Hash cache for fast re-scans.** Store `(path, size, mtime) → quickHash, fullHash, dHash/aHash` (a small JSON/DataStore file or later Room). A second scan then only hashes changed files. That cuts I/O and CPU dramatically and makes scheduled scans cheap.
4. **Scheduled scans:** add `setRequiresCharging(true)` (and optionally `setRequiresDeviceIdle(true)`) in `AutoScanScheduler.kt:39`. The job should never fight the user for battery.
5. **Ads:** `MobileAds.initialize` and a rewarded-ad preload run on **every launch for every user** (`App.kt:31`), including premium users and people who never hit the quota. Initialize lazily the first time the quota sheet is about to show, and never for premium users.
6. **Archive zipping:** turn compression off (§2.8).
7. **Recycle bin bulk actions:** `restoreMany`/`deleteSelectedForever` rewrite the whole JSON index once per file (O(n²)). Batch them like `commitBatch` already does for deletes.
8. **Startup:** remove `runBlocking` (`MainActivity.kt:45`) and the main-thread JSON load (§4). Consider the SplashScreen API while reading the onboarding flag.
9. Minor: Results rebuilds the legend views on every emission, and Home re-runs `animateTo` on every state emission. Only update when values change.

## 6. Design and UX problems (mapped to your two principle docs)

**First run and the "aha moment" (Psych #50, #43, Fogg #49).** Today the path is Get Started → Home → Grant → system dialog → All-files dialog → back → pick a mode on a slider → Start → notification dialog → scan: about 9 steps before any value. Put the permission explanation on onboarding screen 2. After it's granted, **auto-start a quick Exact scan of everything**, so the first thing the user sees is "You have 2.4 GB of duplicates".

**The daily quota blocks the aha moment (Reciprocity #34, Loss aversion #5).** Free users hit a wall *before* seeing results. Consider letting everyone **scan everything** and gating *cleanup volume* or advanced features (Similar modes, Smart Cleanup, scheduling) instead. Seeing "3.1 GB wasted" is the strongest upgrade motivator.

**Home screen: too many choices before the main button (Hick's #11, Progressive disclosure #45, Cognitive load #19).** It has a storage card, a 4-step slider, 5 category chips and a quota caption. Default to one big **"Scan now"** (Exact, all files), and hide mode/categories behind a "Customize scan" row.

**The scan-mode slider is the wrong control (Jakob's #42, Affordance/Signifiers).** A slider suggests a continuous value, yet there are 4 discrete modes, with labels like "Exact Byte by Byte Scan". Use segmented buttons or 2–3 radio cards with plain names: "Identical files", "Similar photos".

**Touch targets are too small (Fitts #41, WCAG).** Group checkbox 24dp (`item_group_card.xml:101-103`), sort-direction 26dp (`fragment_cleanup.xml:173`), category-chip check 20dp, thumbnails 40dp, Premium close 40dp. The minimum is 48dp.

**Honesty and trust (Trust & transparency #40, Negativity bias #29).**
- "Space Freed" is shown when files only moved to the bin (the space is freed 30 days later). Use "12 files moved to Recycle Bin — 1.2 GB will be freed when the bin empties", plus an "Empty now" button.
- "Nothing ever leaves your phone" vs ads (P0 #6).
- "Secure. Fast. Reliable." on onboarding is generic filler. Replace it with a concrete promise: "Nothing is deleted without your review. Deleted files stay recoverable for 30 days."

**Review & Delete shows only totals (Error prevention, User control).** Users confirm without seeing *which* files. Add an expandable list of files to delete. "Will be kept" currently counts every file in every untouched group too, which confuses. Show only the kept copies of the groups being cleaned.

**Two tabs show the same data (Occam's razor, Miller's #20).** Results (donut + totals) and Cleanup (4 stat cards + list) overlap. Merge them: Cleanup gets a compact summary header (donut + "3.1 GB in 240 groups"), and the Results *screen* is shown once after a scan as the celebration and summary, not as a tab. That frees a tab for the Recycle Bin (Recognition over recall #47).

**System status isn't visible (Visibility of system status, Zeigarnik #3).** Home doesn't show "Last scan: 3 days ago · 1.2 GB reclaimable". Add a card with a "Review" button. It's also the natural return trigger (Hook model #1).

**Missing feedback (Feedback principle).** Restore/delete in the bin, "keep one" blocks (a Toast is easy to miss; use a snackbar or shake), and archive failures have no clear message.

**Peak-end (#21, #27).** Confetti plays both on Results *and* on Delete complete. Save the big celebration for the *freeing* moment, and end it with a lifetime counter: "You've freed 8.4 GB with Duplicate Finder" (Endowed progress / Goal gradient #4, #6).

**Consistency.** The "All Files" chip uses the Documents icon (`HomeFragment.kt:47`). The group checkbox has no partial state. The app name differs between onboarding and the launcher.

**Premium screen (Anchoring #13, Decoy #14, Scarcity, all used ethically).** It's a feature list and a test button, with no plans, prices, trial or comparison. Show monthly/yearly/lifetime with the yearly plan highlighted, a free-vs-premium table, a "Restore purchase" button, and the user's own numbers ("Smart Cleanup would free 2.1 GB for you now").

## 7. Things to remove or simplify

| Remove / simplify | Why |
|---|---|
| **"Auto Cleanup / Smart Clean" toggle** (`SettingsFragment.kt:53`) | It deletes nothing. It only changes the notification wording and defaults the schedule to weekly. The name promises something it doesn't do (Least astonishment). Fold it into "Schedule Scan". |
| **Results as a bottom tab** | Duplicates Cleanup (§6). Use the tab slot for the Recycle Bin. |
| **One of the similarity modes** (Deep, or merge Similar + Very Similar) | The band-LSH limit means Deep ≈ Very Similar in practice (§4). Fewer choices = Hick's law. Keep "Identical" + "Similar photos" (+ optionally "Look-alike, slower"). |
| **The 4 stat cards on Cleanup** | They repeat Results' numbers and push the list below the fold. One summary line is enough. |
| **The Home "i" (About) button** | Duplicates Settings → About. Use that top-bar slot for a Bin shortcut, or drop it. |
| **Dead code** | `MagicByteSniffer` (unless wired in), `ByteComparator`, `StandardFolders`, `UiText`, `StepConnectorView`, the `DetectedType` enum, `AppError.Local` and the unused `Scan`/`Delete` enum values (`PERMISSION_LOST`, `QUOTA_EXCEEDED`, `CANCELLED`, `FILE_IN_USE`), `ScanProgress.slowedForBattery`, `Formatters.percent/within`, `ImageFeatureExtractor.dHash(path)`, `RecycleBinViewModel.restore/deleteForever` (single-item versions). |
| **Unused strings** | `view_all`, `ad_dialog_title/body`, `ad_claim`, `more_apps`, `privacy_policy`, `terms` (or implement them: Play *requires* a privacy policy link), `pro_feature_toast`, `mode_tag_*`, `cat_other` vs `cat_others` (pick one). |
| **Unused dependencies** | `androidx.swiperefreshlayout`, `navigation-ui-ktx` (manual nav wiring). |
| **Silent archive fallback to the app folder** | Frees no space and is wiped on uninstall (§2.4). |
| **The per-scan daily file quota as the main paywall** | Consider replacing it (§6). |

## 8. Features to add (prioritized)

**High value, fits the app's core promise**
1. **Undo after cleanup:** a snackbar on the Delete-complete screen that restores the whole batch from the bin (Forgiveness, User control). The data is already there; it just needs a batch id per `BinEntry`.
2. **Home "Last scan" card:** reclaimable space, age of the scan, a "Review" button, and a "scan running" chip (Visibility, Zeigarnik, Hook trigger).
3. **Run scan in background** from the Scan screen (§3.4).
4. **Archive manifest + Archives screen with restore** (§2.3).
5. **Fast re-scan via the hash cache** (§5.3), marketed as "Quick rescan: only new files".
6. **Honest bin UX:** total bin size, "Free 1.2 GB now" (empty the bin), and a retention setting (7/14/30 days).
7. **Ignore list:** "Never show this group/folder again". File ids are already stable (FNV path hash, `FileEnumeratorImpl.kt:94`), so this is cheap.
8. **Lifetime "space saved" counter** on Home and the completion screen (Goal gradient, Endowed progress, Peak-end).

**Medium**
9. **Selection rules** under one "Auto-select" menu: keep oldest / newest / highest resolution / copy in folder X (progressive disclosure for power users).
10. **Side-by-side compare** in Preview for similar photos (zoom, resolution, date, folder), so users can verify before deleting.
11. **Show the full path** of each copy in Preview (today only the parent folder name).
12. **Smart reminders (ethical Hook trigger):** a monthly notification only when the scheduled scan finds more than X MB.
13. **Accessibility pass:** TalkBack descriptions on thumbnails and checkboxes, 48dp targets, contrast check for `text_tertiary`.

**Later / scope expansion (only if it fits the product)**
14. Large-files finder, empty-folder cleaner, WhatsApp/Telegram "sent media" cleaner, old screenshots: common in this category and good for retention, but each is a new screen.
15. Home-screen widget showing reclaimable space.

## 9. Release-blocker checklist
- [ ] Play Billing replaces the premium test toggle (P0 #5)
- [ ] Real AdMob IDs + UMP consent + accurate privacy copy and Data safety form (P0 #6, #8)
- [ ] Privacy policy URL in the app and on the store listing
- [ ] Archive two-phase commit + unique zip entry names (P0 #1, §2.2)
- [ ] Keep-one invariant enforced at the point of deletion (P0 #2)
- [ ] Bin orphan-copy leak fixed (P0 #3)
- [ ] KeeperRanker prefers originals (P0 #4)
- [ ] Controllers reset in the services' `onDestroy` (P0 #7)
- [ ] Backup rules exclude bin index, last scan and the premium flag
- [ ] R8 enabled for release
- [ ] MANAGE_EXTERNAL_STORAGE declaration justified for Play review (a file-management/cleaner app is an allowed use case, but the justification form is required)

## 10. Suggested order of work
1. **Data safety:** P0 #1, #2, #3, #7, then §2.2 and §2.4–2.6.
2. **Correctness:** KeeperRanker, stale selection, quota 0 edge, permission-denied path, scheduled-scan conflicts, `treeUriToPath`.
3. **Performance:** Home cache + single-pass MediaStore, the Cleanup RecyclerView fix, lazy ads, then the hash cache.
4. **Navigation/UX:** Home tab without recreation, empty states, run in background, merge Results into Cleanup + a Bin tab, simplified Home.
5. **Monetization and release:** billing, consent, privacy, R8, backup rules.
6. **Features** from §8 in the order listed.
