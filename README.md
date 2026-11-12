# RuangDroid Desktop OS (`v3.0.0`)

Launcher Android Full-Window bergaya Desktop PC / Windows 11 tanpa dialog/komponen UI bawaan Android, lengkap dengan **Custom Vector Icon Engine**, **Animasi Fluent & Aero Snap**, **Multi-DPI/Resolution Scaling**, serta **Integrasi Resmi Shizuku & Termux**.

## Pembaruan Utama `v3.0.0`
1. **100% Custom Desktop UI (Tanpa Dialog/Popup Bawaan Android):**
   - Menghapus seluruh `AlertDialog`, `Dialog`, `SeekBar`, `CheckBox`, dan `Toast` bawaan Android.
   - Start Menu, Quick Action Center, Context Menu klik-tahan, Input Prompt, Multi-Picker, Slider Volume/Skala, dan **Windows 11 Toast Notification** dirender murni di dalam *Custom Desktop Overlay Compositor*.
2. **Procedural Vector Icon Engine & Animasi 60/120Hz (`VectorIcons.kt`):**
   - Seluruh ikon sistem, tombol kontrol jendela (`Minimize`, `Maximize/Restore`, `Aero Snap`, `Close`), Start Orb, dan 11 aplikasi bawaan digambar vektor tajam dengan `LruCache` (tanpa emoji).
   - Animasi *spring* saat buka/tutup/minimize/restore jendela, efek pantul sentuhan (*press bounce*), serta **Live Aero Snap Ghost Preview** saat menyeret jendela ke tepi layar kiri/kanan/atas.
   - Akselerasi `LAYER_TYPE_HARDWARE` otomatis saat *drag/resize* jendela.
3. **Responsif di Semua Resolusi, Rasio Layar & Custom DPI:**
   - Unit skala hibrida otomatis menyesuaikan dimensi layar pendek & kerapatan piksel + penggunaan `COMPLEX_UNIT_PX` agar ukuran font sistem Android tidak merusak proporsi jendela.
   - Dilengkapi **Slider Skala UI Desktop (`75%`–`135%`)** langsung di *Control Panel*.
4. **Integrasi Shizuku (`rikka.shizuku`), Termux (`RUN_COMMAND`) & Custom System Floating HUD:**
   - **Shizuku Bridge Resmi:** Buka kunci *Global Freeform Multi-Window* untuk semua aplikasi Android, ubah DPI layar sistem (`wm density`), luncurkan aplikasi via `am start --windowingMode 5`, dan jalankan perintah ADB (`rish <cmd>`) langsung dari Terminal maupun aplikasi **Shizuku & Termux Hub**.
   - **Termux & Termux:X11 Bridge:** Kirim perintah bash langsung ke `RunCommandService` Termux dari Terminal (`termux <cmd>`) atau Shizuku Hub.
   - **Custom System-Wide Floating PC HUD (`SYSTEM_ALERT_WINDOW`):** Jendela mengambang kustom RuangDroid yang tetap tampil di atas aplikasi eksternal (game/chat/Winlator) tanpa bingkai bawaan Android.
