# RuangDroid

Launcher Android native bergaya desktop.

## Fitur
- Bisa dipilih sebagai aplikasi Home/launcher bawaan Android.
- Desktop dengan jam, tanggal, shortcut aplikasi, dan taskbar.
- App drawer berisi aplikasi yang bisa diluncurkan, dengan pencarian.
- Dibuat dengan Kotlin tanpa library UI tambahan.

## Build lokal
1. Buka folder ini di Android Studio.
2. Gunakan JDK 17, Android SDK 35, Android Gradle Plugin 8.6.1, dan Gradle 8.7.
3. Jalankan konfigurasi `app` pada emulator atau perangkat Android.
4. Tekan Home, pilih **RuangDroid**, lalu pilih **Selalu** untuk menjadikannya launcher default.

## Build release lewat GitHub Actions
- Jalankan workflow **Android release** secara manual dari tab Actions untuk membangun APK dan menyimpannya sebagai artifact.
- Untuk menerbitkan GitHub Release, push tag versi, misalnya `v1.0.0`. APK akan dilampirkan otomatis ke release.

CI menandatangani APK dengan debug key agar hasilnya dapat dipasang untuk pengujian. Itu bukan kunci rilis privat yang layak dipakai mendistribusikan aplikasi secara publik. Sebelum rilis publik, ganti konfigurasi signing dengan keystore privat dan simpan kredensial sebagai GitHub Actions Secrets.

> `QUERY_ALL_PACKAGES` dipakai agar app drawer dapat menemukan aplikasi launcher yang terpasang. Android tetap mengatur status bar, navigasi, dan izin sistem; ini bukan pengganti desktop OS.
