# HyperDeX (`v5.0.0` — Ultimate Polyglot Android Desktop OS)

**HyperDeX** (`dev.xykal.hyperdex`) adalah **PC / DeX Desktop OS Launcher untuk Android** dengan arsitektur **Polyglot Native 4-Bahasa** (**Kotlin + Rust 2021 + Pure C11 + Modern C++20 & OpenGL ES 3.0**), **100% Custom Full-Window UI** (tanpa dialog/toast bawaan Android), **Virtual Desktops (`[1] [2] [3]`)**, **Hardware Keyboard & Mouse DeX Shortcuts**, **Shizuku + Termux Bridge**, serta **4-Layer Native Anti-Clone Hard Lock**.

---

## Yang Baru di `v5.0.0` (Ultimate OS, Gaming Boost & Anti-Tamper Upgrade)

| Fitur `v5.0.0` | Detail Implementasi |
| :--- | :--- |
| **OpenGL ES 3.0 GPU Live Shader Wallpaper** | Shader plasma aurora real-time 60fps di GPU langsung dari **C++20 (`libhyper_gfx_cc.so` / `GLESv3`)**, hemat baterai (auto-pause saat background) & sinkron warna tema. |
| **Virtual Desktops (`Workspace 1 / 2 / 3`)** | Switcher `[1] [2] [3]` langsung di Taskbar Windows 11 + klik kanan tab jendela untuk memindahkan jendela antar desktop. |
| **Hardware DeX Mouse & Keyboard** | Dukungan **Klik Kanan Mouse Fisik** (`BUTTON_SECONDARY`), **`Alt + Tab`** (pindah fokus jendela), **`Win` / `Ctrl + Esc`** (Start Menu), **`Ctrl + 1/2/3`** (ganti Virtual Desktop), **`Ctrl + Alt + T`** (Terminal), dan **`Ctrl + Alt + Del`** (Task Manager). |
| **Native `.ZIP` Archiver & Inspector** | Klik kanan file/folder di File Explorer untuk **Kompres ke `.ZIP`**, inspeksi isi `.ZIP` lewat **Rust (`librust_hypercore.so`)**, dan **Ekstrak `.ZIP`** berpelindung anti-*Zip-Slip*. |
| **PC `.EXE` Gaming Pad & Box64/X11 Boost** | Game baru **`SpaceShooter_DX.exe`** dengan **Virtual Gaming Pad (`A`/`D`/`SPACE`)**, tombol **1-Click Setup Termux Box64/X11**, **Shizuku Fixed Performance Mode**, serta bar tombol PC (`ESC`/`TAB`/`SPC`/`ENT`) di **System Floating HUD**. |
| **4-Layer XOR-Obfuscated Anti-Clone Hard Lock** | Seluruh pin kriptografi di `librust_hypercore.so` & `libhyper_gfx_cc.so` dienkripsi **Rolling-XOR** (nol string hash polos di `.so`), ditambah pemeriksa **`APK Sig Block 42` (V2/V3) + `classes.dex` CRC32** langsung di C11 (`libhyper_sys_c.so`). |
| **Owner & Token Locked GitHub Actions** | Eksekusi GitHub Actions dikunci ketat hanya untuk pemilik resmi (`xykal`) & pemegang token/secret terotorisasi (`ACTION_OWNER_GUARD`). |

---

## Arsitektur 3 Custom Native Libraries (`.so`)

1. **`librust_hypercore.so` (`rust_hypercore/src/lib.rs` — Rust 2021)**
   - **Rolling-XOR Secret Shield**: Memverifikasi sertifikat X.509 RSA-4096 + `HMAC-SHA256` CI Build Seal di memori tanpa menyisakan string hash di binary `.so`, lalu menghapus kunci dengan volatile zeroize.
   - **Streaming SHA-256 & ZIP Archive Inspector (`rustInspectZipArchive`)**: Menghitung hash file besar dan membedah Central Directory `.zip` dengan aman.
   - **Scientific Math Parser**: Evaluator ekspresi matematika rekursif (`sin`, `cos`, `tan`, `sqrt`, `log`, `ln`, `pi`, `^`) untuk Calculator & Native Lab.
2. **`libhyper_sys_c.so` (`app/src/main/cpp/hyper_sys.c` — Pure C11)**
   - **Direct APK V2/V3 & `classes.dex` Integrity Guard (`cInspectApkIntegrity`)**: Membedah langsung biner `base.apk` untuk memvalidasi blok tanda tangan `APK Sig Block 42` dan CRC32 `classes.dex`.
   - **Anti-Hook & Anti-Clone Scanner (`cVerifyProcessEnv`)**: Memeriksa `/proc/self/cmdline`, `TracerPid`, dan `/proc/self/maps` terhadap injeksi Frida, Substrate, Dobby, dan AppCloner/ParallelSpace.
   - **Kernel Telemetry**: Membaca langsung `/proc/stat`, `/proc/meminfo`, `/proc/loadavg`, `sysinfo()`, dan suhu thermal SoC.
3. **`libhyper_gfx_cc.so` (`app/src/main/cpp/hyper_gfx.cc` — C++20 + OpenGL ES 3.0)**
   - **GPU Live Shader Renderer (`ccGlInit` / `ccGlRenderFrame`)**: Mesin shader GLSL `#version 300 es` untuk Live Aurora Wallpaper.
   - **Acrylic Frosted StackBlur (`ccAcrylicBlurBitmap`)**: Separable StackBlur langsung pada piksel `AndroidBitmap`.
   - **PE32/PE32+ Binary Dissector (`ccAnalyzePeBinary`)**: Membaca header DOS/COFF/Subsystem dan entropi Shannon file `.exe`.

---

## Download APK Resmi

Unduh **`HyperDeX-release.apk`** resmi yang telah ditandatangani dengan kunci RSA-4096 resmi Xykal langsung dari halaman **[GitHub Releases](https://github.com/xykal/HyperDeX/releases)**.
