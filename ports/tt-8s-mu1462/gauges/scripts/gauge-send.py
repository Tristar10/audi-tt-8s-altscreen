#!/usr/bin/env python3
"""Plays the ESP32's part from a laptop joined to the MMI hotspot (parked link test).

    python3 gauge-send.py --host 192.168.x.1            # demo values, 20 Hz, until Ctrl+C
    python3 gauge-send.py --host 192.168.x.1 --set rpm=3000 boost=120 --once
    python3 gauge-send.py --dump packet.bin --set rpm=3000   # write one datagram to a file (PC test)
    python3 gauge-send.py --host 192.168.x.1 --presets       # also play OpenHaldex's preset side

Sends "TTG1" datagrams to the head unit's ttlink (default port 49160, already open in the stock
firewall for hotspot clients) and reads its "TTA1" acks: once a second it prints acks received,
loss and round-trip time, like the ESP32 test sketch does on its serial port.
Same format as OpenHaldex-S3 mmi_link.cpp and headunit/ttlink.c. Values in SI units.

--presets also sends the "TTP1" preset catalog (on start and every 5 s), answers "TTC1" apply
commands with "TTR1" and reports the active preset on channel 22, like OpenHaldex docs/PRESETS.md.
The catalog is OpenHaldex's compiled-in default list; --refuse N answers every apply with result N.
"""
import argparse
import math
import socket
import struct
import time

CHANNELS = {
    'rpm': 1, 'speed': 2, 'pedal': 3, 'torque': 4, 'coolant': 5, 'oil': 6, 'iat': 7, 'boost': 8,
    'lambda': 9, 'haldex_lock': 10, 'haldex_act': 11, 'haldex_mode': 12, 'gear': 13, 'lat_g': 14,
    'long_g': 15, 'dsg_oil': 16, 'battery': 17, 'oil_press': 18, 'clutch_temp': 19,
    'steering': 20, 'brake': 21, 'preset': 22,
    'up_s': 60, 'rssi': 61, 'sent': 62,   # link test: sender uptime, Wi-Fi RSSI dBm, datagrams sent
}


def packet(values, key=0, seq=0, flags=1):
    """values: {channel id: SI value}. Returns the datagram bytes."""
    items = sorted(values.items())[:40]
    if not items:
        raise ValueError('no values')
    out = b'TTG1' + struct.pack('>IHBB', key & 0xFFFFFFFF, seq & 0xFFFF, flags, len(items))
    for cid, v in items:
        out += struct.pack('>Hi', cid, int(round(v * 1000)))
    return out


def demo(t):
    s = (math.sin(t * 0.7) + 1) / 2
    h = (math.sin(t * 1.3) + 1) / 2
    return {1: 900 + 6000 * s, 8: -50 + 230 * s, 4: 30 + 420 * s, 3: 100 * s, 2: 40 + 60 * s,
            5: 88 + 6 * math.sin(t * 0.1), 6: 100 + 15 * math.sin(t * 0.08), 7: 30 + 10 * s,
            16: 75 + 10 * math.sin(t * 0.05), 9: 0.8 + 0.2 * (1 - s), 10: 100 * h,
            11: 100 * ((math.sin(t * 1.3 - 0.4) + 1) / 2), 12: 2}


# OpenHaldex default catalog: id, name, mode number (openhaldex_mode_t), map path.
PRESETS = [(1, 'Street', 0, ''), (2, 'Autocross', 9, '/maps/aggressive.txt'), (3, 'Launch', 2, ''),
           (4, 'Wet', 9, '/maps/conservative.txt'), (5, 'FWD', 1, '')]


def crc16_ccitt(data, crc=0xFFFF):
    for b in data:
        crc ^= b << 8
        for _ in range(8):
            crc = ((crc << 1) ^ 0x1021) if crc & 0x8000 else (crc << 1)
            crc &= 0xFFFF
    return crc


def catalog_version(presets):
    # Stand-in for the firmware's CRC (same inputs, byte layout may differ); never 0.
    data = b''.join(bytes([i, m]) + n.encode() + mp.encode() for i, n, m, mp in presets)
    return crc16_ccitt(data) or 1


def catalog_packet(presets, key):
    out = b'TTP1' + struct.pack('>IHB', key & 0xFFFFFFFF, catalog_version(presets), len(presets))
    for i, n, m, _ in presets:
        nb = n.encode()[:16]
        out += bytes([i, m, len(nb)]) + nb
    return out


def parse_set(items):
    vals = {}
    for it in items or []:
        name, _, v = it.partition('=')
        if name not in CHANNELS:
            raise SystemExit('unknown channel %r (known: %s)' % (name, ', '.join(sorted(CHANNELS))))
        vals[CHANNELS[name]] = float(v)
    return vals


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument('--host', help='head unit address on the hotspot (usually the gateway)')
    ap.add_argument('--port', type=int, default=49160)
    ap.add_argument('--seconds', type=float, default=0, help='stop after this long (0 = until Ctrl+C)')
    ap.add_argument('--key', type=int, default=0)
    ap.add_argument('--rate', type=float, default=20.0)
    ap.add_argument('--set', nargs='*', help='name=value pairs instead of the demo sweep')
    ap.add_argument('--once', action='store_true')
    ap.add_argument('--dump', help='write one datagram to this file and exit')
    ap.add_argument('--presets', action='store_true', help="also play OpenHaldex's preset side")
    ap.add_argument('--refuse', type=int, help='answer every apply with this TTR1 result code')
    a = ap.parse_args()

    fixed = parse_set(a.set)
    if a.dump:
        open(a.dump, 'wb').write(packet(fixed or demo(0.0), a.key, 1))
        return
    if not a.host:
        raise SystemExit('--host is required (or --dump)')
    s = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
    s.settimeout(0.0)
    seq, sent, t0 = 0, 0, time.time()
    sent_at = {}                       # seq -> send time, for the round trip
    acks, rtts, last_report = 0, [], t0
    next_send = t0
    active, last_cat, answered = 255, 0.0, {}   # preset state for --presets
    cat_ver = catalog_version(PRESETS)
    try:
        while True:
            now = time.time()
            if now >= next_send:
                vals = dict(fixed or demo(now - t0))
                vals.update({60: now - t0, 62: sent})
                if a.presets:
                    vals[22] = active
                    if now - last_cat >= 5.0:
                        s.sendto(catalog_packet(PRESETS, a.key), (a.host, a.port))
                        last_cat = now
                s.sendto(packet(vals, a.key, seq), (a.host, a.port))
                sent_at[seq] = now
                sent += 1
                seq = (seq + 1) & 0xFFFF
                next_send += 1.0 / a.rate
                if a.once:
                    time.sleep(0.5)
            try:
                while True:
                    data, src = s.recvfrom(64)
                    if a.presets and len(data) == 14 and data[:4] == b'TTC1':
                        key, cseq, cmd, pid, ver = struct.unpack('>IHBBH', data[4:])
                        if key != a.key:
                            continue
                        if cseq in answered:            # retry: answer again, don't apply twice
                            res = answered[cseq]
                        elif a.refuse is not None:
                            res = a.refuse
                        elif cmd != 1:
                            res = 255
                        elif ver != cat_ver:
                            res = 1
                        elif pid not in [p[0] for p in PRESETS]:
                            res = 2
                        else:
                            res, active = 0, pid
                        answered[cseq] = res
                        s.sendto(b'TTR1' + struct.pack('>IHBBH', a.key, cseq, res, active, cat_ver), src)
                        print('        TTC1 seq=%d apply %d (v=0x%04x) -> result %d, active %d'
                              % (cseq, pid, ver, res, active), flush=True)
                        continue
                    if len(data) == 20 and data[:4] == b'TTA1':
                        aseq, _, rx, lost, up = struct.unpack('>HHIII', data[4:])
                        acks += 1
                        t = sent_at.pop(aseq, None)
                        if t is not None:
                            rtts.append((time.time() - t) * 1000)
                        last_rx, last_lost = rx, lost
            except (BlockingIOError, socket.timeout):
                pass
            except ConnectionResetError:
                pass  # Windows: ICMP port unreachable (nothing listening yet)
            if a.once or now - last_report >= 1.0:
                if rtts:
                    r = sorted(rtts)
                    print('%5.0fs sent=%d acks=%d (%.0f%%) rtt med %.1f ms max %.1f ms | head unit rx=%d lost=%d'
                          % (now - t0, sent, acks, 100.0 * acks / max(sent, 1), r[len(r) // 2], r[-1],
                             last_rx, last_lost), flush=True)
                else:
                    print('%5.0fs sent=%d acks=%d  (no answer: ttlink running? right --host/--port?)'
                          % (now - t0, sent, acks), flush=True)
                rtts, last_report = [], now
                for k in [k for k, v in sent_at.items() if now - v > 5]:
                    del sent_at[k]
            if a.once or (a.seconds and now - t0 >= a.seconds):
                break
            time.sleep(0.002)
    except KeyboardInterrupt:
        pass
    print('done: sent=%d acks=%d' % (sent, acks))


if __name__ == '__main__':
    main()
