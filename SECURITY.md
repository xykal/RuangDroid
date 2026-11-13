# HyperDeX Security & Anti-Cloning Architecture

Repositori **HyperDeX** bersifat terbuka (*open-source*) agar komunitas dapat mempelajari arsitektur desktop compositor dan integrasi sistemnya, namun **rilis biner resmi (`HyperDeX-release.apk`) dilindungi terhadap kloning, modifikasi paket, dan pendistribusian ulang ilegal** tanpa perlu mengubah repositori menjadi *private*.

## Lapisan Pertahanan Tanpa Private Repo

1. **Private Release Keystore di GitHub Actions Secrets (`4096-bit RSA PKCS#12`)**
   - Kunci privat penandatangan APK disimpan terenkripsi di **GitHub Actions Secrets** milik `xykal/HyperDeX`.
   - Siapa pun yang melakukan *Fork* atau `git clone` **tidak memiliki akses** ke GitHub Secrets tersebut, sehingga hasil build mereka memiliki sidik jari sertifikat X.509 yang berbeda.

2. **3-Layer Polyglot Native Attestation (`Rust` + `C11` + `C++20`)**
   - **`librust_hypercore.so` (Rust 2021):** Menghitung hash **FIPS 180-4 SHA-256** secara langsung atas byte DER sertifikat APK serta memverifikasi `HMAC-SHA256` *compile-time build seal* dalam *constant-time* (`ct_eq`).
   - **`libhyper_sys_c.so` (C11):** Memeriksa `/proc/self/cmdline`, `/proc/self/status` (`TracerPid`), dan `/proc/self/maps` guna mendeteksi *App Cloner*, *Virtual Space*, atau perubahan identitas proses di luar `dev.xykal.hyperdex`.
   - **`libhyper_gfx_cc.so` (C++20):** Memvalidasi *quorum* lintas modul native; jika salah satu `.so` dihapus, diganti, atau APK ditandatangani ulang (misal via *MT Manager* / *APKTool*), aplikasi otomatis masuk ke **HyperShield Hard Lock**.

3. **R8 Full Mode Obfuscation & LLVM Symbol Stripping**
   - Build rilis mengaktifkan `isMinifyEnabled = true`, `isShrinkResources = true`, serta `llvm-strip --strip-unneeded` pada seluruh binary `.so` (`arm64-v8a`, `armeabi-v7a`, `x86_64`).

4. **Perlindungan Hukum Lisensi Copyleft Ketat & Merek**
   - Kode dilisensikan di bawah **GNU AGPLv3** dengan kewajiban atribusi penuh ke `https://github.com/xykal/HyperDeX`. Pendistribusian kloning tertutup (*closed-source rebrand*) merupakan pelanggaran lisensi dan hak cipta.
