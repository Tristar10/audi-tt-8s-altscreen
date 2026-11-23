/*
 * ttlink: OpenHaldex <-> MMI Wi-Fi link test, head-unit side (QNX 6.5, also builds on Linux).
 *
 * Listens for "TTG1" telemetry datagrams from the ESP32 (a client on the MMI hotspot, uap0),
 * logs them, and answers each one with a "TTA1" ack so the ESP32 can measure round trip and loss.
 * Standalone: no HMI jar, no restart. Run over SSH, stop with Ctrl+C.
 *
 *   ttlink [-p port] [-k key] [-l log] [-v] [-n]
 *     -p  UDP port, default 49160. The stock firewall already passes UDP 49153-49162 from hotspot
 *         clients to uap0 (pf.conf "WLAN" section), so no firewall change is needed.
 *     -k  key the datagrams must carry, default 0
 *     -l  log file, default /tmp/ttlink.log (capped at 256 KB), "-" for none
 *     -v  one line per datagram (default: one summary per second)
 *     -n  no acks
 *
 * TTG1 (ESP32 -> head unit), big-endian:
 *   0 "TTG1" | 4 key u32 | 8 seq u16 | 10 flags u8 | 11 count u8 | 12 count * (u16 id, s32 value*1000)
 * TTA1 (head unit -> ESP32), big-endian, 20 bytes:
 *   0 "TTA1" | 4 seq u16 (echoed) | 6 reserved u16 | 8 rx u32 | 12 lost u32 | 16 uptime ms u32
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
    case 60: return "esp_up_s";
    case 61: return "rssi";
    case 62: return "esp_sent";
    default: return NULL;
    }
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
    uint32_t key = 0;
    struct sockaddr_in a;
    unsigned char buf[600];
    int32_t val[MAXCH];
    uint32_t at[MAXCH];
    uint32_t rx = 0, lost = 0, bad = 0, badkey = 0, dup = 0, acks = 0;
    uint32_t sec_rx = 0, last_sum, start, last_rx_ms = 0;
    int last_seq = -1;
    char from[32] = "-", line[1024], vals[700];

    while ((opt = getopt(argc, argv, "p:k:l:vn")) != -1) {
        switch (opt) {
        case 'p': port = atoi(optarg); break;
        case 'k': key = (uint32_t)strtoul(optarg, NULL, 0); break;
        case 'l': g_log_path = strcmp(optarg, "-") ? optarg : NULL; break;
        case 'v': verbose = 1; break;
        case 'n': ack = 0; break;
        default:
            fprintf(stderr, "usage: %s [-p port] [-k key] [-l log|-] [-v] [-n]\n", argv[0]);
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
        if (select(s + 1, &rf, NULL, NULL, &tv) > 0) {
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
                        for (i = 0; i < cnt; i++) {
                            const unsigned char *e = buf + 12 + i * 6;
                            int id = (e[0] << 8) | e[1];
                            if (id > 0 && id < MAXCH) {
                                val[id] = (int32_t)rd32(e + 2);
                                at[id] = now ? now : 1;
                            }
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
            } else if (n > 0) {
                bad++;
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
