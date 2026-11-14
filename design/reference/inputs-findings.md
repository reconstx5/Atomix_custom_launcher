# What the TV reports about its inputs

Read on 14 November 2026 from the VEON 4K Android TV (`VEON/MTCMSD6886EU_VEON/bangbae:11/RTO8.260427.001`,
Android TV 11) with Atomix TV 0.1.0 at commit `9a1f19d` installed. Nothing on the TV was changed: only
`dumpsys tv_input`, `dumpsys hdmi_control` and `logcat -d` were run, twice, at 13:21:34 and 13:31:35. The two
readings are identical in every line quoted here. The raw dumps are in
`.superpowers/sdd/2026-11-14-atomix-tv-redesign/` (`tv_input-1.txt`, `hdmi_control-1.txt`, `app-inputs-1.txt`, and
the `-2` pair).

The same evening, 21:34 to 21:45, the readings were taken again with the owner at the TV (Atomix TV at commit
`1b519df`): the receiver's cable was pulled out at each end in turn and put back, and the PlayStation 5 was switched
on and then off. A watcher read the two dumps about every four seconds and wrote a line each time something changed.
That part is under "With the owner at the TV" below; its raw dumps are the `-3-baseline` to `-7-end` pairs and
`inputs-watch.log`, `inputs-watch-2.log` in the same folder.

The owner then sent photos of the receiver's sockets and of the TV's own. With those, the five open questions of the
design spec's §8 are answered; what stays unknown is listed under each.

## The readings

**What an ordinary app sees** (the launcher's own log, tag `AtomixInputs`, written when the Inputs page opens; it uses
only `TvInputManager`, with no special permission):

```
…/hdmi.HDMIInputService/HW4        | type=1007 | parentId=null | state=1 | label=HDMI 1 | customLabel=null | passthrough=true
…/hdmi.HDMIInputService/HW5        | type=1007 | parentId=null | state=1 | label=HDMI 2 | customLabel=null | passthrough=true
…/hdmi.HDMIInputService/HW6        | type=1007 | parentId=null | state=0 | label=HDMI 3 | customLabel=null | passthrough=true
…/hdmi.HDMIInputService/HW7        | type=1007 | parentId=null | state=1 | label=HDMI 4 | customLabel=null | passthrough=true
…/hdmi.HDMIInputService/HDMI310004 | type=1007 | parentId=…/hdmi.HDMIInputService/HW6 | state=0 | label=PlayStation 5 | customLabel=null | passthrough=true
…/composite.CompositeInputService/HW1 | type=1001 | parentId=null | state=2 | label=Composite | customLabel=null | passthrough=true
…/tuner.TunerInputService/HW8      | type=0    | parentId=null | state=0 | label=Tuner(ATV) | customLabel=null | passthrough=false
…/tuner.TunerInputService/HW0      | type=0    | parentId=null | state=0 | label=Tuner(DTV) | customLabel=null | passthrough=false
```

`state` is `TvInputManager`'s: 0 connected, 1 connected but in standby, 2 disconnected.

**What only the system sees** (`dumpsys`, read over ADB; an ordinary app cannot read these):

```
TvInputHardwareInfo {id=4, type=9, … hdmi_port=1, cable_connection_status=1}
TvInputHardwareInfo {id=5, type=9, … hdmi_port=2, cable_connection_status=1}
TvInputHardwareInfo {id=6, type=9, … hdmi_port=3, cable_connection_status=1}
TvInputHardwareInfo {id=7, type=9, … hdmi_port=4, cable_connection_status=1}
TvInputHardwareInfo {id=1, type=3, … cable_connection_status=2}                      (composite)

port_id: 1, type: HDMI_IN, address: 0x1000, cec: true, arc: true,  mhl: false
port_id: 2, type: HDMI_IN, address: 0x2000, cec: true, arc: false, mhl: false
port_id: 3, type: HDMI_IN, address: 0x3000, cec: true, arc: false, mhl: false
port_id: 4, type: HDMI_IN, address: 0x4000, cec: true, arc: false, mhl: false

CEC: logical_address: 0x04 device_type: 4 vendor_id: 524358 display_name: PlayStation 5 power_status: 1 physical_address: 0x3100 port_id: 3
CEC: logical_address: 0x05 device_type: 5 vendor_id: 41182  display_name: RX-V483        power_status: 1 physical_address: 0x3000 port_id: 3
```

`cable_connection_status` is 1 connected, 2 disconnected. CEC `power_status` is 0 on, 1 standby.

## With the owner at the TV

What the TV calls HDMI 3 is the socket the AV receiver's cable is in; the PlayStation 5 is behind the receiver. Times
are the PC's clock. "App" is what an ordinary app is told (`TvInputManager` state: 0 connected, 1 connected but in
standby). "CEC" is the system-only power status (0 on, 1 standby, -1 not yet known). The TV's own
`cable_connection_status` read 1, connected, on all four HDMI sockets in every line, so it is left out.

| Time | What was done | HDMI 3 (app) | PlayStation 5 (app) | CEC |
| --- | --- | --- | --- | --- |
| 21:34:26 | Nothing yet (baseline) | 0 | 0 | PlayStation 5 standby, receiver standby |
| 21:35:25 | Cable out at the receiver's end | 1 | not listed | no devices |
| 21:37:16 | Cable back in | 0 | not listed | no devices |
| 21:37:19 | | 0 | 0 | two devices, named "Playback_1" and "AudioSystem", power not known |
| 21:37:21 | | 0 | 0 | named PlayStation 5 and RX-V483, power not known |
| 21:37:26 | | 0 | 1 | both standby |
| 21:38:41 | Cable out at the TV's end | 1 | 1 | both still listed |
| 21:38:43 | | 1 | not listed | no devices |
| 21:39:54 | Cable back in | 0 | not listed | no devices |
| 21:39:58 | | 0 | 0 | both named, power not known |
| 21:40:26 | | 0 | 1 | both standby |
| 21:40:57 | PlayStation 5 switched on | 0 | 0 | PlayStation 5 on and the active source; receiver standby |
| 21:43:54 | PlayStation 5 switched off | 0 | 0 | PlayStation 5 still "on"; no active source |
| 21:44:26 | | 0 | 1 | PlayStation 5 standby |

HDMI 1, 2 and 4 read 1 to an app in every line. The moment each thing was done is the owner's, not timed; the times
are when the watcher first read the change.

## 1. What an empty port reports

- **What the TV reports.** HDMI 1, 2 and 4 each report `state=1` (connected, standby) to an app, and
  `cable_connection_status=1` (cable connected) to the system. No CEC device is listed on any of them. The only
  "disconnected" reading on the TV is the composite input (`state=2`, `cable_connection_status=2`).
- **How it was read.** The app's log and `dumpsys tv_input`, both readings.
- **Known / not known.** Known: the TV can report "disconnected" (it does for composite), but **it never does for
  HDMI.** With the receiver's cable out, HDMI 3 read `state=1` to an app and `cable_connection_status=1` to the
  system, exactly what HDMI 1, 2 and 4 read all day. It read the same whether the cable was out at the TV's end (the
  socket empty) or at the receiver's end (a cable in the socket with nothing on the far end). So on this TV:
  - `state=1` on an HDMI socket means nothing live is on it. `TvInputManager` calls that value "connected, standby";
    here it is what an empty socket reads.
  - `state=0` on an HDMI socket means something live is on it. The receiver in standby is enough: HDMI 3 went to 0
    the moment the cable went back, before any device was named, and stayed 0 through on and off.
  - `cable_connection_status` says nothing; it is 1 on every HDMI socket whatever is done.

  Not known: what a device that is fully off (no standby power), or one that does not speak CEC, reads as. None was
  to hand. Known (the owner's photos of the TV, 14 November): HDMI 1, 2 and 4 are empty. So their `state=1` all day
  was an empty socket's reading, not something asleep on each.

## 2. Whether an ordinary app can read a device's name

- **What the TV reports.** Yes, for one device: the app's own listing has a child input,
  `HDMI310004`, whose `parentId` is HDMI 3 (`HW6`) and whose `label` is `PlayStation 5`. Every `customLabel` is null.
  The AV receiver on the same port (`RX-V483` in the CEC list) does not appear to the app at all.
- **How it was read.** The app's log (no special permission), against the CEC list in `dumpsys hdmi_control`.
- **Known / not known.** Known: a CEC playback device is given to apps as a child input carrying its CEC name, tied
  to the TV port it arrives through. Known: an audio system (the receiver) is not. Known: the child input goes
  within about two seconds of the cable coming out, and is back two to three seconds after it goes in, both times
  with the console asleep. For the first second or two CEC lists the devices under stand-in names ("Playback_1",
  "AudioSystem") before their own names arrive; whether an app is ever handed a stand-in name was not read (the
  watcher read names from CEC, not from the app's listing). Not known: whether other kinds of device (a set-top box,
  a streaming stick, a PC) appear the same way. A PC and most cheap sticks do not speak CEC and would have no name.

## 3. Devices with CEC switched off, and devices behind a soundbar or switch

- **What the TV reports.** The PlayStation 5 is behind the receiver: its physical address is `0x3100`, one hop below
  the receiver's `0x3000`, and both are on port 3. It is still listed, by name, as a child of HDMI 3.
- **How it was read.** The physical addresses in `dumpsys hdmi_control`; the child input in the app's log.
- **The receiver's own sockets** (the owner's photo of the back of the RX-V483, 14 November). "HDMI OUT (HDCP2.2)
  ARC" holds the cable that goes to the TV. Of its four HDMI inputs, 1 holds a cable and 2, 3 and 4 are empty. That
  fits the address: `0x3100` is input 1 of whatever is on the TV's port 3, so the cable in input 1 is the
  PlayStation 5's. A device in the receiver's input 2 would be `0x3200`. The child input's id, `HDMI310004`, appears
  to carry the same address (`3100`) and the device's CEC logical address (`04`); that is read off one id, not
  documented anywhere.
- **Known / not known.** Known: a device behind a CEC receiver is named and is attributed to the TV socket the
  receiver is plugged into, so "PlayStation 5, HDMI 3" is what the launcher can say. Known: the receiver itself has
  no name the app can read, so a port with only a receiver on it would be unnamed. Not known: what a device with CEC
  switched off looks like. There is none on this TV to read: HDMI 1, 2 and 4 are empty (the owner's photos).

## 4. Which of HW4 to HW7 is which printed port

- **What the TV reports.** `HW4` is `hdmi_port=1` (address `0x1000`, the only port with ARC), `HW5` is port 2, `HW6`
  is port 3, `HW7` is port 4. The TV's own labels follow the same order: HW4 "HDMI 1" to HW7 "HDMI 4".
- **How it was read.** `TvInputHardwareInfo` and `mPortInfo` in the two dumps; the labels in the app's log.
- **What is printed on the TV** (the owner's photos, 14 November).
  - On the side: one HDMI socket marked "HDMI1(ARC)", empty, beside a USB socket with a memory stick in it.
  - Underneath, left to right: "OPTICAL", two "USB", "HDMI4" (empty), a socket holding the receiver's cable,
    "HDMI2" (empty), "AV IN".
  - The label of the socket with the cable in it is hidden behind the plug. It sits between HDMI4 and HDMI2, and it
    is the only one left, so it is HDMI3. That is by elimination; the printed word itself was not read.
- **Known / not known.** Known: the printed numbers match the TV's own. `HW4` is the side socket "HDMI1(ARC)", `HW5`
  is "HDMI2", `HW6` is the one the receiver is in (HDMI3), `HW7` is "HDMI4". So the launcher can use the TV's labels
  as they are. Note the order underneath is 4, 3, 2 from the left, and HDMI 1 is not with them.
- **One thing the readings and the photos put side by side.** The receiver's cable leaves its "HDMI OUT … ARC"
  socket and arrives at the TV's HDMI 3, which the TV reports as `arc: false`. The TV's only ARC socket, HDMI 1 on
  the side, is empty. As cabled, the TV's own sound (apps, Live TV) cannot return to the receiver over HDMI. Whether
  an optical cable carries it instead was not seen. Not a launcher matter. If the cable is ever moved to HDMI 1, the
  PlayStation 5 will be listed under HDMI 1 instead of HDMI 3, with a new address (`0x1100`).

## 5. Whether "on" can be told from "plugged in but asleep"

- **What the TV reports.** In the afternoon's readings, to an app, HDMI 3 and the PlayStation 5 behind it both read
  `state=0`, "connected", not
  standby. To the system, both devices on that port read `power_status: 1`, standby. So at the moment of both
  readings the app was told "connected" about a console that CEC said was asleep. The other three ports read
  `state=1`, standby, with nothing known behind them.
- **How it was read.** `TvInputManager` state from the app's log against CEC `power_status` from
  `dumpsys hdmi_control`, twice, ten minutes apart, with no change.
- **Known / not known.** With the owner at the TV, the PlayStation 5's own state as an app sees it did follow the
  console:
  - asleep, it read 1 (standby), once CEC knew it was in standby;
  - switched on, it read 0 in the same reading in which CEC said "on" (21:40:57);
  - switched off, it read 1 again in the same reading in which CEC said "standby" (21:44:26). That was 32 seconds
    after the TV lost its active source (21:43:54), which is the first sign the console was going off.

  Known: the socket's own state (HDMI 3) does not move with on and off; only the named device's does. Known: the
  device's state is not always right. It read 0 for a console CEC said was asleep three times: twice this afternoon,
  ten minutes apart, and again at 21:34 this evening before anything was touched. And after the cable goes in it
  reads 0 until the power is known, which took 7 seconds the first time and 30 the second. So 1 has always meant
  asleep, and 0 has meant "on" or "not yet asked". Known: the CEC power status itself is not readable by an ordinary
  app (`HdmiControlManager` is system-only). Not known: what leaves the state at 0 for a sleeping console. One guess,
  not tested: the TV waking from its own sleep, which it had done before the evening's first reading.

## Which of the four drawn states this TV can tell apart

Stating only what the readings show:

| Drawn state | Can the launcher tell? |
| --- | --- |
| On and named | **Named: yes**, for a CEC device the TV lists as a child input (the PlayStation 5 on HDMI 3). **On: mostly.** The device's state went to 0 when it was switched on and to 1 about half a minute after it was switched off. But 0 has also been read for a sleeping console (this afternoon, at the start of the evening's check, and for up to half a minute after plugging in). "Asleep" (1) has never been wrong. |
| Connected but unnamed | **Yes.** A socket reads 0 when something live is on it, with or without a named device behind it: HDMI 3 read 0 with the receiver alone answering, before the PlayStation 5 was listed. |
| Not connected | **Yes, as "nothing live here".** A socket reads 1 when it is empty and when its cable has nothing on the far end. It cannot tell those two apart, and the TV never says "disconnected" for HDMI. Not tested: a device that is completely off. |
| Checking | Not a thing the TV reports: it is the launcher's own state while it reads, and it can always show it. |

So the inputs phase can promise three things from what an ordinary app is told: a name where the TV gives one;
"something is on this socket" against "nothing is"; and "asleep" for a named device. "On" is the weak one: by the
design's own rule (fewer states, never a guess shown as fact) a named device reading 0 is safer drawn as "connected"
than as "on", unless a later check shows what made it read 0 while asleep.

## Needs the owner

Nothing more. Done on 14 November: one cable unplugged at each end, one device switched on and off, and photos of
the receiver's sockets and the TV's.

Left unknown because there was nothing to test it with: a device that is completely off, a device that does not
speak CEC, and what makes a sleeping console read "connected" (section 5).
