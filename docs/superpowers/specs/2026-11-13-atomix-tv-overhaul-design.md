# Atomix TV launcher overhaul — design

Status: draft for owner approval · 2026-11-13
Builds on: `2026-11-13-atomix-launcher-design.md` (the first launcher), research report `D:\TV\reports\TV launcher home design.md`,
mockups `.superpowers/brainstorm/772-1794551225/content/combined-v4.html` (frames 1, 3–6 are the approved direction).

## 1. Intent

The launcher today feels like Atomix: its Home is built from Atomix's movie hero, "Continue watching" row and
library shortcuts, and Atomix's web pages open inside it. The owner wants the launcher to be **its own TV home**:
apps, inputs and Live TV. Atomix becomes **one app among the others**, with its movies, series, music, library
and search living inside Atomix. This update is also the **foundation** for later updates, so its parts must be
reusable and its responsibilities separate.

Success, in the owner's words (completion criteria, all required):

- The launcher opens to its own home screen; Atomix launches through its app shortcut.
- Live TV is the only media content on Home; selecting a Live TV card plays it inside the launcher.
- Installed apps and supported inputs stay reachable.
- Users can pin and reorder favourite apps and channels; changes survive restarts.
- Remote navigation, playback controls and returning from apps work reliably; focus and scroll are restored.
- Live TV setup and troubleshooting are in Settings.
- Reduced-effects mode works without removing functionality.
- Missing artwork, missing programme data and disconnected services never break Home.
- The structure gives reusable building blocks for later updates.

## 2. Goals, non-goals, anti-goals

Goals: calm, cinematic Home (Apple TV, Arctic Horizon 2, Atomix Orbit — own identity); refined type, subtle glass,
generous spacing, obvious focus, readable across the room; native in-launcher Live TV playback; remote-only
customisation with visible controls; reduced-effects option.

Non-goals: movie/series recommendations, posters, library sections or continue-watching on Home; a TV guide grid;
recordings, catch-up or start-over (Atomix keeps these); radio on Home; the TV's own aerial channels as cards
(Android 11 lets only system apps read them — they stay reachable via Inputs → Live TV app); a second APK for Atomix.

Anti-goals (failure even if it "works"): oversized greetings or busy dashboards; controls that do nothing
(no pause/rewind unless the stream supports it, never a record button); essential actions hidden behind long-press;
a home screen that blanks, spins forever or crashes when the server is off; jank on this TV (target ≤ 10 % janky
frames when warm, as achieved in the first launcher).

## 3. Constraints (facts)

- Device: VEON 4K, Android TV 11 (API 30), MediaTek, armeabi-v7a, ~1.4 GB RAM, 1920×1080 UI. ADB at 192.168.8.67.
- Stack stays: Kotlin 2.0.20, AGP 8.5.2, compileSdk 34, minSdk 26, Compose BOM 2024.09.03, Coil 2.7, OkHttp,
  kotlinx.serialization, Robolectric tests. New dependency: **AndroidX Media3** (ExoPlayer + DASH + UI-free), a
  version that builds with compileSdk 34 (1.4.x).
- The stock Google TV launcher can't be replaced on this firmware; the existing **Home-button override**
  (accessibility service) stays and keeps working.
- Live TV comes from the owner's Atomix server (`http://192.168.8.65:8787`), per signed-in profile:
  - `GET /api/live/status` → `{ enabled, sources:[{id,name,channels,lastOk, (admin) error, warning, lastRefresh…}] }`
  - `GET /api/live/channels[?favourites=1]` → channels `{ id, number, name, logo, radio, drm, favourite, groups,
    now?, next? }` (now/next `{ id, title, start, end, … }`). 461 channels today: 42 TV, 419 radio, 3 encrypted,
    35 with guide data.
  - Favourites (shared with Atomix, per profile, ordered): `POST /api/live/favourites/:id`,
    `DELETE /api/live/favourites/:id`, `PUT /api/live/favourites {channelIds:[…]}` (sets order). **PUT removes any
    visible favourite missing from the list**, so the launcher must send radio favourites too (in their existing
    order, after the TV ones) or it would wipe the owner's radio favourites in Atomix.
  - Play: `POST /api/live/channels/:id/play {caps, replaces?}` →
    `{ sessionId, delivery:"dash", manifest, timeUrl, rewind:true, channel, programme, next }` (tuner path, 30-min
    buffer) or `{ sessionId, delivery, url, … }` (converted stream), or for encrypted channels
    `{ delivery:"drm", manifest, licence, keySystem, … }`.
  - Keep-alive: `POST …/:sid/ping` (viewer dropped after 2 min without one) and `POST …/:sid/stop` on exit, under
    `/api/live/tuner/` (dash), `/api/live/drm/` (encrypted) or `/api/playback/` (converted stream).
  - The TV's profile (Dallas) is an admin: `/api/live/status` returns error, warning, lastRefresh and groups, and
    `POST /api/live/sources/:id/refresh` is allowed. Atomix's own source setup page is `#/settings/live`.
  - Logos: `/api/live/channels/:id/logo` (Bearer token).
- Owner's standing rules: reversible TV changes; official sources only; never handle passwords; commits to `main`
  with the project's attribution line.

## 4. Approaches considered

1. **Rework the existing app by area (chosen by the owner).** One module; packages per responsibility; native
   Media3 player; staged so the launcher stays usable. Cheapest reliable path; keeps sign-in, dock and override.
2. Separate Gradle modules (core / apps / livetv / atomix). Stricter boundaries, slower builds, more ceremony —
   the "unnecessarily complex framework" the brief rules out.
3. Play through Atomix's web player in a hidden WebView. Least code, but playback depends on Atomix's UI and the
   WebView's memory — contrary to the brief.

## 5. Decisions

### 5.1 Responsibilities (packages under `dev.atomix.launcher`)

| Package | Owns | Never does |
|---|---|---|
| `shell` | `HomeActivity`, top menu (Home · Live TV · Apps), Inputs/Settings/clock pill, screen switching, focus and scroll memory | network calls, playback |
| `apps` | installed-app discovery (cached, refreshed on package changes, off the main thread), launching, HDMI inputs | UI |
| `livetv.source` | channels, now/next, favourites (read/write via Atomix), status; cached for offline display | playback |
| `livetv.player` | `PlayerActivity`: Media3 player, session start/ping/stop, zapping, overlays, error states | Home UI |
| `prefs` | favourite-app order, reduced effects, last-focused item per screen; migration from the old dock store | network |
| `atomix` | the **Atomix app entry**: an activity with its own Leanback launcher tile, icon, banner and task, opening Atomix's web app full screen (today's `WebScreenActivity`, moved) | being Home |
| `ui.kit` | reusable components: `FocusCard`, `ChannelCard`, `AppTile`, `GlassPanel`, `TopMenu`, `IconButton`, `HintBar`, `EmptyState`, `PlayerBanner`, `ChannelList`, `SettingsRow` — all honouring reduced effects | data access |
| `data` (kept) | Atomix HTTP client, session store, server locator | UI |

### 5.2 Atomix as its own app

Atomix is currently embedded (`WebScreenActivity`, opened from Home with routes). It becomes `atomix.AtomixActivity`:
exported with `MAIN` + `LEANBACK_LAUNCHER`, label "Atomix", own banner, `taskAffinity` of its own, opening the
Atomix root (`#/`). It appears in All apps and is a default favourite app (first). Pressing Home leaves it; opening
it again resumes where it was if its task is alive. *(My call)* `AtomixActivity` runs in its own process
(`android:process=":atomix"`), so its WebView never counts against Home's memory and Android can reclaim it on its
own; that process only reads the session (server URL, token) and writes the WebView cookie itself. The launcher
no longer opens Atomix routes from Home; the one exception is Settings → Live TV → "Manage sources in Atomix",
which opens `AtomixActivity` at `#/settings/live`.
Same APK — no second install, no new signing.

### 5.3 Home (approved mockup frame 1)

Layout at 1920×1080, safe margins 116 px left/right, 60 px top/bottom (Android TV grid / Apple inset, per the
research report); artwork bleeds to every edge:

- **Top bar (y 48):** wordmark "Atomix TV" left; glass pill menu centred: Home · Live TV · Apps (28 px, selected =
  semibold + dot); right glass pill: clock, Inputs icon button, Settings icon button (64 px targets).
- **Spotlight (y 170–520):** the focused channel's identity and current programme: channel number + logo plate,
  title 80/88 px SemiBold at normal tracking (2 lines max, ≤ 1000 px wide; wide uppercase tracking only on the
  eyebrow line and the wordmark), time range · genre · "N min left" (28 px), one-line description (28 px). Background:
  full-bleed programme image with baked shading; channel-logo-on-plate fallback; Orbit "Deep Space" backdrop when
  nothing is focused or no art. No buttons in the spotlight — OK on the card plays.
- **"On now" row (header ~y 640, cards ~y 712):** pinned TV channels in pinned order; cards 480×270, radius 16,
  gap 40 — three full cards and a ~244 px peek of the fourth. Cards are **logo-forward** (guide data and artwork
  are often missing, so programme art appears only in the spotlight): logo on a plate coloured from the logo
  (darkened until text reaches 7:1; hashed from the name when there's no logo), channel number 22 px top-right,
  programme title 28 px, "8:00–9:00 · 32 min left" 24 px, 4 px accent progress (6 px focused). The LIVE badge
  (#C62F3A, white text 5.4:1) shows only on the focused card and the spotlight. Focused adds "Next: …".
  Header carries a visible **✎ Edit** chip.
- **Apps row (top edge peeks ~50 px above the bottom while focus is in On now; moving down scrolls the column up
  ~390 px and dims the backdrop with a pre-computed darker copy):** favourite apps as icon (56 px) + name (26 px) pills; Atomix first by default; ends with
  **All apps**; a visible **✎ Edit** at the row's end.
- Focus: 6 px white ring + 1.08 scale + soft shadow, 150 ms in / 100 ms out; Up from a row goes to the row above,
  Up from the top row goes to the menu; Left/Right move along a row; the spotlight follows card focus after
  300 ms (backdrop crossfade 250 ms).

### 5.4 Live TV tab and Apps tab

- **Live TV:** every visible TV channel (radio excluded) as a vertical list or grid in channel-number order, each
  with now/next; OK plays; a visible **☆ Pin / ★ Pinned** button on each row pins/unpins (shared favourites).
- **Apps:** All apps grid (existing banners/icons), OK opens; a visible **☆ Add to Home / ★ On Home** button pins.
  Inputs remain under the Inputs button (HDMI list + "TV tuner" opening the stock Live TV app).

### 5.5 Edit mode (approved frame 3)

Entered from a row's ✎ Edit chip. OK on an item picks it up (lifted, "◀ Moving ▶", "Position n of m"); ◀/▶ move it
live; OK places; ▼ unpins/removes; Back cancels the move (or leaves edit mode when nothing is held). A hint bar
(◀ ▶ Move · OK Place · ▼ Unpin · Back Cancel) stays visible. A trailing "＋ Pin a channel / ＋ Add an app" tile
opens the Live TV / Apps tab. Channel order is written to Atomix (`PUT /api/live/favourites`) when edit mode ends;
app order is saved locally at once. Long-press may also open edit mode as a shortcut, never as the only way.

### 5.6 Live TV playback (frames 4–6a)

`PlayerActivity` (separate activity, so Back returns to Home's restored focus): Media3 ExoPlayer, one player and one
`SurfaceView` reused across channel changes.

- **Start:** `POST play` with caps (h264/hevc/aac, mp4, dash). Plays `manifest` (DASH) or `url`. Encrypted channels:
  Widevine via the `licence` URL **if** the device supports it; otherwise an "can't play this channel here" state
  (no crash). Ping every 30 s; stop on exit and on channel change (`replaces` the previous session).
- **Info banner:** on start, channel change and OK; bottom-anchored; number, logo, name, clock, programme title,
  start–end progress (from guide times), next; hides 5 s after the last key.
- **Controls:** shown only when supported. With a DASH tuner buffer (`rewind:true` and a live window ≥ 60 s):
  Pause, ⟲ 30 s, and a LIVE chip that becomes "−mm:ss · Jump to live" when behind. Otherwise none. No record.
- **Channel change:** Up/Down and CH+/CH− zap through the **list playback was started from** *(my call)*: pinned
  channels when started from Home or the quick list's Pinned tab (all TV channels if none are pinned); all TV
  channels when started from the Live TV tab or the quick list's All tab. The banner updates immediately; tuning
  starts ~500 ms after the last press (zap queue). The **quick channel list** (left glass panel, Pinned | All
  tabs, starts on the playing channel, never pauses video) opens with ◀ (see keys below).
- **Keys** *(my call)*:
  - Clean screen: OK shows the banner; ◀ opens the quick list; Up/Down zap; digits start number entry.
  - Banner showing with controls: focus sits on the controls row; ◀/▶ move between buttons, ◀ on the first
    button opens the quick list; OK activates; Up/Down still zap. Banner without controls: same as clean screen.
  - ◀/▶ never seek on their own. Remote media keys (Play/Pause, Rewind, Fast-forward) work only when the
    controls are supported (rewind = ⟲ 30 s; fast-forward = +30 s, never past live); otherwise they're ignored.
- **Number entry:** digit keys show the typed number in the banner; it commits 1.5 s after the last digit and
  tunes the TV channel with that number; an unknown number shows "No channel 123" for 2 s and stays put.
- **States:** Loading (logo + "Tuning…"), Unavailable / error after silent retries at ~1, 3 and 8 s (error card
  by ~20 s): message +
  **Try again · Next channel · Back to Home** (zapping still works). Behind-live-window errors seek to live.
- **Back:** closes one layer at a time (number entry → channel list → controls → banner); on a clean screen leaves
  playback and returns to where it started (Home or the Live TV tab), focus on the playing channel's card or row
  — or on the card that launched it if the playing channel isn't in that list.
- Keep screen on only while playing; audio focus for media.

### 5.7 Settings

A Settings screen (top-right ⚙): **Live TV** (source names, channel counts, last refresh, errors/warnings from
`/api/live/status`; **Refresh** per source via the admin refresh endpoint; **Test a channel** = opens the player
on the first pinned TV channel (else the first TV channel); **Manage sources in Atomix** = opens Atomix at
`#/settings/live` for adding/editing sources, which the launcher doesn't do natively). If the profile isn't an
admin (no error fields, refresh returns 403), the section shows counts and status only and hides Refresh,
saying "Ask an admin to manage Live TV sources". **Server** (address, connection status, change address — reruns
the locator; "Find again" after an IP change), **Profile** (who's watching, switch), **Display** (Reduced effects
on/off), **Home button** (override on/off status + how to turn it on), **About** (version). Home itself only shows
one-line messages that point here.

### 5.8 Empty, offline and degraded states (frame 6b)

- No pins: "On now" shows "Pin your channels" with a **Choose channels** button.
- No guide data for a channel: card shows the channel name and "No programme information", no progress bar; still
  plays.
- No logo/art: channel name on a tinted plate.
- Live TV not configured on the server (`enabled:false`): one-line message + **Open Settings**; apps unaffected.
- Server unreachable: last-known pins (cached channel list) shown dimmed with "Can't reach the TV server" +
  **Try again · Settings**; playing is disabled for those cards until reachable; the locator re-runs in the
  background after a failure. With no cache yet, the message panel of mockup frame 6b shows instead.
- Signed out / 401: setup (Quick Connect) as today.

### 5.9 Look and motion

Orbit palette (Deep Space #05050C, Ice accent), Sora type. Type scale (px): spotlight title 80/88, row header 30–40,
card title 28, body/meta 24–30, caption floor 22; text #F1F1F1 primary, #B4B8BF secondary (never grey on glass).
Glass = a small blurred copy of the current backdrop (computed once per artwork change off the main thread,
~240×135) drawn clipped under panels + Deep Space tint (≥ 40 % where the backdrop's top is pre-darkened, ≥ 62 %
otherwise) + 1 px light edge at 12–20 %; full-screen shading baked into the backdrop bitmap (drawn-per-frame
gradients cost 12–15 ms a frame on this TV). No real-time blur. Motion uses Compose; no Rive
in this update (optional later for a one-off logo animation).

**Reduced effects:** panels solid (#141726, 1 px edge), no blurred copy, no backdrop crossfade, no shadows/glow,
focus ring kept and scale change instant, banners appear without slide. Layout and every function unchanged. It is a manual toggle, off by default — never
switched on automatically from Android's low-RAM flag, which this 1.4 GB TV likely sets and which would disable
the chosen look on the only target device.

### 5.10 Focus and scroll memory

Each screen remembers its last focused item key and row scroll offset (in memory; Home's also in prefs). Returning
from any app restores Home focus to the tile that launched it (Atomix → the Atomix tile); leaving playback restores
focus to the playing channel's card (scrolling it into view). Returning to a tab restores its list position.

### 5.11 Migration and preservation

Keep the sign-in (server URL, token, profile), the Home-button override, and the dock order: the old `DockStore`
list becomes the initial favourite-apps list, with Atomix inserted first if absent. Old Home code (hero, continue
row, rail, setup unaffected) is removed once the new Home is in. No server changes are required.

### 5.12 Stages (launcher usable after each)

1. Foundations: packages, `ui.kit` basics, prefs + migration, app-discovery cache, Atomix app entry.
2. New Home: top menu, spotlight, On now (read-only pins), apps row, empty/offline states, focus memory.
3. Live TV playback: player activity, banner, zapping, quick list, controls when supported, error states.
4. Customisation: Live TV and Apps tabs with pin buttons, edit mode, order persistence.
5. Settings and reduced effects; polish; performance pass; on-device verification of every flow.

## 6. Verification

Unit tests (Robolectric) for: favourites ordering/migration, app-list cache invalidation, channel/now-next
parsing with missing fields, zap queue, back-layer stack, supported-controls decision, focus-memory keys.
On the TV: app launch and return (focus restored, including Atomix), playback start/exit, channel up/down and quick
list, pause/rewind/jump-to-live, error and retry (unplayable channel), server stopped (offline Home), no pins,
customisation surviving a force-stop and a reboot, reduced effects on/off, jank ≤ 10 % warm, memory within 150 MB
on Home. Anything not testable (e.g. Widevine channels if unsupported, casting) is reported as such.

## 7. Left to the builder

Exact Media3 version within the compileSdk-34 range; caps values sent to the server; list-vs-grid for the Live TV
tab; icon drawings; copy polish; how the blurred copy is produced (stack blur vs toolkit); ping/back-off timings
within the ranges above.
