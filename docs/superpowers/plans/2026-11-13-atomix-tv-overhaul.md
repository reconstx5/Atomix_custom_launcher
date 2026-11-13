# Atomix TV Launcher Overhaul Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Turn the launcher into its own TV home (apps, inputs, Live TV with in-launcher playback), with Atomix as a separate app tile.

**Architecture:** One Gradle module, reorganised into packages by responsibility (`shell`, `apps`, `prefs`, `livetv.source`, `livetv.player`, `atomix`, `ui.kit`, `data`). Pure-Kotlin logic classes (tested with JUnit/Robolectric) sit under thin Compose screens and two activities (`HomeActivity`, `PlayerActivity`) plus `AtomixActivity` in its own process. Built in five stages; the launcher installs and works after every task.

**Tech Stack:** Kotlin 2.0.20, AGP 8.5.2, compileSdk 34 / minSdk 26, Compose BOM 2024.09.03 + tv-material 1.0.0, Coil 2.7, OkHttp 4.12, kotlinx.serialization 1.7.3, AndroidX Media3 1.4.1 (new), JUnit 4 + Robolectric 4.13 + MockWebServer + coroutines-test.

**Spec:** `docs/superpowers/specs/2026-11-13-atomix-tv-overhaul-design.md` (approved). Paths below are relative to `app/src/main/java/dev/atomix/launcher/` (`M:`) and `app/src/test/java/dev/atomix/launcher/` (`T:`) unless absolute.

## Global Constraints

- Device: Android TV 11 (API 30), armeabi-v7a, ~1.4 GB RAM, 1920×1080. No `RenderEffect`, no real-time blur.
- Build: `cmd //c 'D:\atomix-launcher\scripts\gradle.cmd' <tasks>` from bash; check `BUILD SUCCESSFUL` and the APK timestamp. Tests: `:app:testDebugUnitTest`. Install: `:app:installRelease` is not used — build `:app:assembleRelease` and `adb -s 192.168.8.67:5555 install -r app/build/outputs/apk/release/app-release.apk` (owner's signing via keystore.properties; never read its passwords).
- TV helpers: `. D:\TV\tvlib.ps1` then `Key`, `Save-TvShot`, `Get-TvFocus`; check the screen before pressing OK.
- Keep the sign-in (`SessionStore` server URL, token, profile), the Home-button override service, and the dock order (migrated). No server changes.
- Atomix token is a credential: never log or print it.
- Safe margins 116 px left/right, 60 px top/bottom; artwork bleeds to edges. All px values are at 1920×1080 via the existing `px()` / `LocalUnit` scaling in `ui/theme/AtomixTheme.kt`.
- Type: spotlight title 80/88 SemiBold, row header 30–40, card title 28, meta 24–30, floor 22. Text #F1F1F1 primary, #B4B8BF secondary; never grey text on glass. LIVE badge #C62F3A with white text. Orbit palette (Deep Space #05050C, Ice accent), Sora font.
- Focus: 6 px white ring + scale 1.08 (cards) / 1.1 (app tiles), 150 ms in / 100 ms out; spotlight follows focus after 300 ms, backdrop crossfade 250 ms.
- Reduced effects (manual toggle, default off, never from the low-RAM flag): panels solid #141726 + 1 px edge, no blurred copy, no crossfade, no shadows, scale instant; layout and functions unchanged.
- Radio channels never appear in the launcher, but radio favourites must be preserved on every favourites write.
- No pause/rewind/record control unless supported (`rewind == true` and live window ≥ 60 s). No essential action behind long-press only.
- Copy strings fixed by the spec: "Pin your channels", "Choose channels", "No programme information", "Can't reach the TV server", "Try again", "Tuning…", "Next channel", "Back to Home", "No channel 123" (number substituted), "Manage sources in Atomix", "Ask an admin to manage Live TV sources", "Pin a channel", "Add an app", "On now".
- Commits on `main`, message ends with `Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>`.

## Review Focus

1. **A programme that has ended while Home sits idle** (now.end < clock, no refresh yet) — the card must not show negative minutes or a full bar; it shows the `next` programme if it has started, else "No programme information". Test in Task 5.
2. **Saving channel order while some radio favourites exist** — the PUT body must contain every radio favourite after the TV ones, in their prior order. Test in Task 5.
3. **Server stops mid-playback** (ping returns `{ok:false}` or network error) — the player retries per the retry policy and then shows the error card; never a frozen picture with no message. Test in Task 9 (`PlayerSession`).
4. **A pinned app is uninstalled, or a pinned channel disappears from the server** — it's hidden from Home, not forgotten, and edit mode positions count only visible items. Tests in Tasks 1 and 5.
5. **Returning to Home when the remembered item no longer exists** (unpinned or uninstalled while away) — focus lands on the nearest item in the same row (same index, clamped), never nowhere. Test in Task 7.

---

## Stage 1 — Foundations

### Task 1: Launcher preferences and dock migration

**Files:**
- Create: `M:prefs/LauncherPrefs.kt`, `M:prefs/FavouriteApps.kt`
- Delete: `M:apps/DockStore.kt` (logic moves to `FavouriteApps`), `T:apps/DockStoreTest.kt` (cases move)
- Modify: `M:AppGraph.kt` (expose `prefs`, `favouriteApps`), `M:ui/apps/AppsScreenState.kt` (use `FavouriteApps`)
- Test: `T:prefs/FavouriteAppsTest.kt`, `T:prefs/LauncherPrefsTest.kt`

**Interfaces:**
- Produces:
  - `const val ATOMIX_APP_KEY = "dev.atomix.launcher"` (in `FavouriteApps.kt`) — the favourites key for the Atomix tile.
  - `class LauncherPrefs(prefs: SharedPreferences, legacyDock: () -> List<String>?)` with `var favouriteApps: List<String>` (first read migrates: legacy dock ?: `DEFAULT_DOCK`, with `ATOMIX_APP_KEY` inserted at index 0 if absent; then persisted), `var reducedEffects: Boolean` (default false), `fun focusKey(screen: String): String?`, `fun setFocusKey(screen: String, key: String?)`. Backed by plain `SharedPreferences` named `"launcher_prefs"` (`AppGraph` passes `context.getSharedPreferences("launcher_prefs", MODE_PRIVATE)` and `{ store.dock }`).
  - `class FavouriteApps(prefs: LauncherPrefs, apps: () -> List<AppEntry>)` with `fun visible(): List<AppEntry>`, `fun contains(pkg: String): Boolean`, `fun add(pkg: String)`, `fun remove(pkg: String)`, `fun move(pkg: String, toVisibleIndex: Int)`, `fun setVisibleOrder(pkgs: List<String>)` (reorders the visible ones to `pkgs`, drops visible ones not in `pkgs`, keeps hidden ones in their slots). `DEFAULT_DOCK` moves here unchanged.

- [ ] **Step 1: Write the failing tests** (`LauncherPrefsTest` uses Robolectric `RuntimeEnvironment.getApplication().getSharedPreferences(...)`)

```kotlin
@Test fun migratesLegacyDock_withAtomixFirst() {
    val p = LauncherPrefs(prefs(), legacyDock = { listOf("a", "b") })
    assertEquals(listOf(ATOMIX_APP_KEY, "a", "b"), p.favouriteApps)
}
@Test fun noLegacyDock_usesDefaultsWithAtomixFirst() {
    assertEquals(listOf(ATOMIX_APP_KEY) + DEFAULT_DOCK, LauncherPrefs(prefs(), { null }).favouriteApps)
}
@Test fun legacyAlreadyHasAtomix_keepsItsPosition() {
    assertEquals(listOf("a", ATOMIX_APP_KEY), LauncherPrefs(prefs(), { listOf("a", ATOMIX_APP_KEY) }).favouriteApps)
}
@Test fun migrationRunsOnce_laterEditsSurviveNewInstance() {
    val sp = prefs()
    LauncherPrefs(sp, { listOf("a") }).favouriteApps = listOf("z")
    assertEquals(listOf("z"), LauncherPrefs(sp, { listOf("a") }).favouriteApps)
}
@Test fun reducedEffects_defaultsOff_andPersists()
@Test fun focusKey_roundTripsPerScreen()
```

```kotlin
// FavouriteAppsTest — apps(): entries for "a","b","c" installed; "gone" not installed
@Test fun visible_hidesUninstalled_butKeepsThemSaved() {
    prefs.favouriteApps = listOf("a", "gone", "b")
    assertEquals(listOf("a", "b"), fav.visible().map { it.packageName })
    assertEquals(listOf("a", "gone", "b"), prefs.favouriteApps)
}
@Test fun move_usesVisibleIndex_skippingHidden() {
    prefs.favouriteApps = listOf("a", "gone", "b", "c")
    fav.move("c", 0)
    assertEquals(listOf("c", "a", "b"), fav.visible().map { it.packageName })
}
@Test fun add_appendsOnce(); @Test fun remove_dropsIt()
@Test fun setVisibleOrder_keepsHiddenInPlace() {
    prefs.favouriteApps = listOf("a", "gone", "b", "c")
    fav.setVisibleOrder(listOf("c", "a"))
    assertEquals(listOf("c", "gone", "a"), prefs.favouriteApps)
}
```

- [ ] **Step 2: Run to verify they fail** — `:app:testDebugUnitTest --tests "*prefs*"` → compile errors (classes missing).
- [ ] **Step 3: Implement** `LauncherPrefs` and `FavouriteApps`; switch `AppGraph`, `AppsScreenState`, `HomeActivity` from `dockStore` to `favouriteApps` (the old home screen keeps working with `visible()`); delete `DockStore` and its test.
- [ ] **Step 4: Run all unit tests** — `:app:testDebugUnitTest` → BUILD SUCCESSFUL.
- [ ] **Step 5: Commit** — `feat: launcher prefs with dock migration`.

### Task 2: App catalogue — cached, off the main thread, Atomix tile included

**Files:**
- Modify: `M:apps/InstalledApps.kt` (include the Atomix entry, exclude `HomeActivity`)
- Create: `M:apps/AppCatalog.kt`
- Modify: `M:AppGraph.kt` (`val apps = AppCatalog(installedApps::list, scope)`; register a `BroadcastReceiver` for `ACTION_PACKAGE_ADDED/REMOVED/CHANGED` with data scheme `package` that calls `apps.invalidate()`)
- Test: `T:apps/InstalledAppsTest.kt` (extend), `T:apps/AppCatalogTest.kt`

**Interfaces:**
- Consumes: `AppEntry`, `SELF_PACKAGE`.
- Produces: `const val ATOMIX_ACTIVITY = "dev.atomix.launcher.atomix.AtomixActivity"`; `class AppCatalog(load: () -> List<AppEntry>, scope: CoroutineScope, io: CoroutineDispatcher = Dispatchers.IO)` with `val apps: StateFlow<List<AppEntry>>` (empty until first load), `fun invalidate()` (reloads on `io`), `fun byPackage(pkg: String): AppEntry?`. `FavouriteApps` from now on takes `{ graph.apps.apps.value }`.

- [ ] **Step 1: Failing tests**

```kotlin
// InstalledAppsTest (Robolectric ShadowPackageManager): register a LEANBACK_LAUNCHER activity for
// SELF_PACKAGE/ATOMIX_ACTIVITY and one for SELF_PACKAGE/dev.atomix.launcher.HomeActivity
@Test fun includesAtomixTile_excludesLauncherItself() {
    val list = InstalledApps(pm).list()
    assertEquals(listOf(ATOMIX_ACTIVITY), list.filter { it.packageName == SELF_PACKAGE }.map { it.launch.component!!.className })
}
// AppCatalogTest with StandardTestDispatcher
@Test fun loadsOnceOffMainThread_thenServesCache() { /* load counter == 1 after two reads */ }
@Test fun invalidate_reloads() { /* counter == 2, new list visible */ }
@Test fun loadFailure_keepsPreviousList() { /* load throws on second call → apps.value unchanged */ }
```

- [ ] **Step 2: Run** `--tests "*apps*"` → FAIL.
- [ ] **Step 3: Implement.** In `InstalledApps.list()`, skip `SELF_PACKAGE` unless `ai.name == ATOMIX_ACTIVITY`.
- [ ] **Step 4: Run all unit tests** → PASS.
- [ ] **Step 5: Commit** — `feat: cached app catalogue with Atomix tile`.

### Task 3: Atomix as its own app

**Files:**
- Create: `M:atomix/AtomixActivity.kt` (moved from `M:web/WebScreenActivity.kt`; `web/AtomixWebClient.kt` and `web/WebBack.kt` stay), `M:atomix/AtomixIntents.kt`, `app/src/main/res/drawable-xhdpi/banner_launcher.png` (launcher's own "Atomix TV" banner; existing `banner.png` stays Atomix's)
- Delete: `M:web/WebScreenActivity.kt`
- Modify: `app/src/main/AndroidManifest.xml`, `M:HomeActivity.kt` (drop `WEB_ACTIVITY`/`openWeb`), `app/src/main/res/values/strings.xml`
- Test: `T:atomix/AtomixIntentsTest.kt`

**Interfaces:**
- Produces: `fun atomixIntent(context: Context, route: String? = null): Intent` (component `ATOMIX_ACTIVITY`, flags `NEW_TASK`, extra `EXTRA_ROUTE` only when `route` starts with `"#/"`); `const val ROUTE_LIVE_SETTINGS = "#/settings/live"`.

Manifest: `AtomixActivity` exported, `android:process=":atomix"`, `android:taskAffinity="dev.atomix.launcher.atomix"`, `android:label="Atomix"`, `android:banner="@drawable/banner"`, `launchMode="singleTask"`, intent-filter `MAIN` + `LEANBACK_LAUNCHER`, not `excludeFromRecents`. `HomeActivity` gets `android:label="Atomix TV"` and `android:banner="@drawable/banner_launcher"`. In `AtomixActivity`, read the session with `SessionStore(this)` directly (not `AppGraph`); remove the "destroy so Home never holds one" comment; keep `KEEP_SCREEN_ON` only while a `<video>` custom view is showing (fixes deferred minor "KEEP_SCREEN_ON on every page"). If `EXTRA_ROUTE` arrives via `onNewIntent`, load it.

- [ ] **Step 1: Failing test** — `atomixIntent(ctx).component!!.className == ATOMIX_ACTIVITY`; `atomixIntent(ctx, ROUTE_LIVE_SETTINGS).getStringExtra(EXTRA_ROUTE) == "#/settings/live"`; `atomixIntent(ctx, "https://x").hasExtra(EXTRA_ROUTE) == false`.
- [ ] **Step 2: Run** `--tests "*AtomixIntents*"` → FAIL.
- [ ] **Step 3: Implement** the move, manifest and intents.
- [ ] **Step 4: Run all unit tests** → PASS; `:app:assembleRelease` → BUILD SUCCESSFUL.
- [ ] **Step 5: Verify on the TV** — install; in `adb shell ps -A | grep atomix` after opening the Atomix tile from Apps, two processes exist (`dev.atomix.launcher` and `dev.atomix.launcher:atomix`); Home → Atomix tile resumes the same page; the stock Google TV app list shows both "Atomix" and "Atomix TV".
- [ ] **Step 6: Commit** — `feat: Atomix opens as its own app`.

## Stage 2 — Live TV data

### Task 4: Live TV API client

**Files:**
- Create: `M:data/AtomixHttp.kt` (shared `get/post/put/delete` + 401 → `AuthExpiredException`, extracted from `AtomixApi.call`), `M:livetv/source/LiveModels.kt`, `M:livetv/source/LiveApi.kt`
- Modify: `M:data/AtomixApi.kt` (use `AtomixHttp`), `M:AppGraph.kt` (`fun live(): LiveClient?`)
- Test: `T:livetv/source/LiveApiTest.kt`, `app/src/test/resources/live-channels.json`, `live-status.json`

**Interfaces:**
- Produces (in `LiveModels.kt`, `@Serializable`, `ignoreUnknownKeys`):
  - `data class Programme(val id: String? = null, val title: String, val start: Long, val end: Long, val subtitle: String? = null, val description: String? = null, val categories: List<String> = emptyList(), val image: String? = null)` — `start`/`end` epoch ms.
  - `data class Channel(val id: String, val number: Int? = null, val name: String, val logo: String? = null, val radio: Boolean = false, val drm: Boolean = false, val favourite: Boolean = false, val now: Programme? = null, val next: Programme? = null)`
  - `data class LiveSource(val id: String, val name: String, val channels: Int = 0, val lastOk: Long? = null, val error: String? = null, val warning: String? = null, val lastRefresh: Long? = null)`; `data class LiveStatus(val enabled: Boolean, val sources: List<LiveSource>, val admin: Boolean)` where `admin` = any source JSON object has the key `lastRefresh` or `error`.
  - `sealed interface PlayReply { val sessionId: String; val channel: Channel?; val programme: Programme?; val next: Programme?; val pingPath: String; val stopPath: String }` with `Dash(manifest: String, rewind: Boolean)` (ping/stop under `/api/live/tuner/{sid}/`), `Stream(url: String, delivery: String)` (under `/api/playback/{sid}/`), `Drm(manifest: String, licence: String, keySystem: String)` (under `/api/live/drm/{sid}/`).
  - `data class PlayCaps(val video: List<String>, val audio: List<String>, val containers: List<String>, val hls: Boolean, val hdr: Boolean = false, val mse: Boolean = false)`.
- Produces (in `LiveApi.kt`): `interface LiveClient { suspend fun status(): LiveStatus; suspend fun channels(): List<Channel>; suspend fun channelsJson(): String; suspend fun addFavourite(id: String); suspend fun removeFavourite(id: String); suspend fun setFavouriteOrder(ids: List<String>): List<String>; suspend fun play(id: String, caps: PlayCaps, replaces: String?): PlayReply; suspend fun ping(reply: PlayReply): Boolean; suspend fun stop(reply: PlayReply); suspend fun refreshSource(id: String); fun url(path: String?): HttpUrl? }` and `class LiveApi(http: AtomixHttp) : LiveClient`. Channel ids are URL-encoded in paths. `ping` returns false on `{ok:false}`; throws on network error.

- [ ] **Step 1: Failing tests** (MockWebServer)

```kotlin
@Test fun channels_parsesMissingNowNextLogo() // fixture has one channel with no now/next/logo/number
@Test fun status_detectsAdminFromFields()      // admin fixture → admin=true; stripped fixture → false
@Test fun play_dashReply_hasTunerPaths() { assertEquals("/api/live/tuner/s1/ping", (reply as PlayReply.Dash).pingPath) }
@Test fun play_streamReply_hasPlaybackPaths(); @Test fun play_drmReply_hasDrmPaths()
@Test fun play_sendsCapsAndReplaces() // request body JSON has caps.video and replaces="old"
@Test fun setFavouriteOrder_putsChannelIds() // method PUT, body {"channelIds":["a","b"]}
@Test fun ping_okFalse_returnsFalse()
@Test fun unauthorised_throwsAuthExpired()
@Test fun channelIdIsEncodedInPath() // id "a/b" → path /api/live/favourites/a%2Fb
```

- [ ] **Step 2: Run** `--tests "*LiveApi*"` → FAIL.
- [ ] **Step 3: Implement**; existing `AtomixApiTest` must still pass after the `AtomixHttp` extraction.
- [ ] **Step 4: Run all unit tests** → PASS.
- [ ] **Step 5: Commit** — `feat: Live TV API client`.

### Task 5: Live TV repository (pins, cache, programme progress)

**Files:**
- Create: `M:livetv/source/LiveRepository.kt`, `M:livetv/source/ProgrammeClock.kt`
- Modify: `M:data/SessionStore.kt` (`var cachedChannelsJson: String?`, cleared by `clearAuth()`; drop `cachedHomeJson` once Task 7 removes the old Home), `M:AppGraph.kt` (`val live: LiveRepository`)
- Test: `T:livetv/source/LiveRepositoryTest.kt`, `T:livetv/source/ProgrammeClockTest.kt`

**Interfaces:**
- Consumes: `LiveClient`, `Channel`, `Programme`, `SessionStore`, `ServerLocator`.
- Produces:
  - `data class OnAir(val programme: Programme?, val minutesLeft: Int?, val progress: Float?, val upNext: Programme?)` and `fun onAir(channel: Channel, nowMs: Long): OnAir` in `ProgrammeClock.kt` — current = `now` if `start ≤ nowMs < end`, else `next` if `start ≤ nowMs < end`, else null (then minutesLeft/progress null); minutesLeft = ceil((end − nowMs)/60 000); progress in 0..1.
  - `sealed interface LiveState { data object Loading; data object NotConfigured; data class Offline(val pinned: List<Channel>); data class Ready(val pinned: List<Channel>, val all: List<Channel>) }` — `pinned`/`all` contain TV channels only (radio filtered), `pinned` in server favourite order (the order of `favourite == true` channels as returned by `?favourites=1`), `all` sorted by `number` then `name`.
  - `class LiveRepository(client: () -> LiveClient?, store: SessionStore, relocate: suspend () -> Unit, scope: CoroutineScope)` with `val state: StateFlow<LiveState>`, `suspend fun refresh()`, `suspend fun pin(id: String)`, `suspend fun unpin(id: String)`, `suspend fun saveOrder(tvIds: List<String>)`, `fun channel(id: String): Channel?`. `refresh()` on network failure → `Offline(cached pinned)` and calls `relocate()` once per failure streak; `status().enabled == false` → `NotConfigured`. `saveOrder` sends `tvIds + radioFavouriteIdsInCurrentOrder`. `pin`/`unpin` update state optimistically, revert on failure.

- [ ] **Step 1: Failing tests** (fake `LiveClient`)

```kotlin
@Test fun ready_filtersRadio_keepsFavouriteOrder()
@Test fun disabled_isNotConfigured()
@Test fun networkFailure_showsCachedPinsOffline_andRelocatesOnce() // two failures → relocate called once
@Test fun noCache_networkFailure_isOfflineWithEmptyList()
@Test fun saveOrder_keepsRadioFavouritesAfterTv() { repo.saveOrder(listOf("tv2","tv1")); assertEquals(listOf("tv2","tv1","r1","r2"), fake.lastOrder) }
@Test fun pinnedChannelMissingFromServer_isHidden()
@Test fun pinFailure_revertsState()
// ProgrammeClockTest
@Test fun midProgramme_minutesLeftAndProgress() // start 0, end 60min, now 45min → 15, 0.75f
@Test fun nowEnded_nextStarted_usesNext()
@Test fun nowEnded_noNext_isNoInfo() { assertNull(onAir(ch, late).programme); assertNull(onAir(ch, late).progress) }
@Test fun noGuide_isNoInfo()
```

- [ ] **Step 2: Run** `--tests "*livetv.source*"` → FAIL.
- [ ] **Step 3: Implement.** `relocate` in `AppGraph` = `locator.locate(store.serverUrl, store.lastKnownUrl)?.let { store.serverUrl = it }` (review item I3b).
- [ ] **Step 4: Run all unit tests** → PASS.
- [ ] **Step 5: Commit** — `feat: Live TV repository with offline cache`.

## Stage 3 — New Home

### Task 6: UI kit, effects and backdrop glass

**Files:**
- Create: `M:ui/kit/Effects.kt`, `M:ui/kit/Focus.kt`, `M:ui/kit/Glass.kt`, `M:ui/kit/Backdrop.kt`, `M:ui/kit/Controls.kt` (`IconButton`, `ChipButton`, `HintBar`, `EmptyState`), `M:ui/kit/TopMenu.kt`
- Modify: `M:ui/theme/AtomixTheme.kt` (add `AtomixColors.TextPrimary=#F1F1F1`, `TextSecondary=#B4B8BF`, `Live=#C62F3A`, `SolidPanel=#141726`; type tokens per Global Constraints)
- Test: `T:ui/kit/BackdropTest.kt`, `T:ui/kit/EffectsTest.kt`

**Interfaces:**
- Produces:
  - `data class Effects(val reduced: Boolean)` with `val focusInMs: Int` (150 / 0), `val focusOutMs: Int` (100 / 0), `val crossfadeMs: Int` (250 / 0), `val glass: Boolean` (!reduced), `val shadows: Boolean`; `val LocalEffects = staticCompositionLocalOf { Effects(false) }`.
  - `fun Modifier.tvFocus(focused: Boolean, scale: Float = 1.08f, cornerPx: Float = 16f): Modifier` — ring 6 px white, scale, timings from `LocalEffects`.
  - `object Backdrop { fun blurredCopy(src: Bitmap): Bitmap /* 240×135, stack blur radius 12 */; fun dimmed(src: Bitmap, amount: Float): Bitmap }` — pure, called on `Dispatchers.Default`.
  - `@Composable fun GlassPanel(blurred: ImageBitmap?, modifier: Modifier, shape: Shape, tint: Float = 0.62f, content: @Composable BoxScope.() -> Unit)` — draws `blurred` scaled to the screen and clipped to the panel, then Deep Space at `tint`, 1 px edge white 16 %; with `!LocalEffects.current.glass` or null bitmap: solid `SolidPanel` + 1 px edge.
  - `@Composable fun TopMenu(selected: Tab, onSelect: (Tab) -> Unit, onInputs: () -> Unit, onSettings: () -> Unit, clock: String, blurred: ImageBitmap?, firstFocus: FocusRequester)`; `enum class Tab { Home, LiveTv, Apps }`.

- [ ] **Step 1: Failing tests** — `blurredCopy` of a 1920×1080 bitmap returns 240×135; a uniform-colour input stays uniform (±1 per channel); `dimmed(b, 0.5f)` halves a white pixel to ~#808080; `Effects(true).focusInMs == 0 && !Effects(true).glass`; `Effects(false).crossfadeMs == 250`.
- [ ] **Step 2: Run** `--tests "*ui.kit*"` → FAIL.
- [ ] **Step 3: Implement.** Stack blur on an `IntArray` from `getPixels` (no RenderScript, no `RenderEffect`).
- [ ] **Step 4: Run all unit tests** → PASS.
- [ ] **Step 5: Commit** — `feat: UI kit with reduced effects and backdrop glass`.

### Task 7: Shell and new Home screen

**Files:**
- Create: `M:shell/ShellScreen.kt` (top menu + tab host + Inputs dialog), `M:shell/FocusMemory.kt`, `M:shell/HomeViewModel.kt`, `M:ui/home/Spotlight.kt`, `M:ui/home/ChannelCard.kt`, `M:ui/home/OnNowRow.kt`, `M:ui/home/AppsRow.kt`, `M:ui/home/HomeScreen.kt` (rewritten)
- Delete: `M:ui/home/{ContinueRow,Dock,DockEditMenu,Hero,HeroImage,HomeState,HomeViewModel,Rail,TopBar}.kt`, `M:ui/theme/LongPressOk.kt`, `M:data/Progress.kt`, home-feed parts of `M:data/Models.kt` and `AtomixClient` (`homeJson`, `home`, `Item`, `Row`, `Library`, `HomeResponse`), tests `T:data/{HomeParsingTest,ProgressTest}.kt`, `T:ui/home/HomeStateTest.kt`, `app/src/test/resources/home.json`
- Modify: `M:HomeActivity.kt` (hosts `ShellScreen`; keeps setup flow; `Inputs` uses existing `InputsDialog.kt` restyled with `GlassPanel`)
- Test: `T:shell/FocusMemoryTest.kt`, `T:shell/HomeViewModelTest.kt`

**Interfaces:**
- Consumes: `LiveRepository.state`, `onAir`, `FavouriteApps`, `AppCatalog`, `LauncherPrefs.focusKey`, `ui.kit.*`, `atomixIntent`.
- Produces:
  - `data class FocusTarget(val row: String, val key: String, val index: Int)`; `class FocusMemory(prefs: LauncherPrefs, screen: String)` with `fun remember(t: FocusTarget)`, `fun resolve(rows: Map<String, List<String>>): FocusTarget?` — exact key if present; else same row at `index.coerceAtMost(size − 1)`; else first item of first non-empty row; null if all empty. Row ids: `"onNow"`, `"apps"`.
  - `data class HomeUi(val live: LiveState, val apps: List<AppEntry>, val clock: String)`; `class HomeViewModel(repo: LiveRepository, favourites: FavouriteApps, catalog: AppCatalog, scope: CoroutineScope, now: () -> Long)` with `val ui: StateFlow<HomeUi>`, `fun onVisible()` (refresh + 60 s refresh loop while visible), `fun onHidden()`.
  - `HomeActivity` constants for later tasks: `const val REQ_PLAY = 1`; launching a card calls `playChannel(id, source = ZapSource.Pinned)` (Task 9 provides `PlayerActivity.intent`; until then the card opens nothing and shows a toast "Playback arrives in the next update" — removed in Task 9).

Layout per spec §5.3 and Global Constraints: spotlight (eyebrow 24 px +0.16 em uppercase, title 80/88 max 2 lines ≤ 1000 px, meta 30 px, one-line description; backdrop = programme `image` → channel logo on plate → Deep Space) bottom-anchored above the On now header; "On now" header 30 px with **✎ Edit** chip (inactive until Task 11); `ChannelCard` 480×270 radius 16, logo plate (colour from logo's dominant colour darkened to ≥ 7:1 vs #F1F1F1, else hashed from name), number 22 px top-right, title 28 px, meta 24 px "8:00–9:00 · 32 min left", progress 4 px (6 px focused) Ice on 24 % white, LIVE badge on focused card only, "Next: …" when focused, "No programme information" and no bar when `onAir.programme == null`; apps row icon 56 px + name 26 px pills, ends with **All apps** (switches to Apps tab) and **✎ Edit**; the apps row peeks ~50 px at the bottom and the column scrolls ~390 px up when focus enters it, backdrop swapped for the `dimmed` copy. Empty/offline states per spec §5.8 (no pins → "Pin your channels" + **Choose channels** → Live TV tab; NotConfigured → one line + **Open Settings**; Offline → dimmed cached cards, OK disabled, "Can't reach the TV server" + **Try again · Settings**). Live TV and Apps tabs show the existing All apps grid and a placeholder list until Task 10. Focus/launch: before `startActivity` for an app, `FocusMemory.remember`; in `onResume`, request focus on `resolve(...)`.

- [ ] **Step 1: Failing tests**

```kotlin
// FocusMemoryTest
@Test fun exactKey_restored()
@Test fun missingKey_fallsBackToSameIndexClamped() { mem.remember(FocusTarget("onNow","c9",5)); assertEquals(FocusTarget("onNow","c3",2), mem.resolve(mapOf("onNow" to listOf("c1","c2","c3")))) }
@Test fun emptyRow_fallsToFirstNonEmptyRow()
@Test fun survivesNewInstance() // backed by LauncherPrefs
// HomeViewModelTest (fake LiveClient via LiveRepository, virtual time)
@Test fun refreshesEveryMinuteWhileVisible_stopsWhenHidden()
@Test fun appsRow_isVisibleFavourites_inOrder()
```

- [ ] **Step 2: Run** `--tests "*shell*"` → FAIL.
- [ ] **Step 3: Implement** the screens and delete the old Home files.
- [ ] **Step 4: Run all unit tests** → PASS; `:app:assembleRelease` → BUILD SUCCESSFUL.
- [ ] **Step 5: Verify on the TV** — install; press Home; `Save-TvShot home-v2.png` and check: top menu, spotlight, three cards + peek, apps row peeking. With no pins (owner has 0 today): "Pin your channels" shows. Open an app from the row, press Home: focus returns to it (`Get-TvFocus` / screenshot). `adb shell dumpsys gfxinfo dev.atomix.launcher reset`, move along rows 20 times, then `dumpsys gfxinfo` → janky frames ≤ 10 %. `dumpsys meminfo dev.atomix.launcher` TOTAL PSS ≤ 150 MB.
- [ ] **Step 6: Commit** — `feat: new Home with Live TV cards and favourite apps`.

## Stage 4 — Live TV playback

### Task 8: Player logic (pure)

**Files:**
- Create: `M:livetv/player/ZapQueue.kt`, `M:livetv/player/Layers.kt`, `M:livetv/player/Controls.kt`, `M:livetv/player/RetryPolicy.kt`, `M:livetv/player/NumberEntry.kt`, `M:livetv/player/ZapSource.kt`
- Test: `T:livetv/player/PlayerLogicTest.kt`

**Interfaces:**
- Produces:
  - `enum class ZapSource { Pinned, All }`; `fun zapList(source: ZapSource, pinned: List<Channel>, all: List<Channel>): List<Channel>` (Pinned → pinned, or all if pinned empty).
  - `class ZapQueue(scope: CoroutineScope, delayMs: Long = 500, tune: (Channel) -> Unit)` with `fun step(list: List<Channel>, from: Channel, delta: Int): Channel` (wraps around; returns the channel to show in the banner immediately) and debounced `tune` of the last stepped channel.
  - `enum class Layer { NumberEntry, ChannelList, Controls, Banner }`; `class Layers` with `fun show(l: Layer)`, `fun hide(l: Layer)`, `fun top(): Layer?`, `fun back(): Boolean` (closes the top layer in the order NumberEntry → ChannelList → Controls → Banner; false when nothing open = leave playback).
  - `fun controlsSupported(reply: PlayReply, liveWindowMs: Long?): Boolean` = `reply is PlayReply.Dash && reply.rewind && (liveWindowMs ?: 0) >= 60_000`.
  - `class RetryPolicy(val delaysMs: List<Long> = listOf(1_000, 3_000, 8_000), val giveUpMs: Long = 20_000)` with `fun nextDelay(attempt: Int, elapsedMs: Long): Long?` (null = show error card).
  - `class NumberEntry(scope: CoroutineScope, commitMs: Long = 1_500, onCommit: (Int) -> Unit)` with `fun digit(d: Int)`, `val typed: StateFlow<String>`; max 4 digits. `fun findByNumber(list: List<Channel>, n: Int): Channel?`.

- [ ] **Step 1: Failing tests** (virtual time) — zap: three presses within 500 ms tune once, to the third channel; wrap from last to first; `zapList(Pinned, empty, all) == all`; layers: back order and false when empty; controls: `Dash(rewind=true)` with 59 999 ms → false, 60 000 → true, `Stream` → false; retry: delays 1 000, 3 000, 8 000 then null, and null once elapsed ≥ 20 000; number entry commits "12" 1 500 ms after the last digit, a fifth digit is ignored, `findByNumber` missing → null.
- [ ] **Step 2: Run** `--tests "*livetv.player*"` → FAIL.
- [ ] **Step 3: Implement.**
- [ ] **Step 4: Run** → PASS.
- [ ] **Step 5: Commit** — `feat: live player logic`.

### Task 9: PlayerActivity with Media3

**Files:**
- Modify: `gradle/libs.versions.toml` (`media3 = "1.4.1"`; libraries `media3-exoplayer`, `media3-exoplayer-dash`, `media3-exoplayer-hls`, `media3-datasource-okhttp`), `app/build.gradle.kts`, `app/src/main/AndroidManifest.xml` (`.livetv.player.PlayerActivity`, not exported, `configChanges` as `AtomixActivity`, theme fullscreen black)
- Create: `M:livetv/player/PlayerSession.kt`, `M:livetv/player/PlayerActivity.kt`, `M:livetv/player/PlayerOverlay.kt` (banner, controls, quick channel list, number entry, loading/error states), `M:livetv/player/MediaFactory.kt`
- Modify: `M:HomeActivity.kt` (`playChannel` launches `PlayerActivity.intent(ctx, id, source)` for result; on result, stay on the tab playback started from and focus the returned channel id via that tab's `FocusMemory` — falling back to the launching item when the returned channel isn't in that list; remove the Task 7 toast)
- Test: `T:livetv/player/PlayerSessionTest.kt`

**Interfaces:**
- Consumes: `LiveClient.play/ping/stop`, `PlayReply`, `RetryPolicy`, `ZapQueue`, `Layers`, `controlsSupported`, `NumberEntry`, `zapList`, `LiveRepository.state`.
- Produces:
  - `class PlayerSession(client: LiveClient, caps: PlayCaps, scope: CoroutineScope, pingEveryMs: Long = 30_000, retry: RetryPolicy = RetryPolicy())` with `val state: StateFlow<SessionState>` (`Tuning(channel)`, `Playing(channel, reply)`, `Failed(channel, message)`), `fun tune(channel: Channel)` (stops/replaces the previous session via `replaces`), `fun onPlayerError()` (schedules retry per policy), `fun close()` (stops the current session). Ping `false` or a thrown error counts as a player error.
  - `PlayerActivity.intent(context: Context, channelId: String, source: ZapSource): Intent`; result extra `EXTRA_CHANNEL_ID` = the last channel tuned.
  - Caps sent: video `["h264","hevc"]`, audio `["aac","mp3","ac3"]`, containers `["mp4","ts"]`, hls true.

Behaviour per spec §5.6: one `ExoPlayer` and one `SurfaceView` for the activity's life; `MediaFactory` builds `DashMediaSource` (Dash/Drm, with Widevine `DefaultDrmSessionManager` + `HttpMediaDrmCallback(licence)` when `MediaDrm.isCryptoSchemeSupported(WIDEVINE_UUID)`, else `Failed("This channel can't play on this TV")`), `HlsMediaSource` for `Stream(delivery="hls")`, progressive otherwise; `OkHttpDataSource` adds `Authorization: Bearer <token>`. `LiveConfiguration` default; on `BEHIND_LIVE_WINDOW` → `seekToDefaultPosition(); prepare()`. Banner (bottom glass, 5 s auto-hide after last key) shows number, logo, name, clock, programme, start–end progress, next. Keys exactly as spec §5.6 *Keys*; LIVE chip / "−mm:ss · Jump to live" from `currentLiveOffset`; Fast-forward never past live. Error card: message + **Try again · Next channel · Back to Home**; zapping still works. `FLAG_KEEP_SCREEN_ON` only while `Playing`; audio focus via `AudioAttributes(USAGE_MEDIA, CONTENT_TYPE_MOVIE)` with `handleAudioFocus = true`. `onStop` → `session.close()` and `finish()` (Home pressed mid-playback ends it).

- [ ] **Step 1: Failing tests** (fake `LiveClient`, virtual time)

```kotlin
@Test fun tune_pingsEvery30s_whilePlaying()
@Test fun tuneAnother_sendsReplacesWithPreviousSessionId_withoutSeparateStop()
@Test fun close_stopsCurrentSession()
@Test fun pingFalse_retriesThenFails() // ping ok=false → Tuning again after 1s; after policy exhausted → Failed
@Test fun playThrows_retriesWithBackoff_thenFailed()
@Test fun drmUnsupported_isFailedWithMessage() // via injected isWidevineSupported=false
```

- [ ] **Step 2: Run** `--tests "*PlayerSession*"` → FAIL.
- [ ] **Step 3: Implement** session, activity, overlay and wiring.
- [ ] **Step 4: Run all unit tests** → PASS; `:app:assembleRelease` → BUILD SUCCESSFUL.
- [ ] **Step 5: Verify on the TV** (pin two TV channels via Atomix's web UI first, or via `curl -X POST …/api/live/favourites/<id>` with the token from the workspace file, never printed): OK on a card → picture within ~5 s; Up/Down changes channel with banner first; ◀ opens the quick list; OK shows banner; on a Dash channel, Pause and ⟲ 30 s work and "Jump to live" returns; Back peels layers then returns Home with that channel's card focused; stop the server stream (`curl …/stop` on the session) → retries then error card with the three buttons; channel number entry tunes; Home key during playback ends it (server `GET /api/live/status` shows no lingering viewer after 2 min). Screenshot each state.
- [ ] **Step 6: Commit** — `feat: in-launcher Live TV playback`.

## Stage 5 — Customisation, settings, finish

### Task 10: Live TV and Apps tabs with pin buttons

**Files:**
- Create: `M:ui/livetv/LiveTvTab.kt`, `M:ui/apps/AppsTab.kt` (replaces `M:ui/apps/AppsScreen.kt`)
- Modify: `M:ui/apps/AppsScreenState.kt` → `AppsTabState`, `M:shell/ShellScreen.kt`
- Test: `T:ui/apps/AppsScreenStateTest.kt` (→ `AppsTabStateTest`), `T:ui/livetv/LiveTvTabStateTest.kt`

**Interfaces:**
- Produces: `class LiveTvTabState(repo: LiveRepository, scope: CoroutineScope)` with `fun rows(): List<Channel>` (= `Ready.all`), `fun isPinned(id: String): Boolean`, `fun togglePin(id: String)`; `class AppsTabState(favourites: FavouriteApps, catalog: AppCatalog)` with `fun list()`, `fun isFavourite(pkg)`, `fun toggle(pkg)`.

Live TV tab: vertical list (one row per TV channel: number, logo plate, name, now/next via `onAir`), OK plays with `ZapSource.All`, a visible **☆ Pin / ★ Pinned** button at the row's right end reached with ▶. Apps tab: grid as today, each tile with a visible **☆ Add to Home / ★ On Home** button below it reached with ▼. Focus and list position remembered per tab with `FocusMemory(prefs, "liveTab"/"appsTab")`.

- [ ] **Step 1: Failing tests** — `togglePin` on an unpinned id calls `addFavourite` and `isPinned` becomes true; on a pinned id calls `removeFavourite`; `AppsTabState.toggle` adds then removes from favourites (radio never listed in `rows()`).
- [ ] **Step 2: Run** → FAIL. **Step 3: Implement.** **Step 4: Run all unit tests** → PASS.
- [ ] **Step 5: Verify on the TV** — pin three channels from the Live TV tab; they appear on Home in pin order; add an app from Apps; it appears at the end of the apps row; Atomix's web Live TV shows the same three favourites.
- [ ] **Step 6: Commit** — `feat: pin channels and apps from their tabs`.

### Task 11: Edit mode

**Files:**
- Create: `M:shell/EditSession.kt`, `M:ui/home/EditOverlay.kt` (hint bar, lifted item, "Position n of m")
- Modify: `M:ui/home/OnNowRow.kt`, `M:ui/home/AppsRow.kt`, `M:shell/HomeViewModel.kt`
- Test: `T:shell/EditSessionTest.kt`

**Interfaces:**
- Produces: `class EditSession(initial: List<String>)` with `val order: List<String>`, `val held: String?`, `fun pickUp(key: String)`, `fun move(delta: Int)` (no-op at ends), `fun place()`, `fun remove(key: String)`, `fun cancelMove()` (restores the order from pickUp), `fun position(): Pair<Int, Int>?` (1-based n of m), `val changed: Boolean`. `HomeViewModel.endEdit(row)` → for `onNow`: `repo.saveOrder(order)` and unpins removed ids; for `apps`: `favourites.setVisibleOrder(order)` immediately on every `place`/`remove`.

Keys per spec §5.5: ✎ Edit chip enters; OK picks up/places; ◀/▶ move; ▼ removes (unpin); Back cancels the held move, or leaves edit mode when nothing held. Hint bar text: "◀ ▶ Move · OK Place · ▼ Unpin · Back Cancel" (apps row: "▼ Remove"). Trailing tile "＋ Pin a channel" / "＋ Add an app" opens the Live TV / Apps tab. Long-press OK on a card also enters edit mode (shortcut only).

- [ ] **Step 1: Failing tests** — pickUp "b" in [a,b,c], move(+1) → [a,c,b], place → order [a,c,b] and `held == null`; cancelMove after moving restores [a,b,c]; move at the end is a no-op; position after pickUp "b" is (2,3); remove "a" → [b,c], `changed == true`.
- [ ] **Step 2: Run** → FAIL. **Step 3: Implement.** **Step 4: Run all unit tests** → PASS.
- [ ] **Step 5: Verify on the TV** — reorder channels and apps with the remote only; force-stop (`am force-stop dev.atomix.launcher`), press Home: order kept; reboot the TV (`Wait-TvReboot` pattern from `scripts/become-home.ps1`): order kept; Atomix web shows the new channel order and radio favourites unchanged.
- [ ] **Step 6: Commit** — `feat: edit mode for pinned channels and apps`.

### Task 12: Settings and reduced effects

**Files:**
- Create: `M:ui/settings/SettingsScreen.kt`, `M:ui/settings/SettingsState.kt`
- Modify: `M:shell/ShellScreen.kt` (⚙ opens Settings), `M:HomeActivity.kt` (provide `LocalEffects` from `prefs.reducedEffects`)
- Test: `T:ui/settings/SettingsStateTest.kt`

**Interfaces:**
- Consumes: `LiveClient.status/refreshSource`, `LiveRepository`, `ServerLocator`, `SessionStore`, `LauncherPrefs.reducedEffects`, `atomixIntent(ctx, ROUTE_LIVE_SETTINGS)`, `PlayerActivity.intent`.
- Produces: `class SettingsState(...)` with `val live: StateFlow<LiveSettingsUi>` (`sources`, `admin`, `loading`, `error`), `suspend fun refreshSource(id)`, `fun testChannelId(): String?` (first pinned TV channel, else first TV channel), `suspend fun findServerAgain(): Boolean`, `fun homeOverrideOn(enabledServices: String?): Boolean` (true when the colon-separated list contains `dev.atomix.launcher/dev.atomix.launcher.home.HomeOverrideService`), `var reducedEffects: Boolean`.

Sections per spec §5.7 in a left list / right detail layout: Live TV (sources with channel count, last refresh as "x min ago", error/warning text; **Refresh** per source and **Manage sources in Atomix** only when `admin`, else "Ask an admin to manage Live TV sources"; **Test a channel**), Server (address, reachable yes/no, **Find again**), Profile (name, **Switch profile** → existing setup flow), Display (**Reduced effects** on/off), Home button (status from `Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES`, and the one-line how-to), About (versionName).

- [ ] **Step 1: Failing tests** — admin status → refresh allowed; non-admin → `admin == false`; refresh 403 → `error` set, sources unchanged; `testChannelId` prefers pinned; `homeOverrideOn("a/b:dev.atomix.launcher/dev.atomix.launcher.home.HomeOverrideService") == true`, `homeOverrideOn(null) == false`; `findServerAgain` stores the located URL.
- [ ] **Step 2: Run** → FAIL. **Step 3: Implement.** **Step 4: Run all unit tests** → PASS.
- [ ] **Step 5: Verify on the TV** — each section renders; Refresh works; Manage sources opens Atomix at its Live TV settings; Reduced effects on: panels solid, no crossfade, focus ring still visible, every flow from Tasks 7–11 still works (spot-check play, edit, launch); screenshot both modes.
- [ ] **Step 6: Commit** — `feat: settings with Live TV status and reduced effects`.

### Task 13: Full on-device verification, performance and docs

**Files:**
- Modify: `README.md` (new Home, Atomix tile, Live TV playback, settings, reduced effects; remove old Home/dock instructions), `D:\atomix-launcher\docs\superpowers\specs\2026-11-13-atomix-tv-overhaul-design.md` only if a verified fact contradicts it (note it, don't silently change).
- Create: `D:\TV\reports\launcher-overhaul-verification.md` (each check: pass/fail/untested + screenshot path)

- [ ] **Step 1: Run all unit tests** → BUILD SUCCESSFUL; install the release build.
- [ ] **Step 2: Run the spec §6 checklist on the TV** and record each result: app launch and return (incl. Atomix tile → focus back on it), playback start/exit, channel up/down and quick list, pause/rewind/jump-to-live, unplayable channel → error and retry, server stopped → offline Home with cached pins, no pins, customisation after force-stop and reboot, reduced effects on/off, Home button override still opens the launcher, jank ≤ 10 % warm, Home PSS ≤ 150 MB. Mark Widevine channels and anything else that can't be exercised as untested with the reason.
- [ ] **Step 3: Fix any failure found** via superpowers:systematic-debugging, each with a failing test first where the logic is unit-testable.
- [ ] **Step 4: Commit** — `docs: overhaul README and verification report`.
