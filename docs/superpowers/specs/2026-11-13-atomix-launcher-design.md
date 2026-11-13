# Atomix Launcher — design

Date: 2026-11-13 · Status: approved by owner 2026-11-13

## Intent

A custom home screen for the owner's VEON 4K Android TV that is its own product, branded **Atomix**, in the look of Atomix's Orbit theme. It replaces the stock Google TV home with a cinematic screen whose hero and "Continue watching" come live from the owner's self-hosted Atomix server (D:\nodeflix, a Node.js media hub running on the owner's PC at port 8787), and whose lower part is a dock of the TV's own apps, Live TV and HDMI inputs. Pressing Resume plays inside the launcher using Atomix's existing web player; Back returns home.

Why: the owner wants a home screen that feels like theirs, not a copy of Projectivy or Google TV, and that puts their own Atomix library first.

## Visual direction (from the owner's references)

A blend, keeping the best part of each:

- **Orbit Lounge (owner's Atomix concept):** Atomix atom mark + wordmark top-left; slim floating smoked-glass **left rail** with line icons and small labels (Home, Apps, Library, Live, Inputs, Search); warm, full-bleed artwork.
- **Dribbble reference:** the rail collapses to icons only when focus leaves it; a thin **progress line with "42 min left"** directly under the hero metadata; artwork fading into a deep gradient at left and bottom.
- **Vista:** the hero title as the film's **clear logo** (falls back to the title in Display type), one metadata line, two pill buttons; Inputs always one press away.
- **Quiet / cinema:** restraint — one content row on the home, then a **floating smoked-glass dock** of apps with a thin divider before Live TV and Inputs.

Layout at 1920×1080 (all sizes scale with screen):

```
◎ Atomix                                         8:42  (D)
┌──┐  [clear logo or TITLE]        [full-bleed backdrop,
│⌂ │  2026 · Sci-fi · 1h 56m · 4K    scrim left + bottom]
│▦ │  ━━━━━━━━━━──── 42 min left
│▤ │  Two lines of plot…
│((•))[ ▶ Resume ]  [ More info ]
│⇥ │  Continue watching
│⌕ │  [card ━━] [card ━] [card ━━━] [card]
└──┘
        ╭ SmartTube Twitch Prime TV Player │ Live TV  Inputs ╮
```

Design tokens follow Atomix `DESIGN.md` (Orbit): Deep Space `#05050c` background, page-shade scrim strongest at left/bottom, text white / soft / dim, Clear and Tinted Glass with a 1px inner edge, accent = **artwork tint** of the current backdrop (fallback Ice `#8fe9ff`), focus = **white ring with dark gap** (`0 0 0 5px` Deep Space then `0 0 0 9px` white), focused card scale 1.08 with Lift shadow, focused button turns solid white with ink text and scales 1.05 over 180 ms, card corners 18 px, pill buttons, Sora typeface (OFL, bundled from Atomix's `public/fonts`). Type scale from DESIGN.md: Display 116, Title 26, Body 22, Label 20, Caption 16 (×screen factor). 64 px safe margins.

Because the TV is low-memory, glass is **solid glass** (no live blur): blur is faked with pre-darkened translucent fills, exactly as Orbit's own "Reduce effects" does.

## Goals

1. Home appears in well under a second when Home is pressed, and stays smooth (no dropped frames when moving focus) on a 1.4 GB MediaTek armeabi-v7a TV.
2. Hero and Continue watching show real titles, artwork and resume progress for the selected Atomix profile.
3. Resume/Play opens Atomix's player full screen inside the launcher; Back from the player returns to the launcher home, with progress refreshed.
4. Dock opens installed TV apps; Live TV and Inputs switch to the TV tuner / HDMI sources.
5. Works with the remote alone (D-pad, OK, Back, Home), with one unmistakable focused element at all times.
6. Becoming the default Home is reversible, following the order in the research report (`D:\TV\reports\Android TV home screen design.md`).

## Non-goals (v1)

- A native video player (that is approach C, a possible later upgrade).
- Reading other apps' viewing progress (Android does not allow it for third-party launchers).
- Google Assistant/voice search integration, notifications panel, widgets, phone/touch layouts.
- Publishing to Play Store.
- Any change that breaks Atomix's "no npm, no build step" rule — the launcher is a separate project; Atomix itself needs no changes for v1.

## Anti-goals (failure even if it "works")

- Home that stutters, flashes white, or takes seconds to show after pressing Home.
- A blank or error screen when the PC/Atomix is off — the TV must stay usable.
- Profile/kids rules bypassed: the launcher must only show what Atomix's API returns for the signed-in profile.
- Looking like a copy of Projectivy, Google TV or the reference shots, or using any of their code/assets.
- Leaving the TV stuck without a working Home (no un-revertable launcher changes).

## Constraints

- Device: VEON 4K Android TV, Android TV OS 11 (API 30), MediaTek, armeabi-v7a, ~1.4 GB RAM, 1920×1080 UI. ADB at 192.168.8.67:5555 from this PC.
- Atomix server: http://<PC LAN IP>:8787 (currently 192.168.8.65; the PC also runs NordVPN/Tailscale). Plain HTTP on the LAN.
- Atomix API facts (verified in source):
  - Sign-in: **Quick Connect** — `POST /api/quickconnect` → `{code, poll, expiresAt}`; the owner approves the code in Atomix on another device; `POST /api/quickconnect/poll {poll}` → `approved` + session cookie `atomix_session`. Also `POST /api/auth/login {username,password,wantToken:true}` → `{user, token}`.
  - Auth on requests: `Authorization: Bearer <token>` or cookie `atomix_session`.
  - Profiles: `GET /api/profiles`, `POST /api/profiles/:id/select` (the profile is stored on the session, so the player sees the same profile). PIN-protected profiles exist.
  - Home: `GET /api/home` → `{hero, rows[], libraries[]}`; hero has title, year, genres, runtime, overview, `backdrop`, `logo`, `poster`, tint colours, `progress`, `tagline`, `trailer`. Rows have `key`, `title`, `style` and `items` (continue row key `continue`).
  - Images: relative URLs under `/api/items/:id/image/:type`.
  - Player: the web app route `#/play/<itemId>`; it reports progress itself.
- Toolchain is not installed on the PC (no JDK, no Android SDK); the plan must install JDK 17 + Android command-line SDK from official sources.
- Every change on the TV must be reversible; the launcher APK is installed with `adb install` and listed in `D:\TV\installed-by-us.txt`.

## Approaches considered

- **A. Native home + Atomix web player in an embedded WebView — chosen.** Native home is fast and light, works with the PC off, and the WebView only lives during playback, reusing all of Atomix's player features. Cost: Orbit's look is re-implemented natively.
- **B. Whole launcher as an Atomix web page in a WebView shell.** Single codebase and exact Orbit look, but a browser engine resident as Home (~150–250 MB on a 1.4 GB TV), slower Home presses, and a blank home when the PC is off. Rejected for performance.
- **C. Fully native including the player (Media3/ExoPlayer).** Best playback, but rebuilds subtitles, Skip intro, Up next, previews. Deferred as a future upgrade of A.

## Decided by the owner

- Build their own launcher, its own thing, not a Projectivy copy; brand is Atomix.
- Visual blend of Orbit Lounge, Dribbble, Vista and Quiet cinema as above.
- Content comes from Atomix.
- Play happens inside the launcher.
- Approach A.
- **Kids rules cover Atomix content only.** The launcher's apps, dock, Inputs and profile switching are not locked for kids profiles (Atomix's API already filters titles per profile; PINs on profile selection are Atomix's own).
- **Hero follows focus.** Moving along the Continue watching row switches the hero (backdrop, logo, metadata, progress line, tint) to the focused card after a short pause (~300 ms debounce) with a soft crossfade. Backdrops are loaded downscaled (≈1280 px wide) and the neighbours of the focused card are prefetched, so the no-dropped-frames goal holds.

## Proposed by Claude (owner may correct)

- **Stack:** Kotlin, Jetpack Compose for TV (androidx.tv material), Coil for images with explicit size caps, kotlinx.serialization + OkHttp for the API, Android WebView for the player. minSdk 26, target/compile SDK 34, ABI armeabi-v7a + arm64-v8a. Release builds with R8 and a baseline profile for smooth focus movement.
- **Project location:** `D:\atomix-launcher` (separate from D:\nodeflix). App id `dev.atomix.launcher`, name "Atomix".
- **First run:** asks for the Atomix address (pre-filled by trying the last-known PC IP, port 8787), then Quick Connect (shows the code large on the TV), then "Who's watching?" using Atomix profiles (PIN entry via on-screen number pad if the profile has one). Token stored in encrypted app storage.
- **Home data:** fetched on every return to Home (cached last response shown instantly, refreshed in background). Hero = Atomix `hero`; if it has progress, primary button reads "Resume · N min left", else "Play". Row = the `continue` row (falls back to the first non-empty row). Only one content row on Home, by design.
- **Rail:** Home, Apps (grid of all installed launchable apps), Library (opens Atomix's library page in the player WebView), Live (Live TV app), Inputs (HDMI picker), Search (opens Atomix search in the WebView). Collapses to icons when focus moves right.
- **Dock:** owner's favourite apps (default: SmartTube, Twitch, Prime Video, TV Player, TV Bro), then divider, Live TV, Inputs. Long-press OK on a dock app → move/remove; "Add to dock" from the Apps grid.
- **Inputs:** listed via `TvInputManager`; selecting one opens it through the TV's Live TV app with a passthrough-input URI.
- **Player screen:** a full-screen WebView activity loading `http://<server>/#/play/<id>` with the session cookie set for the server origin; Android Back is passed to the page; when the page leaves the play route (or Back on the player's first screen) the activity closes and Home refreshes. WebView is destroyed on exit to free memory.
- **More info:** opens Atomix's own detail page (`#/item/<id>` route as the web app uses it) in the same WebView screen; no native details screen in v1.
- **Library and Search screens (WebView):** one WebView activity serves Player, Library, Search and More info. Navigation inside it follows Atomix's own history, so Library → play → Back returns to Library. Android Back at the page that was opened first (the root) closes the activity and returns to Home. The WebView exists only while that activity is open and is destroyed on exit; Home itself never holds a WebView.
- **Profile persistence:** the selected profile is remembered across reboots; switching is via the profile pill (which shows "Who's watching?"). Atomix's own PIN rules apply when selecting a PIN-protected profile.
- **Finding the server:** use the saved address; if it fails, try the last-known IP, then probe the TV's /24 subnet for port 8787 answering Atomix's API; manual re-entry in settings always available. Setup notes that NordVPN on the PC must allow LAN access. Recommend reserving the PC's IP in the router (owner's action, optional).
- **PC off / unreachable:** hero becomes a calm Atomix-branded backdrop (bundled image) with "Atomix is offline" in small text; dock, Apps, Live and Inputs keep working.
- **Clock and profile pill** top-right as in Orbit.
- **Becoming Home:** a settings screen documents and the ADB scripts perform: (1) install; (2) `cmd package set-home-activity dev.atomix.launcher/.MainActivity` and verify with `resolve-activity`; (3) only if Home still reverts, `pm disable-user --user 0 com.google.android.tvlauncher`. Each step has an undo in `D:\TV\undo.ps1`. Before step 3 the launcher must have passed a reboot test.

## Left to the builder

- Exact Compose component structure, navigation, and state management.
- How artwork tint is computed natively (Atomix provides tint values in serialized items; prefer those, else Palette).
- Image downscaling sizes, cache limits, and memory budget checks (`dumpsys meminfo` on device).
- WebView settings needed for Atomix's player (media autoplay, DRM/EME, fullscreen), discovered by testing on the TV.
- Test strategy (unit tests for API parsing and home-state logic; on-device checks via ADB screenshots/uiautomator; DRM-protected playback shows as black in screenshots).
