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
| Cockpit text page, wheel double-press | Not started. The dial sketch in `gauges/hmi` is on hold and kept only for its drawing and threading code |

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

Build `ttlink` (WSL, toolchain image from [Build and test](Build-and-test.md)):

```bash
wsl -d Ubuntu-24.04 -u root -- sh -c 'cd /mnt/c/.../gauges/headunit && docker run --rm -v "$PWD:/w" -w /w mhi2-qnx65-armv7-public:4.9 arm-unknown-nto-qnx6.5.0eabi-gcc -O2 -std=gnu99 -mfloat-abi=softfp ttlink.c -lsocket -o out/ttlink'
```

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
      down the top-right edge. The flat middle is the fattest part (44 px) and ends exactly at the
      shift point; the slopes taper to 16 px. The scale has three parts: left slope 0-3000 rpm,
      middle 3000 rpm to the shift point, right slope shift point to 7000 (the over-rev zone,
      stretched so the limiter range reads clearly). Numbers sit inside the strip, dark on the lit
      part. Dark track with hairline edges; lit from cool white warming to amber over the last
      1500 rpm before the shift point, all red past it, flashing at the limiter; red marks over the
      limiter range on the outer edge.
    - Shift lights: 12 slanted lights above the strip, as wide as its fat middle, lighting from
      both ends towards the middle: green, blue, red over the last 1200 rpm (a glow behind each lit
      one). From the shift point all flash red, together with the gear notch and the red glow
      behind it.
    - Gear in a two-layer red notch under a red line with a soft glow.
    - Left of the notch: rpm, and the accelerator pedal (bar and percent) under it. Right of it:
      boost (value, and a bar from vacuum to boost with a tick at zero; bar by default, psi as an
      option; channel 8 is taken as kPa above atmosphere and probably reads 0 until the second
      LilyGO).
    - Top-left pocket, tilted along the slope: the active preset with its mode, and a Wi-Fi warning
      icon only while the link is slow (under 10 Hz) or lost. Top-right pocket, tilted: Haldex lock,
      icon and big percent over a bar along the slope with the target tick.
    - The five temperatures in one row at the bottom, one icon each, in C; amber or red out of range,
      blue while the oil is cold.
    - Tilted text and icons need EAL `setRotationZ` on the car, untested (the dial sketch never
      calibrated degrees vs radians or the sign); if it does not work they fall back to level.
    - Not on the screen: speed (the stock speedometer shows it), engine torque (not sent), G-meter,
      stopwatch.
  - Sport view: gear and the five temperatures with icons, in the map area left of the rev counter.
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
