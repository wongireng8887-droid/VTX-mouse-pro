# VTX Mouse Pro

Keyboard dan mouse untuk game di Android. Package `com.vtx.mousepro`, hanya Android 15-16 (API 35-36), hanya 64-bit (arm64-v8a).
Tema: Liquid Glass.

## Fase

| Fase | Isi | Status |
|------|-----|--------|
| 1 | Fondasi: repo, build otomatis, kerangka tampilan Liquid Glass (tab Game / Optimisasi Performa / Pengaturan) | selesai |
| 2a | Koneksi Shizuku otomatis, status Terhubung | **ini** |
| 2b | Pairing debug nirkabel (ADB) | |
| 3 | Izin & overlay: logo V muncul/hilang otomatis, daftar game | |
| 4 | Pemetaan dasar: Sentuh, joystick, tangkap keyboard & mouse | |
| 5 | FPS Look dan mode kursor | |
| 6 | Editor keymap dan menu samping | |
| 7 | Optimisasi Performa, Info Perangkat, poles animasi | |

Satu fase = satu zip. Lanjut ke fase berikutnya hanya setelah daftar tes fase sebelumnya lolos.

## Daftar tes Fase 1

1. Build di tab **Actions** hijau, artifact `VTX-Mouse-Pro-apk` bisa diunduh dan dipasang.
2. Aplikasi terbuka dalam posisi landscape, tanpa crash. Latar gelap kehijauan, panel kaca tembus pandang.
3. Tiga tab di bawah (Game, Optimisasi Performa, Pengaturan) bisa ditekan dan berpindah halaman.
4. Tab **Game**: tekan **+ Tambah**, cari aplikasi, nyalakan saklar pada sebuah game, tekan panah kembali. Game muncul sebagai kartu.
   Tekan **Mulai Game**: game terbuka.
5. Tab **Optimisasi Performa**: Info Perangkat menampilkan model, versi Android, arsitektur, RAM, dan penyimpanan HP.
6. Tab **Pengaturan**:
   - Shizuku: "Terpasang" jika Shizuku ada di HP.
   - Overlay: tekan, beri izin, kembali: berubah jadi "Diizinkan".
   - Akses Penggunaan dan Notifikasi: status sesuai kenyataan.
   - Tentang: versi 0.1.0, package `com.vtx.mousepro`.
7. Pasang mouse atau keyboard USB/Bluetooth: ikon mouse/keyboard di header menyala hijau.

Yang sengaja belum ada di Fase 1: koneksi Shizuku/ADB (status header selalu "Belum terhubung"), overlay, pemetaan, dan toggle Optimisasi Performa (tertulis "Segera").


## Daftar tes Fase 2a (tanpa Tes Sentuhan)

Syarat: Shizuku terpasang dan sedang jalan (aktifkan lewat debug nirkabel atau root).

1. Buka Shizuku > **Manajemen aplikasi**: **VTX Mouse Pro** harus muncul di daftar (ini yang tadinya hilang).
2. Buka VTX Mouse Pro. Dialog izin Shizuku muncul SENDIRI (sekali per sesi). Pilih **Izinkan**.
3. Header berubah jadi **Terhubung** (titik hijau) tanpa menekan apa pun lagi.
4. Pengaturan > **Shizuku**: pill **Aktif**.
6. Matikan Shizuku lalu buka ulang VTX: header berubah (mis. "Shizuku belum jalan"), tidak crash.
