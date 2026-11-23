# OpenHaldex telemetry

The sender lives in the OpenHaldex-S3 firmware (`src/functions/telemetry/mmi_link.cpp`,
`docs/MMI_LINK.md`). This folder is the head-unit side.

OpenHaldex ESP32 to the MMI over its Wi-Fi hotspot, shown later as a text page on the cockpit map
area. Current step: the link test. Plan, protocol and car test steps: [wiki/Telemetry.md](../wiki/Telemetry.md).

| Folder | What |
|---|---|
| `headunit/ttlink.c` | Head-unit link test tool (QNX): receives "TTG1" on UDP 49160, logs, acks with "TTA1" |
| `scripts/gauge-send.py` | Laptop stand-in for OpenHaldex (sends, reads acks, prints rtt and loss) |
| `hmi/`, `tools/` | On hold: the first dial sketch (EAL drawing, wheel paging). Its threading and drawing code is the starting point for the text page; the dials themselves are dropped |
