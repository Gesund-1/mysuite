# MySuite Core 1.0.0

Aplikasi Android modular berbasis Kotlin + WebView. Minimum Android 7.0 (API 24), target ARM/ARM64 mengikuti perangkat dan toolchain Android. Core tidak membundel plugin.

## Build APK
1. Buka folder `mysuite-core` menggunakan Android Studio terbaru.
2. Gunakan JDK 17 dan Android SDK Platform 35.
3. Sync Gradle, lalu Build > Build APK(s).
4. APK debug tersedia di `app/build/outputs/apk/debug/`.

## Instalasi plugin
Buka MySuite → Import ZIP → pilih file plugin. ZIP harus berisi `manifest.json` dan `web/index.html`. Data disimpan di `MySuite.db`.

## Catatan keamanan & batasan rilis awal
- Importer membatasi ukuran ekstraksi 30 MB dan menolak path traversal.
- Plugin web dijalankan di WebView lokal. Pasang hanya ZIP tepercaya; plugin bukan sandbox keamanan penuh.
- Schema DB sudah menyiapkan tabel Music, Vault, Finance dan penyimpanan key/value plugin.
- Audio file picker dan pemindahan audio ke penyimpanan internal belum menjadi bagian build awal ini.
- Uji pada Android 7–14, perangkat ARMv7/ARM64, dan lakukan backup DB sebelum migrasi data lama.
