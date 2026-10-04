# NET CUT — Android App

Aplikasi Android native (Kotlin + Jetpack Compose + Material 3) untuk
monitor & kontrol device di LAN — port dari versi web (Termux/Python).

## Fitur

- **Scan LAN** — ARP scan via tool native `arpkit` (root)
- **Monitor bandwidth** — counter kernel (`iptables` chain NETCUT_ACC) per device
- **Block / unblock** — iptables DROP + ARP MITM
- **Speed limit** — `tc` HTB per-IP (64 kbps – 8 Mbps)
- **MITM toggle** — ARP poison unicast via daemon `arpkit`
- **Dark / light mode** — tersimpan di SharedPreferences
- **Splash screen** — androidx core-splashscreen
- **Auto network watch** — ganti WiFi = auto reset + rescan

## Build

```bash
./gradlew assembleRelease
# output: app/build/outputs/apk/release/app-release.apk
```

CI: push ke `main` → GitHub Actions build otomatis (artifact: `NETCUT-Release.apk`).

## Stack

| Komponen | Pilihan |
|---|---|
| Language | Kotlin 2.0.21 |
| UI | Jetpack Compose + Material 3 |
| Arsitektur | MVVM (ViewModel + StateFlow + Coroutines) |
| Min SDK | 26 (Android 8.0) |
| Target SDK | 35 |
| Native | `arpkit` (C, AF_PACKET ARP scan/poison) — diekstrak dari asset, dieksekusi via `su` |
| Signing | `app/netcut.keystore` (debug-friendly, ganti utk production) |

## Kebutuhan runtime

- HP **rooted** (Magisk / KernelSU) — semua operasi network butuh `su`
- Terhubung ke WiFi (LAN yang sama dengan target)

Tanpa root, app tetap terbuka tanpa crash — panel menampilkan pesan
"Root belum diberikan".

## Struktur

```
app/src/main/
├── java/com/itschandra/netcut/
│   ├── MainActivity.kt            # entry + splash
│   ├── data/
│   │   ├── Models.kt              # NetInfo, Device, DashboardUiState
│   │   ├── RootShell.kt           # wrapper exec su
│   │   ├── ArpKit.kt              # extract + run binary native
│   │   └── NetcutRepository.kt    # orchestrator (scan/acc/block/limit/mitm)
│   ├── vm/DashboardViewModel.kt   # StateFlow + polling coroutines
│   └── ui/
│       ├── theme/Theme.kt         # warna light/dark (konsisten dgn web)
│       └── DashboardScreen.kt     # seluruh UI
├── cpp/arpkit.c                   # tool ARP native (C)
├── assets/bin/arpkit              # binary prebuilt arm64
└── res/                           # icon adaptive, themes, splash
```

## Credits

**itschandra** — [instagram.com/itschandra_28](https://www.instagram.com/itschandra_28)
