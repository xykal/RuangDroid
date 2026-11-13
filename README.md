# HyperDeX (`v4.0.0`) — Polyglot Android DeX / PC Desktop OS

**HyperDeX** (`dev.xykal.hyperdex`) adalah sistem operasi launcher *Full-Window* bergaya PC Desktop / Samsung DeX / Windows 11 untuk Android yang dibangun dengan arsitektur **Polyglot 4 Bahasa (`Kotlin` + `Rust` + `C11` + `C++20`)**, nol dialog bawaan Android, integrasi **Shizuku & Termux**, serta proteksi **HyperShield Anti-Clone**.

---

## Arsitektur Polyglot Custom Native Libraries (`v4.0.0`)

| Modul | Bahasa | Output Binary | Fungsi Utama |
|---|---|---|---|
| **Desktop Compositor** | Kotlin | `classes.dex` (R8) | 4-Layer Full-Window UI, Aero Snap, Vector Icon Engine, Shizuku/Termux IPC |
| **`rust_hypercore`** | **Rust 2021** | `librust_hypercore.so` | Constant-Time X.509 SHA-256 & HMAC-SHA256 Anti-Clone Attestation, File/Text SHA-256, Scientific Math Parser |
| **`hyper_sys_c`** | **Pure C11** | `libhyper_sys_c.so` | Linux Kernel `/proc/stat` & `/proc/meminfo` Telemetry, CPU Core Clocks, `/proc/self/maps` Anti-Cloner Guard, CRC32/FNV1a-64 |
| **`hyper_gfx_cc`** | **Modern C++20** | `libhyper_gfx_cc.so` | Separable 2-Pass Acrylic StackBlur (`jnigraphics`), PE32/PE64 `.EXE` Binary Dissector + Shannon Entropy, Build Quorum |

---

## Fitur Unggulan
- **100% Custom Full-Window UI:** Tanpa `AlertDialog`, `SeekBar`, `CheckBox`, atau *popup* kaku bawaan Android.
- **Responsif Semua Layar & Custom DPI:** Hybrid viewport/density scaling + slider skala UI `75%`–`135%`.
- **HyperShield Anti-Clone Hard Lock:** Pinning sertifikat rilis resmi `4096-bit RSA` + `HMAC-SHA256` CI Seal via GitHub Actions Secrets di dalam binary Rust, C11, dan C++20.
- **Shizuku + Termux + Custom System Floating HUD:** Unlock *Global Freeform Multi-Window*, peluncur aplikasi `--windowingMode 5`, pengubah DPI sistem (`wm density`), eksekusi `rish` & `termux`, serta jendela mini PC melayang di atas semua aplikasi.
- **12 Aplikasi PC Bawaan:** *This PC (Explorer)*, *HyperShield & Native Core*, *Shizuku & Termux Hub*, *EXE & Wine Plugin Center*, *Desktop Web Browser*, *Polyglot Terminal CMD*, *Notepad*, *Rust Scientific Calculator*, *Paint Studio (C++ Blur)*, *Media Player*, *Task Manager*, dan *Control Panel*.

## Download Rilis Resmi
Unduh APK resmi yang telah ditandatangani kunci kriptografi asli di halaman **[Releases](https://github.com/xykal/HyperDeX/releases)**.
