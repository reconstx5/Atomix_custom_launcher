# Atomix launcher

A home screen for Android TV, in the look of Atomix's Orbit theme. The hero and Continue watching come from your Atomix server; Resume plays with Atomix's own player inside the launcher.

- Design: `docs/superpowers/specs/2026-11-13-atomix-launcher-design.md`
- Plan: `docs/superpowers/plans/2026-11-13-atomix-launcher.md`

## Build and install

Tools live in `D:\atomix-launcher-tools` (JDK 17, Android SDK, Gradle). Then:

```powershell
.\scripts\install.ps1
```

`.\scripts\install.ps1 -Release` builds and installs the signed release build (R8-shrunk).
It needs the release key first. You make it once, in your own terminal:

```powershell
.\scripts\create-keystore.ps1
```

You choose the password. It goes only into keytool's prompt and `D:\atomix-launcher-tools\keystore.properties`.
Back up `atomix-release.jks` and `keystore.properties` together, because updates must be signed with the same key.
Switching from a debug build to the release build needs one uninstall (the signatures differ). That clears setup,
so you sign in with Quick Connect once more.

## Cheat sheet

| What | Command (from `D:\atomix-launcher`) |
| --- | --- |
| Make Atomix the Home screen | `.\scripts\become-home.ps1 -Step 1` (backs up the current Home to `D:\TV\backup\launcher-<time>\home.txt`) |
| Check it survives a reboot | `.\scripts\become-home.ps1 -Step 2` |
| Last resort if Home keeps reverting | `.\scripts\become-home.ps1 -Step 3` (disables the Google TV launcher, recorded in `D:\TV\disabled-by-us.txt`) |
| Put the stock Home back | `.\scripts\restore-stock-home.ps1` (also part of `D:\TV\undo.ps1`; Atomix stays installed) |
| Live logs | `D:\TV\platform-tools\adb.exe -s 192.168.8.67:5555 logcat --pid=$(D:\TV\platform-tools\adb.exe -s 192.168.8.67:5555 shell pidof dev.atomix.launcher)` |
| Screenshot | `. D:\TV\tvlib.ps1; Save-TvShot name` (saved to `D:\TV\logs\name.png`) |
| Change the server address | `D:\TV\platform-tools\adb.exe -s 192.168.8.67:5555 shell pm clear dev.atomix.launcher`, then press Home. Setup runs again: it finds Atomix on the network, or you type the address. |

### Network notes

- The TV reaches Atomix at `http://192.168.8.65:8787`. If NordVPN runs on the PC, it must allow local network (LAN) traffic. Otherwise the TV can't reach the server.
- Reserve the PC's IP (192.168.8.65) in the router so the address never changes. If it does change, use "Change the server address" above.

## Performance (VEON 4K, Android TV 11, 1.4 GB RAM)

Measured with `dumpsys gfxinfo` on the release configuration (R8, speed-profile compiled), moving focus along the row 16 times, one press every 300 ms:

| Pass | Janky frames | p50 | p90 |
| --- | --- | --- | --- |
| First pass after launch (art not cached yet) | 15% | 10 ms | 21 ms |
| Warm, fast presses | 4% | 9 ms | 11–13 ms |
| Warm, browsing pace (0.9 s per press) | 0–5% | 9 ms | 11–12 ms |

Memory on Home: 81 MB PSS (budget 150 MB). The debug build started at 98% janky. Four fixes brought it down:
- an in-memory settings cache;
- page shading baked into the hero bitmap;
- recomposing only the hero when focus moves;
- a fixed-height hero text block, so the row never re-lays out.
