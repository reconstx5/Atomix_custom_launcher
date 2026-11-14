# What the TV reports about its inputs

Read on 14 November 2026 from the VEON 4K Android TV (`VEON/MTCMSD6886EU_VEON/bangbae:11/RTO8.260427.001`,
Android TV 11) with Atomix TV 0.1.0 at commit `9a1f19d` installed. Nothing on the TV was changed: only
`dumpsys tv_input`, `dumpsys hdmi_control` and `logcat -d` were run, twice, at 13:21:34 and 13:31:35. The two
readings are identical in every line quoted here. The raw dumps are in
`.superpowers/sdd/2026-11-14-atomix-tv-redesign/` (`tv_input-1.txt`, `hdmi_control-1.txt`, `app-inputs-1.txt`, and
the `-2` pair).

This answers the five open questions of the design spec's §8 as far as reading can. Three of them still need the
owner at the TV; those are marked.

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

## 1. What an empty port reports

- **What the TV reports.** HDMI 1, 2 and 4 each report `state=1` (connected, standby) to an app, and
  `cable_connection_status=1` (cable connected) to the system. No CEC device is listed on any of them. The only
  "disconnected" reading on the TV is the composite input (`state=2`, `cable_connection_status=2`).
- **How it was read.** The app's log and `dumpsys tv_input`, both readings.
- **Known / not known.** Known: the TV can report "disconnected" (it does for composite). **Not known: whether an
  empty HDMI socket reports it.** Either HDMI 1, 2 and 4 all have something plugged in that is asleep and does not
  speak CEC, or an empty HDMI socket reads the same as a sleeping device. The readings cannot tell these apart.
  *Not checked: needs the owner* (say what is in each socket; unplug one cable and read again).

## 2. Whether an ordinary app can read a device's name

- **What the TV reports.** Yes, for one device: the app's own listing has a child input,
  `HDMI310004`, whose `parentId` is HDMI 3 (`HW6`) and whose `label` is `PlayStation 5`. Every `customLabel` is null.
  The AV receiver on the same port (`RX-V483` in the CEC list) does not appear to the app at all.
- **How it was read.** The app's log (no special permission), against the CEC list in `dumpsys hdmi_control`.
- **Known / not known.** Known: a CEC playback device is given to apps as a child input carrying its CEC name, tied
  to the TV port it arrives through. Known: an audio system (the receiver) is not. Not known: whether the name is
  still there when the device is unplugged or has been off for a long time; whether other kinds of device (a set-top
  box, a streaming stick, a PC) appear the same way. A PC and most cheap sticks do not speak CEC and would have no
  name.

## 3. Devices with CEC switched off, and devices behind a soundbar or switch

- **What the TV reports.** The PlayStation 5 is behind the receiver: its physical address is `0x3100`, one hop below
  the receiver's `0x3000`, and both are on port 3. It is still listed, by name, as a child of HDMI 3.
- **How it was read.** The physical addresses in `dumpsys hdmi_control`; the child input in the app's log.
- **Known / not known.** Known: a device behind a CEC receiver is named and is attributed to the TV socket the
  receiver is plugged into, so "PlayStation 5, HDMI 3" is what the launcher can say. Known: the receiver itself has
  no name the app can read, so a port with only a receiver on it would be unnamed. Not known: what a device with CEC
  switched off looks like. None has been identified; if HDMI 1, 2 or 4 holds one, it reads as in section 1.
  *Not checked: needs the owner* (what is plugged into HDMI 1, 2 and 4).

## 4. Which of HW4 to HW7 is which printed port

- **What the TV reports.** `HW4` is `hdmi_port=1` (address `0x1000`, the only port with ARC), `HW5` is port 2, `HW6`
  is port 3, `HW7` is port 4. The TV's own labels follow the same order: HW4 "HDMI 1" to HW7 "HDMI 4".
- **How it was read.** `TvInputHardwareInfo` and `mPortInfo` in the two dumps; the labels in the app's log.
- **Known / not known.** Known: the TV's numbering, and that HDMI 1 is the ARC socket. **Not known: whether the
  numbers printed beside the sockets match it.** *Not checked: needs the owner* (read the printed labels; the socket
  marked ARC should be the one the TV calls HDMI 1, and the receiver's cable should be in the one it calls HDMI 3).

## 5. Whether "on" can be told from "plugged in but asleep"

- **What the TV reports.** To an app, HDMI 3 and the PlayStation 5 behind it both read `state=0`, "connected", not
  standby. To the system, both devices on that port read `power_status: 1`, standby. So at the moment of both
  readings the app was told "connected" about a console that CEC said was asleep. The other three ports read
  `state=1`, standby, with nothing known behind them.
- **How it was read.** `TvInputManager` state from the app's log against CEC `power_status` from
  `dumpsys hdmi_control`, twice, ten minutes apart, with no change.
- **Known / not known.** Known: `state=0` does not mean the device is on. Known: the CEC power status, which does
  say, is not readable by an ordinary app (`HdmiControlManager` is system-only). Not known: whether the state an app
  sees moves at all when a device is switched on or off, and how quickly. *Not checked: needs the owner* (switch one
  device off at its own button, then on, and read again each time).

## Which of the four drawn states this TV can tell apart

Stating only what the readings show:

| Drawn state | Can the launcher tell? |
| --- | --- |
| On and named | **Named: yes**, for a CEC device the TV lists as a child input (the PlayStation 5 on HDMI 3). **On: not shown.** The one named device read "connected" while CEC said it was in standby. |
| Connected but unnamed | **Not shown.** HDMI 1, 2 and 4 all read "connected, standby" with no name. Whether that means "something is plugged in" depends on section 1. |
| Not connected | **Not shown for HDMI.** No HDMI port has read "disconnected". The composite input does. |
| Checking | Not a thing the TV reports: it is the launcher's own state while it reads, and it can always show it. |

On this evidence the inputs phase can promise a name where the TV gives one, and nothing yet about on, asleep or
empty. If the owner's checks below show that an unplugged cable reads "disconnected", "not connected" becomes a
second real state. If they show that the app's state follows a device being switched on, "on" becomes a third.
Until then the design's own rule applies: fewer states, never a guess shown as fact.

## Needs the owner

About five minutes at the TV, then the two `dumpsys` readings are taken again after each step:

1. Read the labels printed beside the four HDMI sockets and say what is plugged into each (sections 1, 3 and 4).
2. Unplug one named cable for a minute (section 1: does that port then read "disconnected"?).
3. Switch one device off at its own button, then on again (section 5: does the state an app sees follow?).

Until that is done, sections 1, 4 and 5 stand as "not checked: needs the owner" where marked.
