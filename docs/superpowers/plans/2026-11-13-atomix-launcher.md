# Atomix Launcher Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** An Android TV home-screen app, "Atomix", whose cinematic hero and Continue watching row come from the owner's Atomix server, with a glass dock of TV apps, Live TV and Inputs, and Atomix's web player embedded full screen.

**Architecture:** Native Kotlin/Compose-for-TV app. `HomeActivity` (HOME + LAUNCHER intent filters) renders cached-then-fresh `/api/home` data and local app/input sources; `WebScreenActivity` hosts a WebView for Atomix's player, item, library and search pages and is destroyed on exit. A small data layer (API client, session store, server locator) is unit-tested on the JVM; UI is verified on the real TV over ADB.

**Tech Stack:** Kotlin 2.0, Jetpack Compose + androidx.tv:tv-material 1.0, Coil 2, OkHttp 4 + kotlinx.serialization, androidx.security-crypto, JUnit4 + OkHttp MockWebServer + Robolectric, Gradle 8.9 wrapper, AGP 8.5, JDK 17.

**Spec:** `D:\atomix-launcher\docs\superpowers\specs\2026-11-13-atomix-launcher-design.md`

## Global Constraints

- Device: Android TV OS 11 (API 30), armeabi-v7a, ~1.4 GB RAM, 1920×1080. ADB `D:\TV\platform-tools\adb.exe -s 192.168.8.67:5555`; helpers in `D:\TV\tvlib.ps1` (`Tv`, `Key`, `Get-TvUi`, `Save-TvShot`).
- `applicationId "dev.atomix.launcher"`, app label `Atomix`, minSdk 26, compileSdk/targetSdk 34, ABIs `armeabi-v7a`, `arm64-v8a`.
- Toolchain only from official sources: Eclipse Temurin JDK 17 (adoptium.net), Android command-line tools (dl.google.com), Gradle (services.gradle.org).
- Atomix server default `http://192.168.8.65:8787`; identify a server by `GET /api/status` → JSON with `"name":"Atomix"`.
- Auth: `Authorization: Bearer <token>` on every API call; the WebView gets cookie `atomix_session=<token>` for the server origin. Token comes from the `atomix_session` Set-Cookie of `POST /api/quickconnect/poll` when `status == "approved"`.
- Profile: `GET /api/profiles`; `POST /api/profiles/{id}/select` (body `{"pin":"…"}` when the profile has a PIN; confirm field name in `D:\nodeflix\src\api\account.js:317`).
- Web routes (hash routes on the server origin): play `#/play/{id}`, item `#/item/{id}`, library `#/library/{id}`, search `#/search`.
- Orbit tokens (from Atomix DESIGN.md): Deep Space `#05050c`, Deep Space Inset `#0b0b16`, Ice `#8fe9ff`, text white / soft / dim, focus ring = 5 px Deep Space gap then 4 px white (outer 9 px), focused card scale 1.08, focused button solid white + ink text scale 1.05 over 180 ms, card corner 18 px, pill buttons, Sora font; type at 1920 width: Display 116, Headline 56, Title 26, Body 22, Label 20, Caption 16 (px, scaled by screenWidth/1920). Safe margin 64 px. No live blur: glass = translucent fills.
- Kids rules apply to Atomix content only (the API filters). The launcher adds no locks.
- Never change the TV irreversibly: every ADB change gets an undo line in `D:\TV\undo.ps1`; the APK package is appended to `D:\TV\installed-by-us.txt`.
- Never read other apps' watch progress; never show anything the Atomix API did not return for the selected profile.

## Review Focus

1. **Atomix unreachable at start or mid-session** (PC off, IP changed, NordVPN blocking LAN): Home shows the bundled backdrop with "Atomix is offline" and a working dock; no crash, no spinner forever. → tests in Task 3 (`ServerLocatorTest.allCandidatesFail_returnsNull`) and Task 6 (`HomeStateTest.networkError_withNoCache_isOffline`).
2. **Token revoked or expired (HTTP 401)** from any call: session cleared and first-run starts at Quick Connect, once — not a loop. → Task 2 `AtomixApiTest.unauthorized_throwsAuthExpired` and Task 6 `HomeStateTest.authExpired_routesToSignIn`.
3. **Thin data:** hero `null`, empty Continue watching, items without `backdrop`/`logo`/`progress`. Hero falls back to first card, then offline-style backdrop; title text replaces missing logo; no progress line when progress is null or watched. → Task 2 `HomeParsingTest.missingOptionalFields_parse`, Task 6 `HomeStateTest.noHero_usesFirstRowItem`, `HomeStateTest.emptyEverything_showsAppsOnly`.
4. **Dock references an app that was uninstalled or disabled**: dropped silently from the dock and not launched. → Task 5 `DockStoreTest.missingPackage_isFiltered`.
5. **Back/Home during WebView load or playback**: WebScreenActivity finishes and destroys its WebView; Home refreshes progress on resume. → Task 7 on-device check: `dumpsys meminfo dev.atomix.launcher` after exit shows no WebView/Chromium heap growth across 3 open/close cycles.

---

### Task 1: Toolchain, project skeleton, installs on the TV as a Home candidate

**Files:**
- Create: `D:\atomix-launcher\settings.gradle.kts`, `build.gradle.kts`, `gradle.properties`, `gradle/libs.versions.toml`, `gradlew`, `gradlew.bat`, `gradle/wrapper/*`
- Create: `app/build.gradle.kts`, `app/src/main/AndroidManifest.xml`, `app/src/main/java/dev/atomix/launcher/HomeActivity.kt`, `app/src/main/res/drawable/banner.png` (320×180 Atomix mark on Deep Space), `app/src/main/res/mipmap-*/ic_launcher*`
- Create: `.gitignore`, `README.md`, `scripts\install.ps1`

**Interfaces:**
- Produces: `HomeActivity` (exported; intent filters MAIN+HOME+DEFAULT and MAIN+LEANBACK_LAUNCHER), `launchMode="singleTask"`, `stateNotNeeded="true"`, `excludeFromRecents="true"`, `android:banner="@drawable/banner"`, `uses-feature android.software.leanback required=false`, `touchscreen required=false`, permission `INTERNET`, `usesCleartextTraffic="true"`. `scripts\install.ps1` builds `assembleDebug` (or `-Release`) and runs `adb install -r`, appending the package to `D:\TV\installed-by-us.txt` once.

- [ ] **Step 1: Install JDK 17** — download the Temurin 17 Windows x64 zip from `https://api.adoptium.net/v3/binary/latest/17/ga/windows/x64/jdk/hotspot/normal/eclipse` into `D:\atomix-launcher-tools\dl\`, extract to `D:\atomix-launcher-tools\jdk17`. Verify: `D:\atomix-launcher-tools\jdk17\bin\java -version` prints `17.`.
- [ ] **Step 2: Install Android SDK** — get the current `commandlinetools-win-*_latest.zip` link from `https://developer.android.com/studio#command-line-tools-only`, extract to `D:\atomix-launcher-tools\android-sdk\cmdline-tools\latest`, then `sdkmanager --licenses` (show licences to the owner and ask before accepting) and `sdkmanager "platform-tools" "platforms;android-34" "build-tools;34.0.0"`. Set `JAVA_HOME` and `ANDROID_HOME` for the session only (no system-wide changes). Verify: `sdkmanager --list_installed` shows the three packages.
- [ ] **Step 3: Gradle wrapper** — download `gradle-8.9-bin.zip` from `https://services.gradle.org/distributions/`, run `gradle wrapper --gradle-version 8.9` in `D:\atomix-launcher`. Verify `gradlew.bat --version` prints Gradle 8.9 and JVM 17.
- [ ] **Step 4: Skeleton** — Compose-enabled app module per Global Constraints; `HomeActivity` shows a full-screen Deep Space `Box` with centred text `Atomix`. `local.properties` points to the SDK (git-ignored).
- [ ] **Step 5: Build** — `gradlew.bat :app:assembleDebug`. Expected: `BUILD SUCCESSFUL`, APK in `app/build/outputs/apk/debug/`.
- [ ] **Step 6: Install and verify Home candidacy** — `scripts\install.ps1`; then `Tv shell cmd package query-activities --brief -a android.intent.action.MAIN -c android.intent.category.HOME`. Expected: lists `dev.atomix.launcher/.HomeActivity`; resolve-activity still returns `com.google.android.tvlauncher/.MainActivity` (nothing taken over yet). Launch with `Tv shell am start -n dev.atomix.launcher/.HomeActivity` and `Save-TvShot`; expected: dark screen with "Atomix".
- [ ] **Step 7: Commit** — `git init`, `git add -A`, `git commit -m "chore: Atomix launcher skeleton and toolchain"` (ask the owner before the first commit).

### Task 2: Atomix API client and models

**Files:**
- Create: `app/src/main/java/dev/atomix/launcher/data/Models.kt`, `data/AtomixApi.kt`, `data/Progress.kt`
- Test: `app/src/test/java/dev/atomix/launcher/data/HomeParsingTest.kt`, `AtomixApiTest.kt`, `ProgressTest.kt`
- Test fixture: `app/src/test/resources/home.json` (captured from the real server in Step 1, artwork paths kept, then trimmed to hero + 2 rows)

**Interfaces:**
- Produces:
  - `@Serializable data class Progress(val position: Double, val duration: Double? = null, val watched: Boolean = false)`
  - `@Serializable data class Item(val id: Long, val kind: String, val title: String, val year: Int? = null, val genres: List<String> = emptyList(), val runtime: Int? = null, val duration: Double? = null, val overview: String? = null, val poster: String? = null, val backdrop: String? = null, val logo: String? = null, val tint: String? = null, val tintWide: String? = null, val progress: Progress? = null, val tagline: String? = null, val season: Int? = null, val episode: Int? = null)` (ignore unknown keys)
  - `@Serializable data class Row(val key: String, val id: String? = null, val title: String, val style: String? = null, val items: List<Item> = emptyList())`
  - `@Serializable data class HomeResponse(val hero: Item? = null, val rows: List<Row> = emptyList())`
  - `@Serializable data class Profile(val id: Long, val name: String, val kids: Boolean = false, val hasPin: Boolean = false, val avatar: String? = null)` (confirm field names against `publicProfile` in `D:\nodeflix\src\api\account.js`)
  - `@Serializable data class Status(val name: String, val serverName: String? = null, val quickConnect: Boolean = false)`
  - `@Serializable data class QuickConnectStart(val code: String, val poll: String, val expiresAt: Long, val ttlMs: Long)`
  - `sealed interface PollResult { object Pending; object Expired; data class Approved(val token: String) }`
  - `class AuthExpiredException : Exception()`
  - `class AtomixApi(baseUrl: HttpUrl, token: () -> String?, client: OkHttpClient)` with `suspend fun status(): Status`, `suspend fun quickConnectStart(): QuickConnectStart`, `suspend fun quickConnectPoll(poll: String): PollResult`, `suspend fun profiles(): List<Profile>`, `suspend fun selectProfile(id: Long, pin: String?)`, `suspend fun home(): HomeResponse`, `fun imageUrl(path: String?): HttpUrl?` (resolves relative `/api/items/...` against baseUrl). Any 401 → `AuthExpiredException`; IO failure → `IOException`.
  - `fun minutesLeft(p: Progress?, itemDuration: Double?): Int?` — null when progress null, watched, or no duration; else `ceil((duration - position)/60)` with duration from progress, then item; null if ≤ 0.
  - `fun progressFraction(p: Progress?, itemDuration: Double?): Float?` — same nulls, else clamp(position/duration, 0, 1).

- [ ] **Step 1: Capture fixture** — with a token from Atomix (owner signs in via Quick Connect: `POST /api/quickconnect`, owner approves in Atomix, poll, read `atomix_session` cookie), `GET /api/home` with Bearer and save to `home.json`. Confirm units of `position`/`duration` are seconds; if not, adjust `minutesLeft` and note it here.
- [ ] **Step 2: Write failing tests**
  - `HomeParsingTest.fixture_parses`: hero non-null, `rows.first { it.key == "continue" }` present when the fixture has one.
  - `HomeParsingTest.missingOptionalFields_parse`: `{"hero":null,"rows":[{"key":"x","title":"X","items":[{"id":1,"kind":"movie","title":"T"}]}]}` parses; item backdrop/logo/progress null.
  - `ProgressTest`: `minutesLeft(Progress(600.0, 3000.0), null) == 40`; `minutesLeft(Progress(600.0), 3000.0) == 40`; `minutesLeft(Progress(10.0, 3000.0, watched = true), null) == null`; `minutesLeft(null, 3000.0) == null`; `progressFraction(Progress(1500.0, 3000.0), null) == 0.5f`.
  - `AtomixApiTest` (MockWebServer): `home_sendsBearer` (request header `Authorization: Bearer abc`); `unauthorized_throwsAuthExpired` (401 → `AuthExpiredException`); `poll_approved_readsCookieToken` (body `{"status":"approved"}` + header `Set-Cookie: atomix_session=tok123; Path=/` → `Approved("tok123")`); `poll_pending` (`{"status":"pending"}` → `Pending`); `imageUrl_resolvesRelative` (`/api/items/5/image/backdrop?v=1` → `http://host:port/api/items/5/image/backdrop?v=1`).
- [ ] **Step 3: Run** `gradlew.bat :app:testDebugUnitTest` — expected FAIL (unresolved references).
- [ ] **Step 4: Implement** Models, AtomixApi (OkHttp `await` via `suspendCancellableCoroutine` or `Dispatchers.IO`), Progress helpers.
- [ ] **Step 5: Run tests** — expected PASS.
- [ ] **Step 6: Commit** `feat: Atomix API client and models`.

### Task 3: Server locator and session store

**Files:**
- Create: `data/ServerLocator.kt`, `data/SessionStore.kt`
- Test: `ServerLocatorTest.kt`, `SessionStoreTest.kt` (Robolectric)

**Interfaces:**
- Consumes: `AtomixApi.status()` semantics (`name == "Atomix"`).
- Produces:
  - `interface ServerProbe { suspend fun isAtomix(base: HttpUrl): Boolean }` and `OkHttpServerProbe(client)` (GET `/api/status`, 800 ms timeout).
  - `class ServerLocator(probe: ServerProbe, subnetHosts: () -> List<String>)` with `suspend fun locate(saved: HttpUrl?, lastKnown: HttpUrl?): HttpUrl?` — order: saved, lastKnown, then every host from `subnetHosts()` on port 8787 probed in parallel (max 32 concurrent), first success wins; null if none.
  - `fun subnetHostsOf(ipv4: String): List<String>` — `x.y.z.1..254` excluding the TV's own address.
  - `class SessionStore(context)` (EncryptedSharedPreferences) with properties `serverUrl: HttpUrl?`, `lastKnownUrl: HttpUrl?`, `token: String?`, `profileId: Long?`, `profileName: String?`, `cachedHomeJson: String?`, `dock: List<String>?`, and `fun clearAuth()` (token, profileId, profileName, cachedHomeJson only).

- [ ] **Step 1: Failing tests** — `ServerLocatorTest.savedWins` (probe true only for saved → saved); `ServerLocatorTest.fallsBackToSubnet` (saved and lastKnown false, `192.168.8.65` true → `http://192.168.8.65:8787/`); `ServerLocatorTest.allCandidatesFail_returnsNull`; `subnetHostsOf("192.168.8.67")` has 253 entries and excludes `.67`; `SessionStoreTest.clearAuth_keepsServerAndDock`.
- [ ] **Step 2: Run** — FAIL.
- [ ] **Step 3: Implement.** The TV's IPv4 for `subnetHosts` comes from `ConnectivityManager.getLinkProperties(activeNetwork)`.
- [ ] **Step 4: Run** — PASS.
- [ ] **Step 5: Commit** `feat: server locator and session store`.

### Task 4: Orbit theme, focus visuals and first-run screens

**Files:**
- Create: `ui/theme/AtomixTheme.kt` (colors, `AtomixType` scaled by `LocalConfiguration.screenWidthDp`, `Modifier.atomixFocusRing(shape)`, `GlassPill`, `GlassButton`), `app/src/main/res/font/sora_regular.ttf`, `sora_semibold.ttf`, `sora_bold.ttf` (copied from `D:\nodeflix\public\fonts\`, OFL licence file copied to `app/src/main/assets/licenses/sora-OFL.txt`)
- Create: `ui/setup/SetupFlow.kt` (`ServerScreen`, `QuickConnectScreen`, `WhoIsWatchingScreen`, `PinPad`), `ui/setup/SetupViewModel.kt`
- Modify: `HomeActivity.kt` (route: no token → setup; token but no profile → Who's watching; else home placeholder)
- Test: `SetupViewModelTest.kt`

**Interfaces:**
- Consumes: `AtomixApi`, `ServerLocator`, `SessionStore`.
- Produces: `@Composable fun AtomixTheme(content)`, `Modifier.atomixFocusRing(shape: Shape)`, `@Composable fun GlassButton(text, primary: Boolean, onClick)`, `@Composable fun SetupFlow(onDone: () -> Unit)`. `SetupViewModel.state: StateFlow<SetupState>` with `SetupState = FindingServer | EnterAddress(error: String?) | QuickConnect(code: String, secondsLeft: Int) | ChooseProfile(profiles: List<Profile>) | EnterPin(profile: Profile, error: String?) | Done`.

- [ ] **Step 1: Failing tests** (`SetupViewModelTest`, fake API): `noServerFound_asksForAddress`; `quickConnect_pollsEvery2s_untilApproved_savesToken`; `quickConnect_expired_restartsWithNewCode`; `singleProfileWithoutPin_selectsAutomatically`; `wrongPin_staysOnPinWithError` (select throws HTTP 403 → `EnterPin(error = "Wrong PIN")`).
- [ ] **Step 2: Run** — FAIL. **Step 3: Implement** ViewModel then screens. Copy: Quick Connect screen shows the code at Display size, "Open Atomix on your phone or computer and enter this code in Settings → Quick Connect." (check the exact menu path in Atomix and use it), countdown, and the server address small. Who's watching uses circular avatars from `Profile.avatar` via `imageUrl`, 1.08 scale on focus.
- [ ] **Step 4: Run tests** — PASS.
- [ ] **Step 5: On-device check** — clear app data (`Tv shell pm clear dev.atomix.launcher`), launch, `Save-TvShot`; expected: Quick Connect code visible (server found automatically). Ask the owner to approve the code in Atomix; then Who's watching appears; pick a profile with the remote (`Key DPAD_CENTER` after checking focus with `Get-TvUi`).
- [ ] **Step 6: Commit** `feat: Orbit theme and first-run setup`.

### Task 5: Local app sources — installed apps, dock, inputs, Live TV

**Files:**
- Create: `apps/InstalledApps.kt`, `apps/DockStore.kt`, `apps/TvInputs.kt`, `apps/Launch.kt`
- Test: `DockStoreTest.kt`, `InstalledAppsTest.kt` (Robolectric)

**Interfaces:**
- Produces:
  - `data class AppEntry(val packageName: String, val label: String, val banner: Drawable?, val icon: Drawable, val launch: Intent)`
  - `class InstalledApps(pm: PackageManager)` with `fun list(): List<AppEntry>` — LEANBACK_LAUNCHER activities first, then LAUNCHER, deduplicated by package, excluding `dev.atomix.launcher`, sorted by label.
  - `val DEFAULT_DOCK = listOf("org.smarttube.stable", "tv.twitch.android.app", "com.amazon.amazonvideo.livingroom", "ar.tvplayer.tv", "com.phlox.tvwebbrowser")` (confirm TV Bro's package with `Tv shell pm list packages | findstr -i tvwebbrowser` and fix here if different).
  - `class DockStore(store: SessionStore, apps: () -> List<AppEntry>)` with `fun dock(): List<AppEntry>` (saved list or DEFAULT_DOCK, filtered to installed), `fun add(pkg)`, `fun remove(pkg)`, `fun move(pkg, toIndex)`.
  - `data class InputEntry(val id: String, val label: String)`; `class TvInputs(context)` with `fun hdmi(): List<InputEntry>` (from `TvInputManager.tvInputList`, type `TYPE_HDMI`, label via `loadLabel`) and `fun intentFor(input): Intent` = `Intent(Intent.ACTION_VIEW, TvContract.buildChannelUriForPassthroughInput(input.id))`.
  - `fun liveTvIntent(pm): Intent?` — launch intent of `com.mediatek.wwtv.tvcenter`, else null.
  - `fun Context.safeStart(intent: Intent): Boolean` — catches `ActivityNotFoundException`, returns false.

- [ ] **Step 1: Failing tests** — `DockStoreTest.defaults_whenNothingSaved`; `DockStoreTest.missingPackage_isFiltered`; `DockStoreTest.move_reordersAndPersists`; `InstalledAppsTest.excludesSelf_andDedupesByPackage` (Robolectric shadow PackageManager with one app in both categories).
- [ ] **Step 2: Run** — FAIL. **Step 3: Implement.** **Step 4: Run** — PASS.
- [ ] **Step 5: On-device check** — a temporary debug screen is not needed; verify via unit tests plus `Tv shell dumpsys tv_input` listing HDMI inputs whose ids match what `hdmi()` returns (log them once with `Log.i("AtomixInputs", …)` and read with `Tv logcat -d -s AtomixInputs`).
- [ ] **Step 6: Commit** `feat: installed apps, dock, inputs`.

### Task 6: Home screen

**Files:**
- Create: `ui/home/HomeViewModel.kt`, `ui/home/HomeState.kt`, `ui/home/HomeScreen.kt`, `ui/home/Rail.kt`, `ui/home/Hero.kt`, `ui/home/ContinueRow.kt`, `ui/home/Dock.kt`, `ui/home/TopBar.kt`, `ui/home/InputsDialog.kt`, `app/src/main/res/drawable-nodpi/offline_backdrop.webp` (original Atomix-branded dark artwork, ≤ 300 KB, 1920×1080)
- Modify: `HomeActivity.kt` (show `HomeScreen`; `onResume` → `viewModel.refresh()`)
- Test: `HomeStateTest.kt`

**Interfaces:**
- Consumes: `AtomixApi.home`, `SessionStore.cachedHomeJson`, `DockStore.dock()`, `TvInputs`, `liveTvIntent`, `minutesLeft`, `progressFraction`, theme components.
- Produces:
  - `data class HomeState(val status: Status, val hero: Item?, val row: Row?, val dock: List<AppEntry>, val profileName: String?)` with `enum class Status { Loading, Online, Offline, SignedOut }`.
  - `fun reduce(cached: HomeResponse?, fresh: Result<HomeResponse>?): Pair<Status, HomeResponse?>` (pure): fresh success → Online+fresh; `AuthExpiredException` → SignedOut; IO error → Online+cached if cached else Offline+null; fresh null (still loading) → Loading+cached.
  - `fun pickRow(rows: List<Row>): Row?` — `continue` if non-empty, else first non-empty row of style `landscape` or `poster`.
  - `fun pickHero(resp: HomeResponse?): Item?` — `resp.hero` else first item of `pickRow`.
  - `HomeViewModel.focusedItem: StateFlow<Item?>` debounced 300 ms from `onCardFocused(item)`.
  - Nav callbacks: `openWeb(route: String)` starts `WebScreenActivity` (Task 7) with extra `route`.

- [ ] **Step 1: Failing tests** (`HomeStateTest`): `networkError_withNoCache_isOffline`; `networkError_withCache_staysOnlineWithCache`; `authExpired_routesToSignIn`; `noHero_usesFirstRowItem`; `emptyEverything_showsAppsOnly` (`pickHero` null, `pickRow` null); `continuePreferred_overOtherRows`; `focusDebounce_emitsLastAfter300ms` (virtual time; three focus changes within 300 ms emit only the last).
- [ ] **Step 2: Run** — FAIL. **Step 3: Implement** state + ViewModel; persist each fresh response to `cachedHomeJson`; `SignedOut` calls `SessionStore.clearAuth()` and shows `SetupFlow` once.
- [ ] **Step 4: Implement UI** per spec layout at 1920 reference: Top bar (atom mark + "Atomix" top-left at 64/48; clock + profile pill top-right, profile pill opens Who's watching). Rail: floating capsule at x 48, y 200, width 88 collapsed (icons) / 200 expanded (icons + labels) when focused; items Home, Apps, Library, Live, Inputs, Search. Hero: backdrop full-bleed via Coil `size(1280, 720)`, crossfade 400 ms, horizontal scrim Deep Space 92%→0% from left to 60% width and vertical scrim 0→95% over bottom 45%; logo image max 640×200 else title in Display; meta line `year · genres(2) · runtime · 4K?`; progress line 360 px wide, 5 px, accent = `tintWide ?: tint ?: Ice` (parse `#rrggbb` or `rgb(r,g,b)`), with "N min left"; 2-line overview; buttons `▶ Resume · N min left` (or `▶ Play`) → `openWeb("#/play/{id}")`, `More info` → `openWeb("#/item/{id}")`. Continue row: heading "Continue watching" (or the row's title), landscape cards 336×189 with 18 px corners, progress bar inset 14/12, focus ring + 1.08; card focus → `onCardFocused`; OK → play. Coil prefetches backdrops of focused±1. Dock: centred glass pill at y ~ 960, height 90, app icon 48 + label 20, divider, Live TV, Inputs. Offline: hero shows `offline_backdrop` + Caption text "Atomix is offline"; Library/Search rail items disabled. Initial focus: Resume button (or first dock app when offline).
- [ ] **Step 5: Run tests** — PASS.
- [ ] **Step 6: On-device check** — install, launch, `Save-TvShot home-1`; read the PNG and compare with the spec sketch. Move focus along the row with `Key DPAD_DOWN` / `DPAD_RIGHT` ×3 and `Save-TvShot home-2`: hero changed to the third card. Smoothness: `Tv shell dumpsys gfxinfo dev.atomix.launcher reset`, press `DPAD_RIGHT` 10× with 300 ms gaps, then `Tv shell dumpsys gfxinfo dev.atomix.launcher` — expected "Janky frames" ≤ 10%. Memory: `Tv shell dumpsys meminfo dev.atomix.launcher` TOTAL PSS ≤ 150 MB. Offline: stop Atomix on the PC (ask the owner first) or block by setting a wrong saved URL in a debug build; expect offline hero and working dock.
- [ ] **Step 7: Commit** `feat: home screen`.

### Task 7: WebScreenActivity (player, item, library, search)

**Files:**
- Create: `web/WebScreenActivity.kt`, `web/WebBack.kt`
- Modify: `AndroidManifest.xml` (activity, `configChanges="orientation|screenSize|keyboardHidden"`, theme fullscreen, `hardwareAccelerated="true"`), `HomeScreen.kt` (Rail Library → `#/library/{firstVideoLibraryId}` from `/api/home` `libraries`, or `#/` if none; Search → `#/search`)
- Test: `WebBackTest.kt`

**Interfaces:**
- Consumes: `SessionStore.serverUrl`, `SessionStore.token`, `openWeb(route)`.
- Produces:
  - `WebScreenActivity` extra `EXTRA_ROUTE: String`.
  - `fun shouldClose(rootRoute: String, currentUrl: String, canGoBack: Boolean): Boolean` — true when `!canGoBack`, or when current hash route equals `rootRoute` and the Back key arrives; false otherwise.

- [ ] **Step 1: Failing tests** — `WebBackTest.atRoot_closes`; `WebBackTest.afterNavigation_goesBack` (root `#/library/3`, current `#/play/9`, canGoBack → false); `WebBackTest.noHistory_closes`.
- [ ] **Step 2: Run** — FAIL. **Step 3: Implement**: before loading, `CookieManager.setCookie(serverOrigin, "atomix_session=$token; Path=/")` + `flush()`; WebView settings `javaScriptEnabled`, `domStorageEnabled`, `mediaPlaybackRequiresUserGesture=false`; `WebChromeClient` grants `RESOURCE_PROTECTED_MEDIA_ID` only for the server origin; load `serverUrl + route`; handle `KEYCODE_BACK` with `shouldClose` → `webView.goBack()` or `finish()`; `onDestroy` → `removeAllViews(); destroy()`; set `FLAG_KEEP_SCREEN_ON` while open. Home refreshes in `onResume` (Task 6).
- [ ] **Step 4: Run tests** — PASS.
- [ ] **Step 5: On-device check** — from Home press OK on Resume; wait 8 s; `Tv shell dumpsys media_session` shows a playing state or `Tv shell dumpsys activity top` shows WebScreenActivity with the player route; screenshots may be black (DRM) — that is expected. Press `Key BACK` until Home; expect Home with updated progress line. Repeat open/close 3× and check `dumpsys meminfo dev.atomix.launcher` TOTAL PSS returns within 20 MB of the first reading (Review Focus 5). Library → open a title → play → Back returns to the library page, Back again returns Home.
- [ ] **Step 6: Commit** `feat: embedded Atomix player and pages`.

### Task 8: Apps grid, dock editing, Inputs and Live

**Files:**
- Create: `ui/apps/AppsScreen.kt`, `ui/home/DockEditMenu.kt`
- Modify: `HomeScreen.kt` (Rail Apps → AppsScreen; Rail Live and dock Live TV → `liveTvIntent`; Inputs → `InputsDialog`)
- Test: covered by `DockStoreTest` (Task 5); this task adds `AppsScreenStateTest.addToDock_appendsWhenAbsent`.

**Interfaces:**
- Consumes: `InstalledApps.list()`, `DockStore`, `TvInputs`, `safeStart`.
- Produces: `@Composable fun AppsScreen(onBack)` — 6-column grid of 16:9 banners (banner or icon on glass), label under, OK launches, long-press OK (`onLongClick`) opens menu "Add to dock" / "Remove from dock". Dock long-press: "Move left", "Move right", "Remove".

- [ ] **Step 1: Failing test** `AppsScreenStateTest.addToDock_appendsWhenAbsent` and `..._noDuplicate`. **Step 2: Run** — FAIL. **Step 3: Implement.** **Step 4: Run** — PASS.
- [ ] **Step 5: On-device check** — open Apps from the rail, launch TV Bro, Home returns to Atomix (via `am start` while not yet default — `Key HOME` still goes to stock until Task 9); open Inputs dialog, select HDMI 1, expect the TV to switch (`Tv shell dumpsys activity top` shows the Live TV activity). Add/remove a dock app and confirm it persists after `Tv shell am force-stop dev.atomix.launcher`.
- [ ] **Step 6: Commit** `feat: apps grid, dock editing, inputs`.

### Task 9: Release build and becoming Home (reversible)

**Files:**
- Modify: `app/build.gradle.kts` (release: `isMinifyEnabled=true`, `isShrinkResources=true`, proguard rules for kotlinx.serialization; signing with a local keystore `D:\atomix-launcher-tools\atomix-release.jks`, passwords read from `D:\atomix-launcher-tools\keystore.properties`, both git-ignored — the owner chooses the password and types it into that file; Claude never sees or enters it)
- Create: `scripts\become-home.ps1`, `scripts\restore-stock-home.ps1`
- Modify: `D:\TV\undo.ps1` (append a "Launcher" section calling `restore-stock-home.ps1`), `README.md` (cheat sheet)

**Interfaces:**
- Produces: `become-home.ps1 [-Step 1|2|3]`:
  1. back up `resolve-activity` HOME result to `D:\TV\backup\launcher-<timestamp>\home.txt`; `cmd package set-home-activity --user 0 dev.atomix.launcher/.HomeActivity`; verify `resolve-activity` returns it; `Key HOME`; `Get-TvFocus` shows HomeActivity.
  2. reboot test: `Tv reboot`, wait for `adb connect`, `Key HOME`, verify again.
  3. only if 1–2 fail and the owner says yes: `pm disable-user --user 0 com.google.android.tvlauncher`, append `com.google.android.tvlauncher` to `D:\TV\disabled-by-us.txt`, reboot test again; afterwards check Live TV, HDMI inputs, casting and the Assistant button and report each.
  `restore-stock-home.ps1`: `pm enable com.google.android.tvlauncher`; `cmd package set-home-activity --user 0 com.google.android.tvlauncher/.MainActivity`; verify; leaves the Atomix APK installed (uninstall line commented).

- [ ] **Step 1: Release build** — `gradlew.bat :app:assembleRelease`; expected APK ≤ 15 MB; install over debug (uninstall debug first if signatures differ — ask, since it clears setup).
- [ ] **Step 2: Release performance check** — repeat Task 6 Step 6 gfxinfo and meminfo on the release build; record numbers in README.
- [ ] **Step 3: Become Home step 1 + 2** with the owner watching; report results.
- [ ] **Step 4: Undo dry-run** — run `restore-stock-home.ps1`, verify stock Home returns, then `become-home.ps1 -Step 1` again.
- [ ] **Step 5: README cheat sheet** — build, install, become Home, restore, where logs are, how to change the server address, and the setup note that NordVPN on the PC must allow LAN access (optionally reserve the PC's IP in the router).
- [ ] **Step 6: Commit** `feat: release build and home switching scripts`.
