# RuangDroid

Launcher Android native bergaya Semi-Desktop (Windows 11 / Samsung DeX).

## Fitur (`v1.1.0`)
- **Mode PC / Landscape Otomatis:** Default berjalan dalam mode landscape ala PC/DeX (bisa diganti kapan saja ke mode Auto/Hybrid langsung dari Quick Panel).
- **Panel Quick Settings Kanan Bawah:** Klik area jam/baterai di kanan bawah taskbar untuk membuka panel kontrol ala Windows 11 (Wi-Fi, Bluetooth, saklar Mode PC/Landscape, Fullscreen Immersive, ganti 4 Tema Desktop, kecerahan layar, slider volume media langsung, serta monitor RAM & Storage).
- **Folder di Desktop:** Pengelompokan aplikasi otomatis ke folder desktop (`Sistem & Alat`, `Internet & Media`, `Kerja & Sosial`) plus fitur buat, ubah nama, hapus, dan kelola isi folder lewat jendela pop-up ala Explorer.
- **Taskbar & Pin Aplikasi:** Start Menu di pojok kiri bawah dengan pencarian real-time, deretan aplikasi yang di-pin di taskbar, dan menu konteks saat klik-tahan ikon aplikasi (Pin/Unpin Taskbar, Shortcut Desktop, Masukkan ke Folder, App Info, Uninstall).
- **Ringan & Tanpa Dependency Tambahan:** Ditulis murni menggunakan Kotlin & Android SDK bawaan.

## Build lokal
1. Buka folder ini di Android Studio.
2. Gunakan JDK 17, Android SDK 35, Android Gradle Plugin 8.6.1, dan Gradle 8.7.
3. Jalankan pada perangkat Android atau emulator, lalu pilih **RuangDroid** saat menekan tombol Home.

## Build release lewat GitHub Actions
- Setiap push ke `main` otomatis membangun artifact `RuangDroid-release.apk`.
- Push tag versi seperti `v1.1.0` otomatis menerbitkan **GitHub Release** beserta file APK siap pasang.
