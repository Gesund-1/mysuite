# MySuite 1.1.0

Aplikasi Android modular (minSdk 24, Java/Kotlin target 17).

## Pembaruan
- Core Android dengan Plugin Manager: **Import**, **Update**, dan **Hapus** plugin. Update dipasang melalui staging dan rollback agar versi lama dipulihkan jika pemasangan gagal.
- Logo launcher dan header MySuite menggunakan ikon wrench + gear.
- Music memakai UI asli Music Player: visualizer, equalizer 8-band, preset, bass, preamp, volume, shuffle, loop, dan playlist dari subfolder. Pilih folder `audio/` melalui pemilih folder Android; izin baca folder disimpan. `all` mengikuti MP3, playlist subfolder mendukung MP3/M4A/OGG/WAV/FLAC/AAC.
- Finance memakai UI asli Finance Manager: dashboard Cash/Vault/Debt, Chat kosong saat awal, input manual, tabel transaksi, analitik, budget harian/mingguan/bulanan, log, kalkulator, kurs, logam mulia, dan harga bahan pokok. Transaksi dan budget disimpan lokal. Ekspor tersedia sebagai CSV.
- Vault memakai UI asli MyVault: Notes To-Do/Ide/Tempat, edit/hapus, Bookmarks dengan preview iframe, Read Later, dan tema gelap/terang. Catatan/bookmark/artikel disimpan lokal; ekstraksi artikel dilakukan melalui koneksi HTTP(S) dengan batas ukuran dan timeout.

## Data dan batasan
- Data Finance dan Vault disimpan pada WebView local storage di perangkat. Menghapus data aplikasi dapat menghapus data tersebut.
- Pemilihan folder audio memakai izin Storage Access Framework Android; jangan pindahkan atau hapus folder sumber jika masih ingin memutar file.
- Kurs, logam mulia, dan harga pangan membutuhkan koneksi internet serta bergantung pada ketersediaan API sumber.
- Plugin menjalankan HTML/JavaScript dalam WebView; hanya impor ZIP yang dipercaya. Ini bukan sandbox keamanan penuh.

## Format ZIP plugin
ZIP harus berisi `manifest.json` dan `web/index.html` di root. Batas instalasi 30 MB dan 2.000 entri. Update harus memakai ID plugin yang sama.

## Build
GitHub Actions workflow berada di `.github/workflows/android.yml`. APK debug dihasilkan di `app/build/outputs/apk/debug/app-debug.apk`.
