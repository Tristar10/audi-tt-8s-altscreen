# TT 8S port wiki

How the CarPlay AltScreen was brought to the Audi TT 8S Virtual Cockpit
(MHI2_US_AU43x_P5180, MU1462, G24), and what was learned on the way. Dead ends
are kept, with the evidence, so nobody has to repeat them.

Everything marked "works" was checked on one car (US TT, MU1462), November 2026.

## Status

| Feature | State | Page |
|---|---|---|
| CarPlay map (Apple/Google Maps) on the cockpit map tab | Works | [CarPlay map](CarPlay-map.md) |
| Large/Sport view (tach-centred) layout | Works | [CarPlay map](CarPlay-map.md#small-view-layout) |
| Steering-wheel scroll zoom | Works | [CarPlay map](CarPlay-map.md#steering-wheel-zoom) |
| iPhone turn-by-turn data on the head unit | Works | [Turn-by-turn data](Turn-by-turn-data.md) |
| Turn panel on the cockpit (arrows, lanes, exit badge) | Works; first short drive done (Google Maps) | [Turn panel](Turn-panel.md) |
| Stock guidance display fed with iPhone turns | Not possible | [Stock guidance dead ends](Stock-guidance-dead-ends.md) |
| CarPlay tab and map switching without drops | Fixed for normal switching (renderer build 5/6, supervisor v9); light-speed flipping out of scope | [Display-window handover](Display-window-handover.md) |
| Bottom strip with Google/Apple turn text ("Left to Alpha St") | Works | [CarPlay map](CarPlay-map.md#the-bottom-strip) |
| OpenHaldex telemetry over the MMI hotspot (text page on the map area) | Head-unit tool built and PC-tested; sender added to OpenHaldex-S3; not in the car yet | [Telemetry](Telemetry.md) |

## Pages

**The car**
- [System architecture](Architecture.md): every part involved, with a diagram
- [How the TT differs](TT-differences.md): one display, MMI-Kombi, contexts, firewall
- [Components](Components.md): what is installed where

**Features**
- [CarPlay map](CarPlay-map.md): safe area, small-view shift, wheel zoom
- [Turn-by-turn data](Turn-by-turn-data.md): getting route guidance from the iPhone
- [Turn panel](Turn-panel.md): our guidance panel, its pictures and its history
- [Telemetry](Telemetry.md): OpenHaldex data over the MMI hotspot, link test first

**Internals**
- [HMI patching](HMI-patching.md): how `tt-zoom.jar` overrides stock classes, and the rules
- [Cockpit drawing](Cockpit-drawing.md): drawing our own nodes with the EAL, and its traps
- [Stock guidance dead ends](Stock-guidance-dead-ends.md): why the stock banner cannot show iPhone turns
- [Display-window handover](Display-window-handover.md): the CarPlay-tab crash, how it was found, and the fix (with diagrams)

**Working on it**
- [Build and test](Build-and-test.md): build, `tt.sh` toolkit, parked tests, logs
- [Credits and exclusions](Credits.md): third-party code, what is never committed
- [Working with Claude](Working-with-Claude.md): how this was built with Claude Code, prompts and tips

## Next

- Stability run with renderer build 6: about 100 normal CarPlay-tab switches over two sessions.
- v1.0.0: SD-card (M.I.B.) installer with the tested renderer, supervisor and jar; how-to on GitHub;
  clean-install test.
- Chinese phone language: exit numbers from Chinese instructions (need a sample).
