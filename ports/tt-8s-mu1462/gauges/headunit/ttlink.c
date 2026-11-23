/*
 * ttlink: OpenHaldex <-> MMI Wi-Fi link test, head-unit side (QNX 6.5, also builds on Linux).
 *
 * Listens for "TTG1" telemetry datagrams from the ESP32 (a client on the MMI hotspot, uap0),
 * logs them, and answers each one with a "TTA1" ack so the ESP32 can measure round trip and loss.
 * Standalone: no HMI jar, no restart. Run over SSH, stop with Ctrl+C.
 *
 *   ttlink [-p port] [-k key] [-l log] [-v] [-n] [-a preset]
 *     -p  UDP port, default 49160. The stock firewall already passes UDP 49153-49162 from hotspot
 *         clients to uap0 (pf.conf "WLAN" section), so no firewall change is needed.
 *     -k  key the datagrams must carry, default 0
 *     -l  log file, default /tmp/ttlink.log (capped at 256 KB), "-" for none
 *     -v  one line per datagram (default: one summary per second)
 *     -n  no acks
 *     -a  apply this preset id once the catalog has arrived. While running, typing a preset id
 *         and Enter applies that preset too.
 *
 * TTG1 (ESP32 -> head unit), big-endian:
 *   0 "TTG1" | 4 key u32 | 8 seq u16 | 10 flags u8 | 11 count u8 | 12 count * (u16 id, s32 value*1000)
 * TTA1 (head unit -> ESP32), big-endian, 20 bytes:
 *   0 "TTA1" | 4 seq u16 (echoed) | 6 reserved u16 | 8 rx u32 | 12 lost u32 | 16 uptime ms u32
 *
 * Presets (OpenHaldex docs/PRESETS.md), same socket, same key:
 * TTP1 (ESP32 -> head unit) catalog, on link-up, on change and every 5 s:
 *   0 "TTP1" | 4 key u32 | 8 catalog version u16 | 10 count u8 | 11 count * (id u8, mode u8, len u8, name)
 * TTC1 (head unit -> ESP32, sent to the TTG1 source address) command, 14 bytes:
 *   0 "TTC1" | 4 key u32 | 8 cmd seq u16 | 10 cmd u8 (1 = apply preset) | 11 preset id u8 | 12 version u16
 * TTR1 (ESP32 -> head unit) reply, 14 bytes:
 *   0 "TTR1" | 4 key u32 | 8 cmd seq u16 | 10 result u8 | 11 active id u8 | 12 version u16
 * Channel 22 in TTG1 is the active preset id (255 = none), the authoritative confirmation.
 */
#include <arpa/inet.h>
#include <errno.h>
#include <netinet/in.h>
#include <signal.h>
#include <stdint.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <sys/select.h>
#include <sys/socket.h>
#include <sys/stat.h>
#include <time.h>
#include <unistd.h>

#define MAXCH 64
#define LOG_CAP (256 * 1024)

static volatile sig_atomic_t g_stop;
static FILE *g_log;
static const char *g_log_path = "/tmp/ttlink.log";

static const char *ch_name(int id)
{
    switch (id) {
    case 1: return "rpm";
    case 2: return "speed";
    case 3: return "pedal";
    case 4: return "torque";
    case 5: return "coolant";
    case 6: return "oil";
    case 7: return "iat";
    case 8: return "boost";
    case 9: return "lambda";
    case 10: return "hx_req";
    case 11: return "hx_act";
    case 12: return "hx_mode";
    case 13: return "gear";
    case 14: return "lat_g";
    case 15: return "long_g";
    case 16: return "dsg_oil";
    case 17: return "batt";
    case 18: return "oilp";
    case 19: return "clutch";
    case 20: return "steer";
    case 21: return "brake";
    case 22: return "preset";
    case 60: return "esp_up_s";
    case 61: return "rssi";
    case 62: return "esp_sent";
    default: return NULL;
    }
}

#define MAXPRESET 8
static const char *const g_modes[] = {"STOCK", "FWD", "5050", "6040", "7030", "8020",
                                      "9010", "SPEED", "THROTTLE", "MAP", "RPM"};
static const char *const g_results[] = {"ok", "catalog version mismatch", "unknown preset",
                                        "calibration running", "CAN View capture active",
                                        "preset map missing"};

static const char *mode_name(int m)
{
    return m >= 0 && m < (int)(sizeof(g_modes) / sizeof(g_modes[0])) ? g_modes[m] : "?";
}

static const char *result_name(int r)
{
    if (r == 255)
        return "unknown command";
    return r >= 0 && r < (int)(sizeof(g_results) / sizeof(g_results[0])) ? g_results[r] : "?";
}

static uint32_t now_ms(void)
{
    struct timespec ts;
    clock_gettime(CLOCK_MONOTONIC, &ts);
    return (uint32_t)(ts.tv_sec * 1000u + ts.tv_nsec / 1000000u);
}

static uint32_t rd32(const unsigned char *p)
{
    return ((uint32_t)p[0] << 24) | ((uint32_t)p[1] << 16) | ((uint32_t)p[2] << 8) | p[3];
}

static void wr32(unsigned char *p, uint32_t v)
{
    p[0] = v >> 24;
    p[1] = v >> 16;
    p[2] = v >> 8;
    p[3] = v;
}

/* One line to stdout and the capped log. */
static void out(const char *line)
{
    puts(line);
    fflush(stdout);
    if (!g_log)
        return;
    if (ftell(g_log) > LOG_CAP) {
        fclose(g_log);
        g_log = fopen(g_log_path, "w");
        if (!g_log)
            return;
        fputs("(log restarted at 256 KB)\n", g_log);
    }
    fputs(line, g_log);
    fputc('\n', g_log);
    fflush(g_log);
}

static void on_signal(int s)
{
    (void)s;
    g_stop = 1;
}

static void values_text(char *b, size_t n, const int32_t *val, const uint32_t *at, uint32_t now)
{
    size_t o = 0;
    int id;
    b[0] = 0;
    for (id = 1; id < MAXCH && o + 32 < n; id++) {
        const char *nm;
        if (!at[id] || now - at[id] > 1000)
            continue;
        nm = ch_name(id);
        {
            double v = val[id] / 1000.0;
            int dec = (v >= 100 || v <= -100) ? 0 : 2;
            if (nm)
                o += snprintf(b + o, n - o, " %s=%.*f", nm, dec, v);
            else
                o += snprintf(b + o, n - o, " ch%d=%.*f", id, dec, v);
        }
    }
}

int main(int argc, char **argv)
{
    int port = 49160, verbose = 0, ack = 1, opt, s;
    int want_preset = -1, stdin_open = 1;
    int cat_n = 0, cat_ver = -1, cat_id[MAXPRESET], cat_mode[MAXPRESET];
    char cat_name[MAXPRESET][17], inbuf[32];
    size_t inlen = 0;
    int cmd_pending = 0, cmd_id = 0, cmd_tries = 0, last_active = -1;
    uint16_t cmd_seq = 0;
    uint32_t cmd_sent_ms = 0;
    struct sockaddr_in oh; /* last TTG1 source: where TTC1 goes */
    int have_oh = 0;
    uint32_t key = 0;
    struct sockaddr_in a;
    unsigned char buf[600];
    int32_t val[MAXCH];
    uint32_t at[MAXCH];
    uint32_t rx = 0, lost = 0, bad = 0, badkey = 0, dup = 0, acks = 0;
    uint32_t sec_rx = 0, last_sum, start, last_rx_ms = 0;
    int last_seq = -1;
    char from[32] = "-", line[1024], vals[700];

    while ((opt = getopt(argc, argv, "p:k:l:vna:")) != -1) {
        switch (opt) {
        case 'p': port = atoi(optarg); break;
        case 'k': key = (uint32_t)strtoul(optarg, NULL, 0); break;
        case 'l': g_log_path = strcmp(optarg, "-") ? optarg : NULL; break;
        case 'v': verbose = 1; break;
        case 'n': ack = 0; break;
        case 'a': want_preset = atoi(optarg); break;
        default:
            fprintf(stderr, "usage: %s [-p port] [-k key] [-l log|-] [-v] [-n] [-a preset]\n", argv[0]);
            return 2;
        }
    }
    memset(val, 0, sizeof(val));
    memset(at, 0, sizeof(at));
    signal(SIGINT, on_signal);
    signal(SIGTERM, on_signal);
    if (g_log_path)
        g_log = fopen(g_log_path, "a");

    s = socket(AF_INET, SOCK_DGRAM, 0);
    if (s < 0) {
        perror("socket");
        return 1;
    }
    memset(&a, 0, sizeof(a));
    a.sin_family = AF_INET;
    a.sin_addr.s_addr = htonl(INADDR_ANY); /* uap0 is not loopback */
    a.sin_port = htons(port);
    if (bind(s, (struct sockaddr *)&a, sizeof(a)) < 0) {
        snprintf(line, sizeof(line), "bind udp %d failed: %s (port in use? try another in 49153-49162 with -p)",
                 port, strerror(errno));
        out(line);
        return 1;
    }
    start = last_sum = now_ms();
    snprintf(line, sizeof(line), "ttlink listening on udp %d, key %u, acks %s", port, (unsigned)key, ack ? "on" : "off");
    out(line);

    while (!g_stop) {
        fd_set rf;
        struct timeval tv = {0, 200000};
        uint32_t now;
        FD_ZERO(&rf);
        FD_SET(s, &rf);
        if (stdin_open)
            FD_SET(0, &rf);
        if (select(s + 1, &rf, NULL, NULL, &tv) <= 0)
            FD_ZERO(&rf);
        if (stdin_open && FD_ISSET(0, &rf)) {
            /* A preset id + Enter applies that preset. End of input (no terminal) stops reading. */
            char c;
            ssize_t r = read(0, &c, 1);
            if (r <= 0) {
                stdin_open = 0;
            } else if (c == '\n' || c == '\r') {
                inbuf[inlen] = 0;
                if (inlen)
                    want_preset = atoi(inbuf);
                inlen = 0;
            } else if (inlen + 1 < sizeof(inbuf)) {
                inbuf[inlen++] = c;
            }
        }
        if (FD_ISSET(s, &rf)) {
            struct sockaddr_in src;
            socklen_t sl = sizeof(src);
            ssize_t n = recvfrom(s, buf, sizeof(buf), 0, (struct sockaddr *)&src, &sl);
            now = now_ms();
            if (n >= 12 && !memcmp(buf, "TTG1", 4)) {
                int cnt = buf[11], i, seq = (buf[8] << 8) | buf[9];
                if (cnt < 1 || cnt > 40 || n < 12 + cnt * 6) {
                    bad++;
                } else if (rd32(buf + 4) != key) {
                    badkey++;
                } else {
                    int d = last_seq < 0 || now - last_rx_ms > 5000 ? 1 : ((seq - last_seq) & 0xFFFF);
                    if (d == 0 || d >= 0x8000) {
                        dup++; /* repeated or reordered */
                    } else {
                        lost += d - 1;
                        last_seq = seq;
                        last_rx_ms = now;
                        rx++;
                        sec_rx++;
                        snprintf(from, sizeof(from), "%s:%d", inet_ntoa(src.sin_addr), ntohs(src.sin_port));
                        oh = src;
                        have_oh = 1;
                        for (i = 0; i < cnt; i++) {
                            const unsigned char *e = buf + 12 + i * 6;
                            int id = (e[0] << 8) | e[1];
                            if (id > 0 && id < MAXCH) {
                                val[id] = (int32_t)rd32(e + 2);
                                at[id] = now ? now : 1;
                            }
                        }
                        if (at[22] == (now ? now : 1) && val[22] / 1000 != last_active) {
                            int act = val[22] / 1000, k;
                            const char *nm = act == 255 ? "none" : "?";
                            for (k = 0; k < cat_n; k++)
                                if (cat_id[k] == act)
                                    nm = cat_name[k];
                            snprintf(line, sizeof(line), "active preset %d (%s)", act, nm);
                            out(line);
                            last_active = act;
                        }
                        if (verbose) {
                            values_text(vals, sizeof(vals), val, at, now);
                            snprintf(line, sizeof(line), "rx seq=%d from %s flags=%d%s", seq, from, buf[10], vals);
                            out(line);
                        }
                    }
                    if (ack) {
                        unsigned char r[20];
                        memcpy(r, "TTA1", 4);
                        r[4] = seq >> 8;
                        r[5] = seq;
                        r[6] = r[7] = 0;
                        wr32(r + 8, rx);
                        wr32(r + 12, lost);
                        wr32(r + 16, now - start);
                        if (sendto(s, r, sizeof(r), 0, (struct sockaddr *)&src, sl) == (ssize_t)sizeof(r))
                            acks++;
                    }
                }
            } else if (n >= 11 && !memcmp(buf, "TTP1", 4)) {
                if (rd32(buf + 4) != key) {
                    badkey++;
                } else {
                    int ver = (buf[8] << 8) | buf[9], cnt = buf[10], i, o = 11, ok = 1;
                    int ids[MAXPRESET], modes[MAXPRESET];
                    char names[MAXPRESET][17];
                    if (cnt > MAXPRESET)
                        ok = 0;
                    for (i = 0; ok && i < cnt; i++) {
                        int len;
                        if (o + 3 > n) {
                            ok = 0;
                            break;
                        }
                        len = buf[o + 2];
                        if (len < 1 || len > 16 || o + 3 + len > n) {
                            ok = 0;
                            break;
                        }
                        ids[i] = buf[o];
                        modes[i] = buf[o + 1];
                        memcpy(names[i], buf + o + 3, len);
                        names[i][len] = 0;
                        o += 3 + len;
                    }
                    if (!ok) {
                        bad++;
                    } else if (ver != cat_ver) {
                        size_t lo;
                        cat_ver = ver;
                        cat_n = cnt;
                        memcpy(cat_id, ids, sizeof(ids));
                        memcpy(cat_mode, modes, sizeof(modes));
                        memcpy(cat_name, names, sizeof(names));
                        lo = snprintf(line, sizeof(line), "catalog v=0x%04x, %d presets:", ver, cnt);
                        for (i = 0; i < cnt && lo + 40 < sizeof(line); i++)
                            lo += snprintf(line + lo, sizeof(line) - lo, " %d=%s(%s)", cat_id[i], cat_name[i],
                                           mode_name(cat_mode[i]));
                        out(line);
                    }
                }
            } else if (n >= 14 && !memcmp(buf, "TTR1", 4)) {
                if (rd32(buf + 4) != key) {
                    badkey++;
                } else {
                    int seq = (buf[8] << 8) | buf[9], res = buf[10], act = buf[11];
                    int ver = (buf[12] << 8) | buf[13];
                    snprintf(line, sizeof(line), "reply cmd %d: %s (result %d), active %d, catalog v=0x%04x%s", seq,
                             result_name(res), res, act, ver,
                             cmd_pending && seq == cmd_seq ? "" : " (not the pending command)");
                    out(line);
                    if (cmd_pending && seq == cmd_seq)
                        cmd_pending = 0;
                }
            } else if (n > 0) {
                bad++;
            }
        }
        now = now_ms();
        /* Preset apply: needs a catalog and a sender to reply to. Same seq for retries. */
        if (want_preset >= 0 && cat_ver >= 0 && have_oh && !cmd_pending) {
            cmd_id = want_preset;
            want_preset = -1;
            cmd_seq++;
            cmd_tries = 0;
            cmd_pending = 1;
            cmd_sent_ms = now - 1000;
        }
        if (cmd_pending && now - cmd_sent_ms >= 500) {
            if (cmd_tries >= 3) {
                snprintf(line, sizeof(line), "cmd %d (apply %d): no reply after 3 tries", cmd_seq, cmd_id);
                out(line);
                cmd_pending = 0;
            } else {
                unsigned char c[14];
                memcpy(c, "TTC1", 4);
                wr32(c + 4, key);
                c[8] = cmd_seq >> 8;
                c[9] = cmd_seq;
                c[10] = 1;
                c[11] = cmd_id;
                c[12] = cat_ver >> 8;
                c[13] = cat_ver;
                sendto(s, c, sizeof(c), 0, (struct sockaddr *)&oh, sizeof(oh));
                snprintf(line, sizeof(line), "cmd %d: apply preset %d (catalog v=0x%04x)%s", cmd_seq, cmd_id,
                         cat_ver, cmd_tries ? " retry" : "");
                out(line);
                cmd_tries++;
                cmd_sent_ms = now;
            }
        }
        now = now_ms();
        if (now - last_sum >= 1000) {
            uint32_t idle = last_rx_ms ? now - last_rx_ms : now - start;
            if (sec_rx) {
                values_text(vals, sizeof(vals), val, at, now);
                snprintf(line, sizeof(line), "%5us %s rate=%u/s rx=%u lost=%u dup=%u bad=%u badkey=%u acks=%u |%s",
                         (unsigned)((now - start) / 1000), from, (unsigned)sec_rx, (unsigned)rx, (unsigned)lost,
                         (unsigned)dup, (unsigned)bad, (unsigned)badkey, (unsigned)acks, vals);
                out(line);
            } else if (idle >= 3000 && (idle / 1000) % 5 == 0) {
                snprintf(line, sizeof(line), "%5us waiting (nothing for %us; bad=%u badkey=%u)",
                         (unsigned)((now - start) / 1000), (unsigned)(idle / 1000), (unsigned)bad, (unsigned)badkey);
                out(line);
            }
            sec_rx = 0;
            last_sum += 1000;
            if (now - last_sum > 1000)
                last_sum = now;
        }
    }
    snprintf(line, sizeof(line), "ttlink stop: rx=%u lost=%u dup=%u bad=%u badkey=%u acks=%u", (unsigned)rx,
             (unsigned)lost, (unsigned)dup, (unsigned)bad, (unsigned)badkey, (unsigned)acks);
    out(line);
    if (g_log)
        fclose(g_log);
    close(s);
    return 0;
}
