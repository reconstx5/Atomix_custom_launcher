# Atomix TV

A home screen for Android TV: your Live TV channels and your apps, from your own Atomix server. Channels play inside
the launcher — there is no hand-off to another app. Atomix itself is a separate tile on the row, not the launcher.

- Design: `docs/superpowers/specs/2026-11-14-atomix-tv-redesign-design.md` (the look, in six phases; phase 1 is
  built), on top of `docs/superpowers/specs/2026-11-13-atomix-tv-overhaul-design.md` (how the launcher works)
- Plans: `docs/superpowers/plans/2026-11-14-atomix-tv-redesign-phase-1.md`,
  `docs/superpowers/plans/2026-11-13-atomix-tv-overhaul.md`
- On-device verification: `D:\TV\reports\launcher-redesign-phase-1.md` (this look),
  `D:\TV\reports\launcher-overhaul-verification.md` (the launcher underneath)
- What the TV reports about its inputs: `design/reference/inputs-findings.md`

## What's on screen

The top bar has three tabs (**Home**, **Live TV**, **Apps**), the clock, and two round buttons for **Inputs** and
**Settings**. Every screen except Home at rest ends in a bar of hints at the bottom right that says what OK, Back and
the arrows do right now. One thing on screen has the focus ring; nothing is reached by a long press.

**Home** is one fixed page. **Your apps** comes first: a row of tiles (banner and name) that ends with "All apps".
Under it, **On now**: the first channel you have on Home as a large card over its programme's artwork, and the rest
as small cards beside it, two at a time (Down past the second turns the page). OK on a card plays the channel.

**Edit** at the right of either row rearranges it, and only that: OK picks an item up, the arrows move it, OK or Back
puts it down, Back again is done. An app leaves "Your apps" in manage apps; a channel leaves Home with "Show on Home"
on the Live TV page.

**Live TV** lists every channel on the left, with a preview of the focused one on the right and one switch under it,
**Show on Home**. Right from a channel reaches the switch; Left goes back to the same channel. A small house marks the
channels that are on Home.

**Playing a channel.** While playing: OK shows the banner, Left opens the channel list ("On Home" or "All channels"),
Up and Down change channel, digits tune by number and show the channel they match before tuning. Pause, back 30
seconds and jump to live appear only when the channel has a rewind buffer. Back closes one layer at a time, and from
a clean picture leaves playback with focus on the channel you were watching.

**Apps** shows everything installed, seven across, with a house on the ones in "Your apps". OK opens an app. **Edit**
opens manage apps, where OK on an app offers "Add to Your apps" or "Remove from Your apps", and "App info" (the TV's
own page for that app).

**Inputs** is a page of the TV's HDMI ports and the TV tuner. OK switches to one; the Home button comes back.

**Settings** has a list of sections on the left and their rows on the right; OK or Right moves into the rows, Back
steps out one level. Today: **Set-up and launcher** (whether the Home button opens Atomix TV, and the way to the TV's
own settings where that is switched), **Appearance** (Reduced effects), **Live TV** (sources, their last refresh and
any errors, Refresh and "Manage sources in Atomix" for admins, "Test a channel"), **Account and server** (the server's
address, "Find the server again", "Switch profile") and **About**. A section appears once it has something that
works.

**Reduced effects** (Settings › Appearance) removes the glow around the focused item and all animation, keeping the
ring and every function. It is off by default and never switches itself on.

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
| Make the Home button open Atomix TV (this TV) | On the TV: Settings > Device Preferences > Accessibility > **Atomix Home button** > On. It only notices the Google TV home screen opening and never reads the screen. Settings › Set-up and launcher shows whether it is on. |
| Open the launcher without the Home button | `D:\TV\platform-tools\adb.exe -s 192.168.8.67:5555 shell am start -n dev.atomix.launcher/.HomeActivity` |
| Make Atomix TV the Home screen (other TVs) | `.\scripts\become-home.ps1 -Step 1` (backs up the current Home to `D:\TV\backup\launcher-<time>\home.txt`) |
| Check it survives a reboot | `.\scripts\become-home.ps1 -Step 2` |
| Last resort if Home keeps reverting | `.\scripts\become-home.ps1 -Step 3` (disables the Google TV launcher, recorded in `D:\TV\disabled-by-us.txt`) |
| Put the stock Home back | `.\scripts\restore-stock-home.ps1` (also turns off the Home button override; part of `D:\TV\undo.ps1`; Atomix TV stays installed) |
| Live logs | `D:\TV\platform-tools\adb.exe -s 192.168.8.67:5555 logcat --pid=$(D:\TV\platform-tools\adb.exe -s 192.168.8.67:5555 shell pidof dev.atomix.launcher)` |
| Screenshot | `. D:\TV\tvlib.ps1; Save-TvShot name` (saved to `D:\TV\logs\name.png`) |
| Change the server address | Settings › Account and server › **Find the server again**. If that doesn't find it: `D:\TV\platform-tools\adb.exe -s 192.168.8.67:5555 shell pm clear dev.atomix.launcher`, then open the launcher — setup runs again and you can type the address. |

Never `am force-stop dev.atomix.launcher` on this TV: Android clears `enabled_accessibility_services` with it, which
switches the Home-button override off. Use `adb install -r`, which restarts the app without that side effect.

On this VEON TV the firmware keeps the Google TV launcher as Home. `become-home.ps1` is accepted but ignored, and a
disabled stock launcher is switched back on at boot. That is why the Home button override exists.

### Network notes

- The TV reaches Atomix at `http://192.168.8.65:8787`. If NordVPN runs on the PC, it must allow local network (LAN) traffic. Otherwise the TV can't reach the server.
- Reserve the PC's IP (192.168.8.65) in the router so the address never changes. If it does change, use "Change the server address" above.

## Performance (VEON 4K, Android TV 11, 1.4 GB RAM, Mali-470)

Release build, `dumpsys gfxinfo`, real key presses about 1.5 s apart, each scenario run twice and the second kept.
"Before" is the build that was on the TV on 14 November 2026 before the redesign, measured the same day the same way.

| Scenario | Before the redesign | With the redesign |
| --- | --- | --- |
| Home, along a row (Right ×10, Left ×10) | 125 slow frames of 270 (46 %), along the "On now" cards | 11 of 19 (58 %), along the app tiles |
| Home, second scenario | 20 of 97 (21 %), along the apps row | 8 of 23 (35 %), Down and Up between the two rows ×5 |
| Apps (Down ×4, Up ×4) | 26 of 54 (48 %) | 9 of 18 (50 %) |

The redesign draws far fewer frames, because focus no longer animates: there is no growing, sliding or fading, so a
key press costs one or two frames instead of a dozen. Fewer frames are slow (9 to 11 a run, against 20 to 125), and a
slow frame is 20 to 40 ms where the budget is 16.7 ms. The percentage is higher, because the cheap in-between frames
that used to dilute it are gone. Which of the two figures to hold the redesign to is the owner's call; the
verification report has every run.

Memory on Home: 59 MB PSS after a session (159 MB for the old Home at the same point).

The first passes after any install read worse (the app's code is not compiled yet), and so does the first start: the
splash is followed by most of a second of plain background before Home draws. A baseline profile would help; it is
not in this build.

The design keeps costs down in a few deliberate ways: no blur and no full-screen gradient drawn per frame (the
background is a small picture drawn once and stretched), a focus glow made of four plain outlines rather than a blur,
no scale or movement on focus, lists that draw only the rows that show, and Live TV's preview picture asked for only
once focus has rested on a channel.
