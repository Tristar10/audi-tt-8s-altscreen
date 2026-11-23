# OpenHaldex telemetry

[Home](Home.md)

Live car data from the OpenHaldex ESP32, sent over the MMI Wi-Fi hotspot and shown as text on
the cockpit map area, like a second AltScreen page.

## Goal (user, 2026-11-22)

- **Text, not dials.** Dials take too much room.
- **Shown where the maps go:** drawn over the map area as its own page. Pressing the scroll wheel
  twice switches between the map and this page.
- **Large view: our own cluster.** A big rev counter across the top, a big gear number, and
  telemetry lines. The exact look comes later.
- **First milestone: the link.** OpenHaldex connects to the MMI and basic data arrives. The UI
  comes after that.

## State

| Step | State |
|---|---|
| How the MMI's Wi-Fi works | Found in the firmware (below) |
| Head-unit test tool `ttlink` | Built for QNX; tested on the PC against the laptop sender: 81 datagrams, 0 lost, acks back, wrong key rejected |
| Sender in OpenHaldex-S3 (`mmi_link.cpp`) | Added to the OpenHaldex firmware; builds with PlatformIO (RAM +0.9 KB, flash +10 KB). Not flashed |
| Link test in the car | Not done |
| Cockpit text page, wheel double-press | Not started. The dial sketch in `gauges/hmi` is on hold and kept only for its drawing and threading code |

## Where the data comes from

OpenHaldex-S3 already decodes the chassis CAN with its built-in VW DBC tables (the `received_*`
values its RaceChrono feed uses). The MMI link reads those same values, so nothing new is decoded
on the ESP32. Its own `OpenHaldex-S3` access point keeps running: the ESP32 runs AP and client at
once, and joins the MMI hotspot with OpenHaldex's existing client Wi-Fi settings (Setup page,
the same ones used for OTA).

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
  `ttlink` uses UDP **49160**, so **no firewall change is needed**.
- The reverse (ESP32 as access point, MMI as client) would need the MMI's client mode. The
  firmware contains `wpa_supplicant`, but nothing shows the MMI using a Wi-Fi client mode, so this
  is not the plan.
- The ESP32 sends to its gateway address, which is the MMI, so no IP address is configured.
- Until the head unit answers, OpenHaldex sends one probe a second; with acks it streams at
  20 Hz. A home router on the client side never gets the full stream.
- OpenHaldex's own AP is 192.168.4.1/24. If the MMI hotspot used the same subnet the link could
  not work; OpenHaldex logs a warning if it sees that. Step 1 below checks the hotspot subnet.

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

Also 18 oil pressure bar, 19 Haldex clutch °C, 20 steering angle °, 21 brake pressure bar.
OpenHaldex does not send 9, 14, 15 or 17 (no source on the bus yet). Full source mapping:
`docs/MMI_LINK.md` in OpenHaldex-S3.

They are defined in `ttlink.c`, `gauge-send.py` and OpenHaldex `mmi_link.cpp`; change all three together.

## Files

| File | What |
|---|---|
| `gauges/headunit/ttlink.c` | Head-unit tool: listens on UDP 49160 and logs one summary per second, with `-v` one line per datagram. Acks each datagram. Log in `/tmp/ttlink.log`. No jar and no HMI restart |
| OpenHaldex-S3 `src/functions/telemetry/mmi_link.cpp` | Sender: low-priority task, reads the decoded CAN values, sends, reads acks; stats in `/api/network` under `mmi` |
| `gauges/scripts/gauge-send.py` | Laptop stand-in for OpenHaldex, with the same output |

Build `ttlink` (WSL, toolchain image from [Build and test](Build-and-test.md)):

```bash
wsl -d Ubuntu-24.04 -u root -- sh -c 'cd /mnt/c/.../gauges/headunit && docker run --rm -v "$PWD:/w" -w /w mhi2-qnx65-armv7-public:4.9 arm-unknown-nto-qnx6.5.0eabi-gcc -O2 -std=gnu99 -mfloat-abi=softfp ttlink.c -lsocket -o out/ttlink'
```

## Car test plan (parked, one step per test)

| # | Do | Pass |
|---|---|---|
| 1 | MMI: turn the Wi-Fi hotspot on and note the SSID and password. Join it with the laptop | Laptop gets an address (note the subnet: it must not be 192.168.4.x); `ping` the gateway answers. If the hotspot will not start without a data plan, stop here |
| 2 | Copy `ttlink` to `/mnt/app/root/tt/`, then over SSH run `chmod +x` on it and start it with `-v` | `ttlink listening on udp 49160`. If the bind fails, the port is taken: `-p 49161` |
| 3 | Laptop: `python3 gauge-send.py --host <gateway>` | Laptop shows acks and rtt; ttlink shows rx and values. This proves the path without the ESP32 |
| 4 | Flash OpenHaldex-S3 with the MMI link. On its Setup page enter the MMI hotspot SSID and password as the client Wi-Fi, client enabled | `uaputl sta_list` lists the ESP32; ttlink shows `esp_up_s`, `rssi` and live rpm/gear; `http://openhaldex.local/api/network` (phone on the OpenHaldex AP) shows `"mmi":{"linked":true…}` |
| 5 | Engine running, parked: rev it, change Haldex mode | rpm and `hx_mode` follow in ttlink; Haldex control unaffected |

## Next, after the link works

- Find how the scroll-wheel **press** reaches the HMI. Rotation arrives as
  `AbstractGUI.increment(400898, ...)` ([HMI patching](HMI-patching.md)); the press has not been
  traced yet.
- Move the receiver into `tt-zoom.jar` (Java, as RgdBus does) and draw the text page with EAL
  text nodes over the map area ([Cockpit drawing](Cockpit-drawing.md)).
- Large view layout: rev counter across the top, big gear, telemetry lines.
