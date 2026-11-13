# HyperDeX Security, Anti-Clone & CI Token Policy (`v5.0.0`)

## 1. 4-Layer Polyglot Anti-Clone Hard Lock (`strict_lock`)

`HyperDeX` tetap *open-source* di bawah lisensi MIT, namun setiap rilis resmi (`HyperDeX-release.apk`) dilindungi oleh **4-Layer Native Quorum**:

1. **Rust Rolling-XOR Cert & Seal Guard (`librust_hypercore.so`)**: Memvalidasi SHA-256 sertifikat X.509 RSA-4096 resmi Xykal serta `HMAC-SHA256` CI Build Seal. Pin rahasia disimpan dalam bentuk byte array ter-obfuscate Rolling-XOR dan di-*zeroize* dari stack setelah verifikasi.
2. **C11 Process & Hook Guard (`libhyper_sys_c.so`)**: Memastikan `/proc/self/cmdline` identik dengan `dev.xykal.hyperdex` dan memindai `/proc/self/maps` terhadap framework cloning/hooking (`frida-agent`, `frida-gadget`, `substrate`, `dobby`, `sandhook`, `appcloner`, `parallel`, `virtualapp`). Pengguna Magisk/KernelSU/Shizuku normal **tidak** diblokir selama tanda tangan APK asli tidak dimodifikasi.
3. **C11 Direct APK V2/V3 & DEX CRC32 Guard (`libhyper_sys_c.so`)**: Memeriksa langsung file APK terpasang (`sourceDir`) pada level struktur ZIP EOCD untuk memastikan keberadaan `APK Sig Block 42` (APK Signature Scheme v2/v3) dan keutuhan `classes.dex`.
4. **C++20 Rolling-XOR Quorum + R8 Obfuscation (`libhyper_gfx_cc.so`)**: Verifikasi silang independen ditambah *minification* & *resource shrinking* penuh lewat Android R8.

## 2. GitHub Actions Owner & Authorized Token Lockdown

Sesuai kebijakan keamanan repositori `xykal/HyperDeX`:
- Default permission `GITHUB_TOKEN` pada level repositori dikunci ke **`read`** dan persetujuan PR otomatis dinonaktifkan.
- Workflow `.github/workflows/android-release.yml` menerapkan `permissions: {}` di level root dan hanya mengeksekusi job rilis apabila:
  1. `github.repository == 'xykal/HyperDeX'` & `github.repository_owner == 'xykal'`
  2. `github.actor == 'xykal'` & `github.triggering_actor == 'xykal'` (eksekusi oleh pemilik atau melalui Personal Access Token resmi milik pemilik)
  3. Gate rahasia `ACTION_OWNER_GUARD`, `RELEASE_KEYSTORE_B64`, dan `OFFICIAL_HMAC_PEPPER` terverifikasi aktif sebelum *checkout* kode.
