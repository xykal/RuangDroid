# RuangDroid Desktop OS (`v2.0.0`)

Launcher Android Semi-Desktop bergaya PC / Windows 11 dengan **Multi-Window Floating Compositor**, **10 Aplikasi PC Bawaan Lengkap**, dan **Subsistem EXE & Plugin Wine/Winlator**.

## Apa yang Baru di `v2.0.0`
1. **Perbaikan Total Anti-Force-Close (Crash Guard):**
   - Menghapus pemanggilan `WindowInsetsController` sebelum `DecorView` terpasang ke window (penyebab crash saat startup pada ROM MIUI/HyperOS, ColorOS, OneUI, dan Android 11–15).
   - Pemindaian ikon aplikasi Android berjalan **asinkron di background thread** dengan *bitmap downscaling* aman + proteksi `try/catch` per aplikasi.
   - Dilengkapi **Recovery Mode (`CrashActivity`)** otomatis bila ada anomali perangkat.
2. **Engine Multi-Window Floating PC Asli:**
   - Buka banyak jendela sekaligus di atas desktop!
   - **Drag Title Bar** untuk menggeser jendela, **Resize Corner (`◢`)** di kanan bawah untuk mengubah ukuran bebas, **Aero Snap (`◧` / `◨`)** untuk belah layar kiri-kanan 50:50, **Minimize (`—`)** ke Taskbar, **Maximize (`▢`)** atau ketuk 2x title bar, dan **Close (`✕`)**.
   - Mendukung percobaan peluncuran **Freeform Window** untuk aplikasi Android terpasang.
3. **10 Aplikasi PC Bawaan Lengkap & Berfungsi Penuh:**
   - 🗂️ **This PC (File Explorer):** Drive virtual `C:\` + akses penyimpanan Android, buat file/folder, copy/paste, rename, delete, klik langsung buka file sesuai tipe.
   - 🍷 **EXE & Wine Plugin Center:** Import & install file `.exe` dari penyimpanan HP ke `C:\Program Files` & Desktop, **PE32/PE64 Binary Header & DLL Inspector** asli, paket `.exe` siap main bawaan (`Minesweeper_XP.exe`, `Arkanoid_DX.exe`, `WinCalc_Sci.exe`), serta konektor otomatis ke engine kontainer x86 (**Winlator / WineHQ / Box64**).
   - 🌐 **Desktop Web Browser:** Mode User-Agent PC Windows 10 x64, omnibox pencarian, bookmark, navigasi lengkap.
   - 📝 **Notepad / Code Editor:** Buka/simpan file `.txt`/`.bat`, hitung baris/kata/karakter, zoom font.
   - `>_` **Command Prompt (CMD & Linux Shell):** Mendukung perintah DOS (`dir`, `cd`, `type`, `sysinfo`, `tasklist`, `start`, `wine`) maupun perintah shell Linux Android (`uname -a`, `df -h`).
   - 🧮 **Scientific Calculator:** Parser ekspresi matematika (`sin`, `cos`, `√`, `^`, tanda kurung).
   - 🎨 **Paint Studio:** Kanvas gambar multi-warna, ketebalan kuas, *undo*, *eraser*, dan simpan PNG ke `C:\Users\Administrator\Pictures`.
   - 🎵 **Media Player:** Pemutar file audio lokal + *Built-in PC Synth Deck* siap bunyi.
   - 📊 **Task Manager:** Grafik *real-time* RAM + kontrol fokus / *End Task* semua jendela aktif.
   - ⚙️ **Control Panel:** 6 tema warna desktop + **pasang foto wallpaper dari Galeri HP**, ukuran ikon, mode PC Landscape / Auto, dan Fullscreen.
