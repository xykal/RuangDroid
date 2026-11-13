# HyperDeX (`v6.0.0` — Graphite Micro Desktop OS & Custom Floating Engine)

**HyperDeX** (`dev.xykal.hyperdex`) adalah **PC / DeX Desktop OS Launcher untuk Android** dengan estetika **Graphite Mica presisi (Zero AI-Slop)**, **ukuran UI micro-compact yang nyaman di semua layar**, **Custom Floating Window Engine** (tanpa bar freeform bawaan Android), **Splash Screen + Onboarding + Default Launcher Auto-Lock**, serta arsitektur **Polyglot Native 4-Bahasa** (**Kotlin + Rust 2021 + Pure C11 + Modern C++20 & OpenGL ES 3.0**).

---

## Yang Baru di `v6.0.0`

| Fitur `v6.0.0` | Detail Implementasi |
| :--- | :--- |
| **Graphite Mica Micro UI (Zero AI-Slop)** | Desain ulang total menghilangkan gradien neon/kotak tebal: palet **Graphite Mica / Carbon Slate / Obsidian**, border hairline `1px`, ikon desktop transparan tanpa kotak abu-abu, taskbar tipis `34dp`, serta skala dasar micro (`84%` default, slider bebas `55%–130%` + preset `60%/72%/84%/96%/110%` di Control Panel). |
| **Custom Floating Window Engine** | Tiga mode jendela custom tanpa UI freeform bawaan Android: **(1)** jendela desktop `25dp` Graphite dengan tombol opasitas (`◐`) & **Pop-Out OS-Wide (`⧉`)**; **(2)** `SystemFloatingOverlay` (`TYPE_APPLICATION_OVERLAY`) multi-window bebas geser/resize di atas aplikasi apa pun; **(3)** **Custom Floating Top Bar** & **Custom Web/Cloud App Container** untuk aplikasi terpasang. |
| **Logo Baru Isometric Titanium `HX`** | Android Adaptive Icon (`@mipmap/ic_launcher` + `@mipmap/ic_launcher_round` + `monochrome` Android 13+) dengan lambang pisau isometrik Titanium `HX` + aksen Cyan. |
| **Boot Splash + Onboarding + Default Launcher Lock** | Saat pertama kali dibuka menampilkan **Splash Screen animasi** lalu **Onboarding Setup** sebelum set Default Home. Begitu dijadikan **Default Home Launcher**, sistem **otomatis mengunci di HyperDeX** (tombol Home/Back & aplikasi launcher bawaan HP diblokir penuh) sampai pengguna membuka kunci (opsional dengan **PIN**) dan memilih ganti ke launcher bawaan di Control Panel / Start Menu. |

---

## Arsitektur 3 Custom Native Libraries (`.so`)

1. **`librust_hypercore.so` (`rust_hypercore/src/lib.rs` — Rust 2021)** — Rolling-XOR Secret Shield, Streaming SHA-256, ZIP Archive Inspector, dan Scientific Math Parser.
2. **`libhyper_sys_c.so` (`app/src/main/cpp/hyper_sys.c` — Pure C11)** — Direct APK V2/V3 & `classes.dex` Integrity Guard, Anti-Hook/Anti-Clone `/proc` Scanner, dan Kernel Telemetry.
3. **`libhyper_gfx_cc.so` (`app/src/main/cpp/hyper_gfx.cc` — C++20 + OpenGL ES 3.0)** — GPU Live Shader Renderer, Acrylic StackBlur, dan PE32/PE32+ `.EXE` Binary Dissector.

---

## Download APK Resmi

Unduh **`HyperDeX-release.apk`** resmi langsung dari halaman **[GitHub Releases](https://github.com/xykal/HyperDeX/releases)**.
