# Atomix TV

A home screen for Android TV: your Live TV channels and your apps, from your own Atomix server. Channels play inside
the launcher — there is no hand-off to another app. Atomix itself is a separate tile on the row, not the launcher.

- Design: `docs/superpowers/specs/2026-11-13-atomix-tv-overhaul-design.md`
- Plan: `docs/superpowers/plans/2026-11-13-atomix-tv-overhaul.md`
- On-device verification: `D:\TV\reports\launcher-overhaul-verification.md`

## What's on screen

**Home** — the focused channel's programme over its artwork, then **On now** (your pinned channels, with now/next and
how long is left) and then **Apps** (your favourites, then All apps). **Live TV** lists every channel so you can pin
one. **Apps** lists everything installed so you can add one to Home. The ⚙ opens Settings.

**Playing a channel.** OK on a card plays it. While playing: OK shows the banner, ◀ opens the quick channel list,
Up/Down change channel, digits tune by number. Pause and rewind appear only when the channel has a rewind buffer.
Back closes one layer at a time — number entry, then the list, then the banner — and from a clean screen leaves
playback and puts focus back on the channel you were watching.

**Pin, unpin, reorder.** Press ✎ Edit in the "On now" header (or at the end of the apps row). Then: OK picks a card
up, ◀ ▶ move it, OK puts it down, ▼ unpins or removes, Back is done. Nothing is hidden behind a long press.

**Settings** (⚙) — Live TV sources with their channel counts, last refresh and any errors, with Refresh and
"Manage sources in Atomix" for admins; Server address and Find again; Profile; Display; Home button; About.

**Reduced effects** (Settings › Display) makes panels solid and removes animation, keeping the focus ring and every
function. It is off by default and never switches itself on. On this VEON TV it is worth turning on: it removes the
frame drops when focus crosses between the two rows. See the verification report for numbers.

## Build and install

Tools live in `D:\atomix-launcher-tools` (JDK 17, Android SDK, Gradle). Then:

```powershell
.\scripts\install.ps1
```

`.\scripts\install.ps1 -Release` builds and installs the signed release build (R8-shrunk).
It needs the release key first. You make it once, in your own terminal:

```powershell
powershell -ExecutionPolicy Bypass -File .\scripts\create-keystore.ps1
```

You choose the password. It goes only into keytool's prompt and `D:\atomix-launcher-tools\keystore.properties`.
Back up `atomix-release.jks` and `keystore.properties` together, because updates must be signed with the same key.
Switching from a debug build to the release build needs one uninstall (the signatures differ). That clears setup,
so you sign in with Quick Connect once more.

## Cheat sheet

Windows blocks `.ps1` files by default. Run each script as `powershell -ExecutionPolicy Bypass -File <script> [args]`,
which allows that one run and changes no setting. The table shows the script part.

| What | Command (from `D:\atomix-launcher`) |
| --- | --- |
| Make the Home button open Atomix TV (this TV) | On the TV: Settings > Device Preferences > Accessibility > **Atomix Home button** > On. It only notices the Google TV home screen opening and never reads the screen. Settings › Home button shows whether it is on. |
| Open the launcher without the Home button | `D:\TV\platform-tools\adb.exe -s 192.168.8.67:5555 shell am start -n dev.atomix.launcher/.HomeActivity` |
| Make Atomix TV the Home screen (other TVs) | `.\scripts\become-home.ps1 -Step 1` (backs up the current Home to `D:\TV\backup\launcher-<time>\home.txt`) |
| Check it survives a reboot | `.\scripts\become-home.ps1 -Step 2` |
| Last resort if Home keeps reverting | `.\scripts\become-home.ps1 -Step 3` (disables the Google TV launcher, recorded in `D:\TV\disabled-by-us.txt`) |
| Put the stock Home back | `.\scripts\restore-stock-home.ps1` (also turns off the Home button override; part of `D:\TV\undo.ps1`; Atomix TV stays installed) |
| Live logs | `D:\TV\platform-tools\adb.exe -s 192.168.8.67:5555 logcat --pid=$(D:\TV\platform-tools\adb.exe -s 192.168.8.67:5555 shell pidof dev.atomix.launcher)` |
| Screenshot | `. D:\TV\tvlib.ps1; Save-TvShot name` (saved to `D:\TV\logs\name.png`) |
| Change the server address | Settings › Server › **Find again**. If that doesn't find it: `D:\TV\platform-tools\adb.exe -s 192.168.8.67:5555 shell pm clear dev.atomix.launcher`, then open the launcher — setup runs again and you can type the address. |

Never `am force-stop dev.atomix.launcher` on this TV: Android clears `enabled_accessibility_services` with it, which
switches the Home-button override off. Use `adb install -r`, which restarts the app without that side effect.

On this VEON TV the firmware keeps the Google TV launcher as Home. `become-home.ps1` is accepted but ignored, and a
disabled stock launcher is switched back on at boot. That is why the Home button override exists.

### Network notes

- The TV reaches Atomix at `http://192.168.8.65:8787`. If NordVPN runs on the PC, it must allow local network (LAN) traffic. Otherwise the TV can't reach the server.
- Reserve the PC's IP (192.168.8.65) in the router so the address never changes. If it does change, use "Change the server address" above.

## Performance (VEON 4K, Android TV 11, 1.4 GB RAM, Mali-470)

Release build, `dumpsys gfxinfo`, speed-profile compiled at install, settled (warm) runs:

| Scenario | Janky frames |
| --- | --- |
| Along the "On now" cards | 0–8 % |
| Along the apps row | 2–10 % |
| Up/down between the two rows | 13–50 % |
| Anything, with Reduced effects on | 6 % |

Memory on Home: 75 MB PSS cold, 103–116 MB after a long session with artwork cached (budget 150 MB).

Crossing between rows slides the whole 1920×1080 column and fades the spotlight; this GPU cannot composite a
full-screen translating layer inside 16.7 ms, so frames drop there. Three code changes were tried against it and none
helped — the detail, and the measurements, are in the verification report. **Reduced effects** removes the transition
and is the answer on this hardware.

Note that the first one or two passes after any install read much worse (JIT warm-up), and a fresh install reads about
30 % until Android has dexopted the app. A baseline profile would fix that; it is not in this build.

The design keeps costs down in a few deliberate ways: no real-time blur (glass is one small pre-blurred copy of the
backdrop, made once per artwork change), full-screen shading baked into the artwork bitmap rather than drawn per
frame, and the spotlight following card focus only after it settles.
