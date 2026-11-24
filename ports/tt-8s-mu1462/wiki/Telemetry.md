# OpenHaldex telemetry

[Home](Home.md)

Live car data from the OpenHaldex ESP32, sent over the MMI Wi-Fi hotspot and shown as text on
the cockpit map area, like a second AltScreen page.

## Goal (user, 2026-11-22)

- **Autocross view instead of the map** (user, 2026-11-23): when it is on, no map shows and the
  information fills the cockpit, keeping clear of the stock dials. A **double press** on the scroll
  wheel switches it on and off.
- **Classic (large) view:** the rev counter and shift light big and in the centre.
- **Sport (small) view:** only gear and engine information for now.
- **First milestone: the link.** OpenHaldex connects to the MMI and basic data arrives. The UI
  comes after that.

## State

| Step | State |
|---|---|
| How the MMI's Wi-Fi works | Found in the firmware (below) |
| Head-unit test tool `ttlink` | Built for QNX; tested on the PC against the laptop sender: 81 datagrams, 0 lost, acks back, wrong key rejected. Builds on macOS too |
| Sender in OpenHaldex-S3 (`mmi_link.cpp`) | OpenHaldex branch `one-board-ble`, v1.1.15 (BLE + MMI link on one board, ~86 KB RAM free). Bench-tested on the Mac over a Mac hotspot against OpenHaldex's `mmi_sim.py`: 20 Hz, 0 lost. v1.1.15 fixed a bug where the link never counted as up, so older builds stayed at 1 probe a second |
| Presets from the MMI | Works on the Mac bench (apply preset 2: ok, channel 22 follows). `ttlink -a` tested on the PC against `gauge-send.py --presets`; not yet against the real board |
| MMI hotspot gateway | Without a mobile data connection the hotspot hands out no gateway (below). OpenHaldex v1.1.16 falls back to (own address & netmask) \| 1 = `10.173.189.1` and accepts replies only from there. Builds and runs; the no-gateway path is first exercised in the car |
| Link test in the car | Not done |
| Autocross view on the cockpit | Built into tt-zoom `tele1` (v63 + the view, 2026-11-23); compiles against the stock HMI, library check clean. Receiver tested on the PC against `gauge-send.py --presets`: 20 Hz, 0 lost, catalog parsed, apply preset ok. Not run in the car yet. Wheel double press not traced; on/off by switch file for now |

## Where the data comes from

OpenHaldex-S3 already decodes the chassis CAN with its built-in VW DBC tables (the `received_*`
values its RaceChrono feed uses). The MMI link reads those same values, so nothing new is decoded
on the ESP32.

One board does everything (OpenHaldex branch `one-board-ble`, user decision 2026-11-22):

- **BLE:** RaceChrono (DIY BLE) and the OpenHaldex iOS app, which replaces the web page.
- **Wi-Fi:** only the client link to the MMI hotspot. OpenHaldex's own access point, web page and
  RC3 server are compiled out (`OH_ENABLE_WEB=0`). The hotspot SSID and password are set in the
  app; without them Wi-Fi stays off.
- Measured on the T-2CAN: about 96 KB internal RAM free, never below 93 KB over 13 minutes with
  RaceChrono and the app both connected. With the AP and web page still on it fell to 4 KB.

## Which side is the access point

The MMI is the access point and OpenHaldex joins it as a client. The stock firmware is built
for that:

- The Wi-Fi interface is `uap0`, a Marvell **micro-AP**. `uaputl sta_list` on the head unit lists
  connected clients. A DHCP server hands out addresses.
- The stock firewall (`/mnt/system/etc/pf.conf`, WLAN section) already passes from hotspot
  clients to the head unit:
  - ICMP, so ping works;
  - DNS;
  - TCP 49152 and **UDP 49153-49162**;
  - EXLAP TCP 25010 and UDP 28500.

  Everything else to the head unit is blocked (`block in quick on $wlan_if from any to ($wlan_if)`).
  Outbound is open (`pass out quick on $wlan_if all keep state`), so acks and preset commands to
  the ESP32's port go out. `ttlink` uses UDP **49160**, so **no firewall change is needed**.
- The head unit's hotspot address is fixed: `uap0` is **10.173.189.1/24** (`ifconfig uap0
  10.173.189.1` in the startup script). dnsmasq leases 10.173.189.10-99.
- **No gateway without internet.** At boot the startup script writes `3` (DHCP router option,
  empty) to `/tmp/dhcp.opts`, so dnsmasq gives clients **no default gateway**. A script in the app
  image removes that file only when the MMI has an internet connection (or the flag file
  `/eso/hmi/uselocalnet` exists). Without a data plan the ESP32's gateway is `0.0.0.0`.
  OpenHaldex therefore falls back to 10.173.189.1 when it has no gateway (v1.1.16, "No gateway"
  in its `docs/MMI_LINK.md`). Touching `uselocalnet` on the MMI is not the plan: it is persistent and its
  other effects are unknown.
- The reverse (ESP32 as access point, MMI as client) would need the MMI's client mode. The
  firmware contains `wpa_supplicant`, but nothing shows the MMI using a Wi-Fi client mode, so this
  is not the plan.
- The ESP32 sends to its gateway address, which is the MMI, so no IP address is configured. With
  no gateway (no internet, above) it uses 10.173.189.1. It accepts acks and commands only from that
  address; `ttlink` answers from `uap0`, which is that address.
- Until the head unit answers, OpenHaldex sends one probe a second; with acks it streams at
  20 Hz. A home router on the client side never gets the full stream.
- With OpenHaldex's own AP compiled out, the hotspot subnet no longer matters.

## Link protocol

All fields big-endian.

**TTG1, ESP32 to head unit, 20 Hz.** Header, then N entries of 6 bytes:

| Offset | Size | Field |
|---|---|---|
| 0 | 4 | `TTG1` |
| 4 | 4 | Key, u32 (`-k` on ttlink) |
| 8 | 2 | Sequence number, u16 |
| 10 | 1 | Flags |
| 11 | 1 | Entry count N |
| 12 + 6i | 2 | Channel id, u16 |
| 14 + 6i | 4 | Value × 1000, s32, SI units |

Only fresh values are sent.

**TTA1, head unit to ESP32, 20 bytes:** `TTA1`, the echoed sequence number, received count, lost
count, head-unit uptime. The ESP32 uses it for round-trip time and loss.

Channels:

| Id | Value | Id | Value |
|---|---|---|---|
| 1 | rpm | 11 | Haldex actual % |
| 2 | speed km/h | 12 | OpenHaldex mode |
| 3 | pedal % | 13 | gear |
| 4 | torque Nm | 14 | lateral g |
| 5 | coolant °C | 15 | longitudinal g |
| 6 | oil °C | 16 | DSG oil °C |
| 7 | intake °C | 17 | battery V |
| 8 | boost kPa | 60 | ESP32 uptime (sent automatically) |
| 9 | lambda | 61 | RSSI dBm (sent automatically) |
| 10 | Haldex request % | 62 | datagrams sent (sent automatically) |

Also 18 oil pressure bar, 19 Haldex clutch °C, 20 steering angle ° (**right positive**, left
negative), 21 brake pressure bar, 22 active preset id (255 = none).
OpenHaldex does not send 9, 14, 15 or 17 (no source on the bus yet). Full source mapping:
`docs/MMI_LINK.md` in OpenHaldex-S3.

They are defined in `ttlink.c`, `gauge-send.py` and OpenHaldex `mmi_link.cpp`; change all three together.

## Presets

The MMI switches OpenHaldex between **presets**, not raw modes. A preset is a named bundle kept on
OpenHaldex: a mode, optionally a map file, and optionally its gates (`docs/PRESETS.md` in
OpenHaldex-S3). The head unit hardcodes none of it; it shows whatever catalog OpenHaldex sends.
Applying is safe while driving: every preset's map is preloaded into PSRAM and swapped in at once,
with no file access. OpenHaldex refuses during calibration or CAN View capture.

Defaults until the user picks: 1 Street (stock), 2 Autocross (aggressive map), 3 Launch (50:50),
4 Wet (conservative map), 5 FWD. At most 8.

Same socket and key as TTG1, big-endian:

| Message | Direction | Content |
|---|---|---|
| `TTP1` | OpenHaldex to MMI, on link-up, on change, every 5 s | catalog version u16, count, then per preset: id, mode, name length, name (≤ 16 bytes) |
| `TTC1` | MMI to OpenHaldex, to the TTG1 source address | command seq u16, command (1 = apply preset), preset id, catalog version u16. Reusing the seq is a retry and is not applied twice |
| `TTR1` | OpenHaldex to MMI, at once | echoed seq, result, active id, catalog version. Results: 0 ok, 1 catalog version mismatch, 2 unknown preset, 3 calibration running, 4 CAN View capture active, 5 preset map missing, 255 unknown command |

Channel 22 in the TTG1 stream is the real confirmation; a lost TTR1 does no harm. On result 1 the
head unit waits for the next catalog (at most 5 s).

`ttlink` speaks this too: it prints the catalog, `-a 2` applies preset 2 once the catalog is in,
and typing a preset id and Enter while it runs applies that one. It sends each command up to
three times, 500 ms apart, with the same seq. Tested on the PC against `gauge-send.py --presets`
(apply ok, unknown preset, refusal "calibration running", no catalog means no command); the retry
path and the real firmware are not tested yet.

## Files

| File | What |
|---|---|
| `gauges/headunit/ttlink.c` | Head-unit tool: listens on UDP 49160 and logs one summary per second, with `-v` one line per datagram. Acks each datagram, prints the preset catalog, `-a` applies a preset. Log in `/tmp/ttlink.log`. No jar and no HMI restart |
| OpenHaldex-S3 `src/functions/telemetry/mmi_link.cpp` | Sender: runs from `loop()`, no task of its own; reads the decoded CAN values, sends, reads acks; stats in the app's Wi-Fi page (`/api/network`, `mmi`) |
| OpenHaldex-S3 `docs/PRESETS.md`, `scripts/mmi_sim.py` | Preset spec; Python fake head unit for bench tests |
| `gauges/scripts/gauge-send.py` | Laptop stand-in for OpenHaldex, with the same output; `--presets` also plays the preset side |
| `hmi/src/local/tt/TeleStrip.java`, `gauges/tools/StripPreview.java` | tele8: the rev strip painted whole (track and lit pictures); PC preview of the clip cut |

Build `ttlink` (WSL, toolchain image from [Build and test](Build-and-test.md)):

```bash
wsl -d Ubuntu-24.04 -u root -- sh -c 'cd /mnt/c/.../gauges/headunit && docker run --rm -v "$PWD:/w" -w /w mhi2-qnx65-armv7-public:4.9 arm-unknown-nto-qnx6.5.0eabi-gcc -O2 -std=gnu99 -mfloat-abi=softfp ttlink.c -lsocket -o out/ttlink'
```

## Autocross view on the head unit (tt-zoom tele19)

**Haldex page** (user 2026-11-24, after the OpenHaldex iPhone app's drivetrain card,
`ios/OpenHaldex/HaldexVisual.swift`): the car from above with an x-ray drivetrain (front axle ice,
prop shaft and rear lit cyan in 5 steps with the actual lock, torque dashes travelling rearward,
faster with more lock, the coupling filling up, front wheels turned with the steering at 15:1), a
270-degree ring gauge for the actual lock with the target as a pink dot, and TARGET, STEERING
(channel 20) and CLUTCH (channel 19, bar to 120 °C, pink from 90). "STOCK CONTROL" when the mode is
STOCK, "NOT CONNECTED" without link. Classic: car sideways (nose right) in the panel, gauge and stats
right of it, the gear small top right; the strip, shift lights and corners stay. Sport: car upright
on the left, gauge and stats on the right, under the shift lights; the warning frame stays.
Switching: with the pop-up closed, roll (up/down) changes the page; `sh tt.sh tele ui page`; the
long press still opens the pop-up on either page. Simulator: Up/Down, or `--ui page`.

### Earlier: tele18


User 2026-11-24: the Haldex control as a pop-up, not inside the panel. tele18:
- **Pop-up card** over the dimmed view (Classic centred on the gear, Sport over the panel): three
  rows PRESET, MODE, LOCK. Roll moves between rows; press edits the row (roll changes, press sends,
  back cancels); back closes; 20 s untouched closes. Opened by a **long press** of the toggle key
  (the double press still switches the view), or `sh tt.sh tele ui open`. Same commands and
  confirmation as tele17 below.
- **Screenshot**: `sh tt.sh shot` makes the head unit write a PNG of the cockpit itself
  (`HMIService.takeScreenshot(0, path)`, the call its own key-combo screenshot uses) to
  /tmp/tt-shot-HHMMSS.png; copy it off with scp. For exact stock positions and sharp sim backgrounds.
- Cleanup: dead helpers removed (old boost arc and cover, hxBar, linkText, old strip colours); a
  layout can no longer write past 400 slots (extra slots dropped, logged once).

### Earlier: tele17


**Haldex control page** (user 2026-11-24: presets, modes and a lock slider from the cockpit).
TeleUi: one cursor over the presets, then MODE, then LOCK; press on a preset applies it, on MODE
or LOCK it starts editing (roll changes, press sends, back cancels); closes after 20 s idle. Every
command is sent once and confirmed by the stream (channel 22 preset, 12 mode, 12 = 11 and 23 for
the lock); TTR1 refusals are shown in words, "NO ANSWER" after 3 s. Classic: presets left of the gear,
mode and slider right of it, status in the temperature row. Sport: in the temperature column.
Board contract (OpenHaldex v1.1.21, docs/PRESETS.md): TTC1 cmd 2 SET_MODE (mode 0..10, channel 12
numbering), cmd 3 SET_LOCK (0..100 %, enters MANUAL = 11, gated like the fixed lock modes, RAM
only: a reboot restores MAP), channel 23 = MANUAL setpoint (255 otherwise). Lock % is the lock
target: 100 = fully locked (like 50:50), 40 = 60:40.
- `sh tt.sh tele ui open|up|down|press|back|close` drives it from SSH.
- Steering wheel: `sh tt.sh tele ui map "CODE EVENT ACTION" ...` (EVENT pressed|turned|long; ACTION
  open|up|down|press|back), codes from the key trace; `tele ui savemap` keeps it on flash.

**Theme on the car**: the simulator exports the backgrounds (`java -cp out local.tt.Theme --export
DIR colour` -> theme-classic.rgba, theme-sport.rgba, 1440 x 540 RGBA, 3.1 MB each). Copy them to
the car's /tmp, `sh tt.sh tele theme install`, `sh tt.sh tele theme on` (or `boot`). TeleView reads
a view's file once and uploads it as slot 0's picture; the gear notch turns see-through. The log
shows the Java heap after loading (`grep "tele theme" /tmp/tt-rgd.log`).

**Glide** (TeleGlide, shared with the simulator): `sh tt.sh tele glide on|off|boot`; the moving
channels go along the line between the last two samples, one sample (about 50 ms) late.

**Selftest**: `sh tt.sh tele selftest` walks through the parked test (view and build time,
switching, redline flash, sound list and tones, key trace, strip probe, shift tone), waits for
Enter between steps and writes everything to /tmp/tt-selftest-HHMMSS.txt.

### Earlier: tele13 to tele16


Sport view (user 2026-11-24): the panel outline (TeleLayout SP_X/SP_Y) traces what the cluster lets
through: the frame's diagonal, under the stock status line, around the big rev counter (8 px outside
its ring), along the bottom; the theme's housing uses the same outline. Content 20 px further left;
oil temperature dropped (the stock Sport view shows it), Haldex and boost bars 10 px, gear 170 px,
temperatures 36 px on 50 px rows. From the shift point a warning flashes with the shift lights: a
thick red frame with a glow along the outline, a red tint over the panel and a hazard band along its
top (fixed shapes, only shown or hidden). tele14: the top band is a shift-light row instead of hazard
stripes (ten glass segments lighting left to right over the last 1200 rpm, mint, cobalt, red, all
flashing red from the shift point); the theme puts the white-haired chibi in the panel's top-right. tele15: Sport content 10-18 px lower (the top
rows were against the frame's diagonal and top edge; the lower part had room). tele16: Sport shift lights 26 px tall (were
18), content another 6-10 px lower.

### Earlier: tele12


Steering-wheel keys (2026-11-24): the HMI queues key events on its event dispatcher, which accepts
filters (`HMIService.getEventDispatcherAdmin().addEventFilter`, `BaseJobFilter`; the stock
driving-school display uses one). TeleKeys is such a filter: every event goes on to the stock
handling first, unchanged; `KeyEvent`s (`getKeyCode`, `getID`: pressed 10401, released 10402, turned
10403, long 10405) are only looked at. Known codes include INC_MENU_ENTER 17, INC_ROLLER_LEFT 20,
INC_ROLLER_RIGHT 21, KEY_MENU 30, KEY_JOKER1 50; which ones the TT's wheel sends is to be traced.
- `sh tt.sh tele keys trace`, press the wheel buttons, `grep "tele key" /tmp/tt-rgd.log | tail -30`.
- `sh tt.sh tele keys toggle CODE` (RAM) / `keys save CODE` (flash): a double press of that key
  within 450 ms switches the view. The stock action of the key still happens.
- If the trace shows nothing for the wheel, its keys reach the HMI another way (the cluster's BAP
  MFL path, `de.audi.app.combi.bap.app.mfl`) and that is the next place to look.

### Earlier: tele11


User 2026-11-24: the view started slowly, and switching Sport -> Classic was slow (Classic -> Sport
fast). Cause: Classic is 273 slots with 161 quads (361 000 picture pixels, each tested at 16 sample
points) and Sport 29 slots with no quads; every view switch cleared all pictures and nodes, and every
new colour of a quad painted it again. tele11:
- Quad edges from the distance to the edges, one value per pixel (PC: 29x faster on Classic's
  shapes, mean difference 0.4/255), the coverage kept per shape and only recoloured per colour.
- Pictures keyed by shape and colour, not by view: nothing is cleared on a switch.
- Each view keeps its own node group (ViewNodes); a switch hides one and shows the other.
- At most 48 new pictures per HMI job: the first Classic build shows over a few frames instead of
  stalling the HMI thread.
- While nothing changes, the other view's pictures are painted in the background.
Also carries the tele10 shift tone probe (below). Measure on the car: `grep "tele job\|built" /tmp/tt-rgd.log`.

### Earlier: tele10


tele10 = the Classic layout reviewed in the simulator (fatter, lower strip; upright numbers and
ticks; everything on the centre line x 720; red line at 6800 with a hazard-striped red zone; flat
FL5-style boost bar; ice -> sakura -> red colours; strip and shift lights flash together and the
strip turns red from 6500) plus a **shift tone probe**.

Shift tone (2026-11-24): the head unit has no click or acknowledgement tone, but its own sound
effects go through the stock wave player service `de.audi.tghu.waveplayer.WavePlayer`
(`getSystemTonePlayer()` / `getRingTonePlayer()`, `playTone(PLAY_ONCE, id)`): the message chime and
the ringtone preview use it; underneath it is the native `DSIWavePlayer`, which requests its own
audio slot. Each caller gets its own player client, so the stock users are untouched. Reached
through `HMITerminal.getFramework().getBundleCxt()`. TeleBeep logs every state the player reports
with the milliseconds since the request (the delay is the open question; mixing with music does
not matter to the user).

Probe (parked, view on): `sh tt.sh tele sounds` lists the sound files on the head unit (tone ids
and formats); `sh tt.sh tele beep N` (or `beep N ring`, `beep default`, `beep stop`) plays tone N
once; `grep beep /tmp/tt-rgd.log | tail` shows "playing after X ms" and "finished after Y ms".
`sh tt.sh tele shiftbeep N` plays it each time rpm rises past 6500 (try with `tele demo on`).

### Earlier: tele9


User 2026-11-24: make the UI more delicate, after screenshots of the stock cockpit. Reference: Audi's
RS performance layout (a retrofit company's photo of a real cluster): a thin outlined rev band with
the numbers beside it, 7 and 8 in red; Power and Torque as thin rings with a small red tick and light
numerals; hairline dividers; red only as an accent. tele9 (also has the tele8 probe):
- Rev strip 18..34 px (was 30..54), numbers 1..8 beside it in light weight (white once passed, red
  from the red line). Painted strip (sim, probe): an outlined band, the fill inset inside a 1.5 px edge.
- Shift lights 8 px slanted segments, no halo. No glow behind the gear; the notch is a red outline
  whose dark fill flashes. Red line and notch edges 2 px.
- Boost: 10 px coloured band between two hairline circles (TeleIcons RING); the turned cover is black.
- Bars 4..6 px, values in the regular (not bold) font; gear glyph strokes 5.5 (were 8.5).
Constants to go back: HALF_MIN/HALF_MAX in TeleLayout.

### Earlier: tele8 probe

tele7 on the car (photo 2026-11-23, 6620 rpm): the strip still glitched. A black notch where each
slope meets the flat middle (every piece is cut square to its own direction, so the cuts don't meet
at a bend), a staircase at the partly filled piece (a stretched, turned plain box over a tapered
track), and the 12 shift-light blocks read as a fence.

How the view draws (all builds so far): EAL only places pictures and text. Every shape is a
picture painted in Java once, then moved, scaled, turned or hidden per frame. The tele7 strip was
21 pieces (dark track + lit picture each, plus a scaled box for the piece holding the rpm), about
60 pictures.

What the stock EAL also offers (found in the stock jar, 2026-11-24):
- `IWrappedNode3D.setClippingRectangle(x, y, w, h)` with `setClippingInheritance(17)`,
  `useClippingRectangle(true)`, `setClipping(true)` (the order ComboBoxRendererHigh and
  MultilineTextFieldRendererHigh use). The rectangle is in the node's own coordinates; `clip()` adds
  each ancestor's translation and scale (not rotation) and ends in the native `INode3D.clip`.
- `IWrappedNode3DImage.setModulateColor(int)`: tint a picture (TitleBarRendererHigh, IconRendererHigh).
- `createQuickDrawNode3D` / `IWrappedNode3DQuickDraw`: polyline with line width and colour (the
  finger-trace screens).

tele8 is a probe of the clip, not a new view. `sh tt.sh tele probe on` (or `img`) replaces the view
with: the strip painted whole (TeleStrip: one continuous shape, round joins, soft edges, red zone,
1000 rpm ticks, lit picture white to amber to red), the lit picture under a clip rectangle whose
right edge is the rpm's x on the centre line, white marks above and below where the edge should be,
and a readout of the clip's screen rectangle. Modes: `on`/`grp` clip on a group holding the picture,
`img` clip on the picture itself. At the limiter the lit picture blinks (one visibility). The 10 s
`tele stats` line carries the same readout. PC preview: `gauges/tools/StripPreview.java` ->
`docs/strip-probe-preview.png`.

PC simulator: repo [audi-tt-8s-MHI2-drawing-sim](https://github.com/Tristar10/audi-tt-8s-MHI2-drawing-sim) (`sh run.sh`, `--live` for UDP from
`gauge-send.py` or OpenHaldex). It runs TeleLayout, TeleIcons, TeleStrip and TeleLink as they are
here (copied with its `sync.sh`; this branch stays the source of truth) and draws the slots with
AWT at the car's tick rate over the cockpit photos: demo or live data, painted or pieces strip,
glide between samples, 10/20/50 Hz data and 15-60 Hz ticks. For that, TeleLayout has a STRIP slot
kind and `paintedStrip` (off on the car, so tt-zoom tele8 draws as before). Not simulated: the
car's font size, EAL quirks, the head unit's speed.

Probe test (parked, map tab up): `sh tt.sh tele on`, `sh tt.sh tele demo on`, `sh tt.sh tele probe on`.
Pass: the lit part ends at the white marks while the demo sweeps, no freeze at the top (limiter
blink), frames/s in `grep tele /tmp/tt-rgd.log | tail` like tele7. If the whole strip stays dark or
fully lit, try `sh tt.sh tele probe img`. Then `sh tt.sh tele probe off`.

### Earlier: tele7

tele5 photos and the tele6 preview: the slopes still showed seams and steps, the boost arc was
stepped, in Sport the values ran under the rev counter's ring and the preset row sat under the stock
status line, in Classic the preset ran off the left edge. tele7:
- Slopes: whole pieces are tapered quads that share edges (all tracks drawn before all fills); only
  the piece holding the rpm adds a box scaled to it.
- Boost: one fixed coloured ring (60 pieces, never redrawn) and a cover in the track colour made of
  two quarter rings (TeleIcons ARCQ) turned to the boost about the arc's centre; a black block under
  the centre line hides the cover's lower half. The arc sweeps smoothly and costs two turns a frame.
- Sport: content between x 52 and 432; preset and mode under the gear. Classic: preset at 30 % of the
  rising slope; glow behind the gear narrower (clear of the boost arc).

### Earlier: tele6

First road test (tele5 + OpenHaldex v1.1.19, 2026-11-23): mildly successful. Found: the screen froze
on reaching the limiter (all 84 strip pieces changed colour on every flash, and the first time each
needed new pictures); the brake read about 1 bar off the brakes; the stepped strip was not exact.
tele6: the strip is 21 straight pieces (10 per slope, the flat middle one), each a dark track plus a
fill box that is only scaled to the exact rpm (turned boxes on the slopes, `rot` on BOX slots); the lit
part takes one colour by rpm (4 steps white to amber, red past the shift point); at the limiter only
the fill blinks. Brake: EMA (0.3 per 30 Hz tick) with a 2 bar dead band.

### Earlier: tele5

The receiver's port. With wired CarPlay up, UDP 49160 was taken ("BindException ... 0.0.0.0:49160",
`*.49160` in netstat, no ttlink running): CarPlay's AirPlay receiver binds ports in the same
49153-49162 range the firewall opens. User decision 2026-11-23: port discovery. OpenHaldex v1.1.19
sweeps 49153..49162 with one probe every 250 ms until a TTA1 answers and then keeps the port the
answer came from (re-sweeping after 3 s without acks). tele5 binds the first free port from the top,
49162 down to 49153 (CarPlay seems to fill the range from the bottom), logs `tele rx listening udp N`,
and acks and commands leave from that socket. PC test with 49162 held: bound 49161, 121/121 acked,
presets ok. Not yet known: whether CarPlay ever wants the port we took when it connects after the
view is on (then CarPlay would fail); a netstat with the phone in and out would show its set.

### Earlier: tele4

tele3 on the car: once the view did not draw after `tele on` (a job posted while the HMI was still
starting can be dropped, and the tick thread then waited for it forever), and after a quick restart
the link stayed at "NO LINK" with OpenHaldex probing. tele4: a job with no answer in 2 s is given up
and the frame sent again (`tele job lost` in the log); every 10 s while the view is on, one
`tele stats:` line with frames/s, job times, a stuck job's age, and the receiver: port
open/binding/closed, rx, acks, bad datagrams and the last sender in those 10 s, bind errors (logged
on the first three and every 30th; the bind is retried every 2 s, e.g. while an exiting HMI still
holds 49160).

The switches are in /tmp: they survive an HMI-only restart, not a full MMI reboot (then the view is
off unless `sh tt.sh tele boot on`).

Wired CarPlay stopping during these tests was not tt-zoom: `smartphone_integrator` dropped the
iPhone (`[SI] disconnectDevice`); forgetting the car on the iPhone and pairing again fixed it.

### Earlier: tele3

tele2 on the car (2026-11-23): `-deg` tilt is right. The car's font draws about 1.45x the preview's at
the same size (cap height about the size itself), so texts crowded their neighbours; the gear was
soft; refresh looked slow. tele3: texts at 70 % of the layout size (`/tmp/tt-tele-font` holds another
percentage to try), the gear drawn as our own stroke glyphs (TeleIcons GLYPH, sharp at any size),
84 rev-strip pieces instead of 120, 20 Haldex pieces instead of 25, tick up to 30 frames/s.

### Earlier: tele2

First car run (tele1, 2026-11-23, parked, demo values): the whole layout draws and moves. Found:
- tele1 was built on the v63 *prototype* (90300bc), not on the v63 the car runs (4e07548): the
  prototype's screen-factory hook for the "About Apple CarPlay" page came back, and a wired
  iPhone's CarPlay did not start (the wireless dongle did). tele2 merges 4e07548; apart from the 7
  Tele classes and the hooks in RgdBus, RgiPlayer and TtPanel, every class is byte-identical to
  v63 (e434810).
- EAL blends pictures as premultiplied alpha: the 10-30 % glows came out as solid red. tele2
  premultiplies every picture.
- setRotationZ turns the other way: the corner text tilted against the slopes. tele2 defaults to
  `-deg`.
- The refresh looked slow; tele2 logs `tele stats` every 10 s (frames per second, job time,
  changed slots).
- The gear (font size 154) looks soft, probably scaled up from a smaller font size.

Built from the `telemetry` branch: v63's code (nothing on `main` is missing) plus:

| File | What |
|---|---|
| `hmi/src/local/tt/TeleLayout.java`, `TeleIcons.java` | The layout and icons (the same code the PC preview draws) |
| `hmi/src/local/tt/TeleLink.java` | Receiver: UDP 49160, open only while the view is on (so `ttlink` still works when it is off); acks every TTG1 with TTA1; reads the TTP1 catalog; merges channels from several senders; `apply` sends TTC1 to the catalog's sender |
| `hmi/src/local/tt/TeleView.java`, `TeleFrame`, `TeleSlot`, `TeleJob` | Drawing. Tick thread at 20 Hz builds the layout and draws every new picture off the HMI thread (quads with 4x4 soft edges, big glows at half size, icons, solid blocks); posts one job when the picture changed. The HMI thread only creates nodes, uploads those pictures and switches visibility, text, colour (one node per colour a slot uses), position and scale. Own viewport at index 228, above the turn panel (225), opaque, so the map and the panel are hidden while it shows |
| `RgdBus.start` | starts it; `RgiPlayer` playback line `tele apply N` / `tele info`; `TtPanel.ealManager()` |

It shows only while the map tab (Classic) or the small view's map area (Sport) is up. Switches,
RAM unless noted, read within 1 s, through the kit:

```
sh tt.sh tele on | off          # view on/off (until the double press is traced)
sh tt.sh tele demo on | off     # sweeping fake values, no OpenHaldex needed
sh tt.sh tele rot deg | -deg | rad | -rad | off   # how the tilted corner text turns
sh tt.sh tele boot on | off     # flash: on at every MMI start
```

Messages go to `/tmp/tt-rgd.log` (`tele rx listening`, `tele catalog`, `tele job N ms` when a job
takes over 30 ms or reports something).

Not known until the car:
- Tilted text and icons use `setRotationZ` on a group node, never used on the car before: degrees
  or radians, and which way. `tele rot` switches it live; `off` makes them level.
- Positions come from photos; the cluster's own parts are drawn above our layer.
- How long the first build takes (about 300 nodes) and the per-frame jobs.
- The fonts are the HMI's, so text widths differ a little from the preview.

### Parked test (tele1)

| # | Do | Pass |
|---|---|---|
| T1 | Copy `hmi/out/tele1/tt-zoom.jar` (as `/tmp/tt-zoom-tele1.jar`) and the new `kit/tt.sh` to the car. `mount -uw /mnt/app; cp /tmp/tt-zoom-tele1.jar /mnt/app/eso/hmi/lsd/jars/tt-zoom.jar; cp /tmp/tt.sh /mnt/app/root/tt.sh; mount -ur /mnt/app`, restart the MMI | CarPlay, the map and the turn panel work as with v63 (the view is off by default). Back: `sh tt.sh use v63` |
| T2 | Map tab, Classic view: `sh tt.sh tele demo on`, then `sh tt.sh tele on` | The autocross view replaces the map within a second and sweeps: shift lights, rev strip, gear, boost arc. Photo. `tele job` lines in the log |
| T3 | `sh tt.sh tele rot -deg`, `rad`, `-rad` in turn if the corner text is not along the slopes | One of them follows the slopes; note which |
| T4 | Switch to the Sport view, then the car tab, then back to the map | Sport layout in the map area; nothing on the car tab; back on the map tab the view returns |
| T5 | `sh tt.sh tele off` | The map is back at once |
| T6 | With OpenHaldex on the hotspot and `ttlink` not running: `sh tt.sh tele demo off; sh tt.sh tele on` | Live values; link "Hz" shown only when slow; `tele info` (playback) shows rx and rate |

## Car test plan (parked, one step per test)

| # | Do | Pass |
|---|---|---|
| 1 | MMI: turn the Wi-Fi hotspot on and note the SSID and password. Join it with the laptop | Laptop gets a 10.173.189.x address; `ping 10.173.189.1` answers. Note whether the laptop got a gateway (expected: none). If the hotspot will not start without a data plan, stop here |
| 2 | Copy `ttlink` to `/mnt/app/root/tt/`, then over SSH run `chmod +x` on it and start it with `-v` | `ttlink listening on udp 49160`. If the bind fails, the port is taken: `-p 49161`. Run it by hand for this test, not at boot |
| 3 | Laptop: `python3 gauge-send.py --host 10.173.189.1 --presets` | Laptop shows acks and rtt; ttlink shows rx, values and the catalog. Typing `2` in ttlink applies preset 2 on the laptop. This proves the path without the ESP32 |
| 4 | OpenHaldex `one-board-ble` **v1.1.16** or later (no-gateway fallback; already flashed on the board). In the OpenHaldex app enter the MMI hotspot SSID and password | The app's More > Wi-Fi > MMI gauge link "Head unit" reads 10.173.189.1 (first real run of the fallback); `uaputl sta_list` lists the ESP32; ttlink shows `esp_up_s`, `rssi`, live rpm and the catalog; the app's Wi-Fi page shows the MMI link as linked. Gear, boost and oil pressure may read 0 (that CAN message probably isn't on the Haldex bus) |
| 5 | Engine running, parked: rev it; in ttlink type a preset id | rpm follows in ttlink; `reply ... ok` and `active preset` change; the app shows the same preset; Haldex control unaffected |

## Next, after the link works

- Find how the scroll-wheel **press** reaches the HMI. Rotation arrives as
  `AbstractGUI.increment(400898, ...)` ([HMI patching](HMI-patching.md)); the press has not been
  traced yet.
- Move the receiver into `tt-zoom.jar` (Java, as RgdBus does) and draw the text page with EAL
  text nodes over the map area ([Cockpit drawing](Cockpit-drawing.md)).
- Autocross view, third draft (2026-11-23, after the FL5 Civic Type R's +R screen; waiting for the
  user's OK). Layout code `hmi/src/local/tt/TeleLayout.java` (pure Java: slots of boxes, quads,
  icons and texts, no EAL), icons `hmi/src/local/tt/TeleIcons.java` (drawn from shapes in code,
  same pixels on the car and the PC). PC preview `gauges/tools/TelePreview.java` draws the slots
  over the cockpit photos into `docs/telemetry-preview.png`, clipped by what the cluster draws above
  our layer: dials, tab bar, bottom bar and the frame over the top corners (the top corners of the
  screen do not show). A slot cut by them is a collision.
  - The map is hidden by our own opaque full-screen layer; CarPlay keeps running underneath, so
    switching back is instant. Our turn panel hides while the autocross view shows.
  - Classic view (2026-11-23, fourth pass):
    - Rev strip: straight pieces following the cluster housing, up the frame's top-left edge, flat,
      down the top-right edge. The flat middle is the fattest part (54 px) and ends exactly at the
      shift point; the slopes taper to 30 px, so every number fits inside. The scale has three parts: left slope 0-3000 rpm,
      middle 3000 rpm to the shift point, right slope shift point to 8000 (room for a higher
      rev limit later). Red zone from 7000: the unlit track there is dark red and its numbers red. Numbers sit inside the strip, dark on the lit
      part. Dark track with hairline edges; lit from cool white warming to amber over the last
      1500 rpm before the shift point, all red past it, flashing at the limiter; red marks over the
      limiter range on the outer edge.
    - Shift lights: 12 slanted lights above the strip, as wide as its fat middle, lighting from
      both ends towards the middle: green, blue, red over the last 1200 rpm (a glow behind each lit
      one). From the shift point all flash red, together with the gear notch and the red glow
      behind it.
    - Gear in a two-layer red notch under a red line with a soft glow.
    - Left of the notch: rpm, and the driver's inputs under it: accelerator (green bar, percent)
      and brake pressure (channel 21, red bar full at 100 bar, value in bar). Right of it: boost as a
      turbo-style arc from -1 to +1.5 bar (user, 2026-11-23: bar; psi as an option): segments sweep
      from vacuum (steel blue) through a white zero mark into boost, warming amber, orange, red from
      1.2 bar, with a glow while on boost; a white marker holds the peak for 3 s; the big value sits
      inside the arc; channel 8 is taken as kPa above atmosphere and probably
      reads 0 until the second LilyGO.
    - Top-left pocket, tilted along the slope: the active preset with its mode, and a Wi-Fi warning
      icon only while the link is slow (under 10 Hz) or lost. Top-right pocket, tilted: Haldex lock,
      icon and big percent over a bar along the slope with the target tick.
    - The five temperatures in one row at the bottom, one icon each, in C; amber or red out of range,
      blue while the oil is cold.
    - Tilted text and icons need EAL `setRotationZ` on the car, untested (the dial sketch never
      calibrated degrees vs radians or the sign); if it does not work they fall back to level.
    - Not on the screen: speed (the stock speedometer shows it), engine torque (not sent), G-meter,
      stopwatch.
  - Sport view, in the map area left of the rev counter (top to bottom): the preset with its mode
    (and the Wi-Fi warning when the link is slow or lost), a Haldex lock strip with the target tick
    and percent, the big gear with the five temperatures beside it, and a boost strip from -1 to
    +1.5 bar with zero marked and the value.
  - Defaults (user, 2026-11-23): shift 6500, limiter 6800, temperatures in C.
  - Presets page (kept from the first draft, for later): a panel with the catalog, green mark on the
    active preset, cursor row.
  - Open: how the scroll-wheel double press reaches the HMI (needs a trace in the car).
  - Gear: channel 13 as sent; it probably reads 0 ("N") until a second LilyGO on the powertrain
    CAN sends it (user decision 2026-11-23: no gear worked out from rpm and speed). The hotspot
    takes several clients (dnsmasq leases .10-.99), so that board can join it too: the head-unit
    receiver merges channels from every TTG1 sender, and preset commands go only to the sender of
    the preset catalog (TTP1).
- Shift cue: a sound clip played by the head unit. How to play one, and its delay, is not known
  yet.
