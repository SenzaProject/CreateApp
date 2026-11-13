# Tahap 7 — APK Manager + penyempurnaan build lokal

Status: **kode, unit test, dan skrip ditulis; logika domain dan skrip Termux diuji dengan kompiler Kotlin 2.1.21 dan proses nyata di
JVM/shell; layer Android non-Compose (`SafArtifactStore`, `ApkStager`, `ApkIntents`, `ApkManagerViewModel`) dikompilasi terhadap
`android.jar` API 35 dengan stub AndroidX; layar Compose belum dikompilasi dan seluruhnya belum dijalankan di perangkat.** Jangan centang
Tahap 7 di `CHECKLIST.md` sebelum CI hijau dan daftar uji perangkat di bawah lulus.

## Yang dibangun

### 1. APK Manager (layar baru, dibuka dari tab Build → **Buka APK Manager**)

| Fitur | Cara kerja nyata |
|---|---|
| Daftar artefak | Membaca isi `<folder bersama>/out/<id proyek>/` lewat Storage Access Framework. **Hanya file yang benar-benar ada** menjadi entri; riwayat tidak pernah menambah entri, hanya melengkapi keterangan (nama proyek, jenis build). |
| Ukuran & waktu | Ukuran dan waktu ubah dibaca dari file itu sendiri, bukan dari catatan. Waktu build jatuh ke catatan riwayat hanya bila penyedia dokumen tidak melaporkan waktu. |
| Pemeriksaan isi | Tiap file diperiksa di latar belakang (ZIP utuh + manifest) dan hasilnya di-cache per `proyek/nama + ukuran + waktu ubah`; file yang berubah otomatis diperiksa ulang. |
| Pasang | Hanya untuk APK yang **valid dan bertanda tangan**. Disalin ke `cache/apk-share/` (satu salinan), diberikan ke pemasang paket lewat FileProvider. Bila izin "pasang aplikasi tidak dikenal" belum ada, membuka pengaturannya. AAB dan APK `*-unsigned.apk` ditolak dengan alasan yang jelas. |
| Bagikan | Salinan yang sama lewat `ACTION_SEND`. |
| Ekspor | Memilih tujuan lewat pemilih dokumen sistem; salinan dengan kemajuan nyata, dibatalkan/dihapus bila gagal. Nama saran: `<proyek>-<artefak>`. |
| Detail | Untuk APK: nama aplikasi, package, versi, minSdk/targetSdk **dibaca dari file APK** (PackageManager). Untuk AAB: ukuran dan lokasi saja (Android tidak bisa membaca AAB). |
| Hapus | Selalu dengan dialog konfirmasi; hanya satu file; folder proyek yang jadi kosong ikut dibersihkan; daftar dibaca ulang sesudahnya. |
| Buka lokasi | Membuka folder `out/<id>` di aplikasi File; bila tidak ada aplikasi yang bisa, path disalin. Path juga bisa disalin langsung. |
| Riwayat hasil build | Build berhasil yang menghasilkan artefak; tiap artefak ditandai **tersedia** hanya bila file ada **dan** build itulah pemiliknya (build baru menghapus isi `out/<id>`). |

Keamanan: nama file dan id proyek divalidasi dua kali (di `ArtifactManager` dan di `SafArtifactStore`) sebelum menyentuh penyimpanan;
tidak ada path yang disusun dari input mentah; FileProvider hanya membuka `cache/apk-share/`.

### 2. Build lokal (Termux) yang lebih bisa diandalkan

- **Log langsung.** Sebelumnya Termux hanya mengirim keluaran setelah build selesai, sehingga build 10+ menit tampak membeku. Kini helper
  menyalin log Gradle ke `<folder bersama>/live/<id>.log` tiap 2 detik (header berisi token run unik, ditulis atomik lewat `mv`), dan
  aplikasi membacanya untuk menampilkan task terakhir, jumlah task, dan log. Log sisa build lama ditolak lewat token. Berkas live dihapus
  saat build selesai atau dibatalkan; proses pendamping ikut berhenti.
- **Wake lock.** Helper memanggil `termux-wake-lock` selama build dan melepasnya saat selesai/gagal/dibatalkan (bila perintahnya ada),
  agar layar mati tidak menghentikan build.
- **Pemeriksaan lingkungan** kini juga melaporkan RAM, ruang kosong, dan ketersediaan wake lock (peringatan, bukan syarat).
- Terjemahan Inggris 74 string lama yang hilang ditambahkan (mencegah galat lint `MissingTranslation` pada build release).

## Hasil pengujian nyata (di lingkungan pengembangan, bukan di HP)

| Pengujian | Hasil |
|---|---|
| Kompilasi seluruh modul `domain` dengan Kotlin 2.1.21 | Lulus |
| Tes domain (20 kelas lama + 5 kelas baru, JUnit 4 memakai runner mini; tes `runTest` dilewati karena butuh `coroutines-test`) | **226 lulus, 0 gagal** (169 lama, 57 baru) |
| Tes mutasi: merusak 4 logika kunci (kepemilikan riwayat, cek ukuran salin, blok unsigned, token log) | Tiap mutasi digagalkan tes |
| Tes shell helper (`scripts/test-build-helper.sh`) dengan `dash` dan `bash` | **109 lulus, 0 gagal** (79 lama + 30 baru: log langsung, token berbahaya, wake lock, pembatalan, tanpa proses tersisa) |
| `SafArtifactStore`, `SafBuildLiveLog`, `ApkStager`, `ApkIntents`, `ApkManagerViewModel` dikompilasi terhadap `android.jar` API 35 + stub AndroidX | Lulus kompilasi |
| Layar Compose (`ApkManagerScreen`, perubahan `BuildScreen`), `AppContainer`, `NavHost` | **Tidak bisa dikompilasi di sini.** Dicek statis: semua import ada di kode yang sudah terbukti terkompilasi atau di modul proyek, semua `R.string.*` ada di `values` dan `values-en`, tanda kurung seimbang, tanda tangan komponen cocok. |
| Alur update Git (`termux-update.sh`) dengan remote lokal | Lihat bagian akhir laporan tahap ini di `TERMUX-PUSH.md` |

## Daftar uji perangkat (wajib sebelum mencentang Tahap 7)

- [ ] CI hijau (Unit test, Build APK debug)
- [ ] Tab Build → **Buka APK Manager** terbuka; tanpa folder bersama muncul pesan pilih folder
- [ ] Setelah build Debug sukses: APK muncul dengan ukuran sama seperti di aplikasi File, status **Valid**, proyek dan jenis build benar
- [ ] **Pasang**: pertama kali diarahkan ke izin "pasang aplikasi tidak dikenal"; setelah diizinkan, pemasang paket terbuka dan APK terpasang
- [ ] **Bagikan** ke aplikasi lain berhasil; **Ekspor** menghasilkan file dengan ukuran identik
- [ ] **Detail** menampilkan package/versi yang cocok dengan proyek
- [ ] Release APK: ditandai **Belum ditandatangani**, tombol Pasang menolak dengan alasan; AAB: Pasang ditolak, Ekspor jalan
- [ ] **Hapus**: dialog konfirmasi; setelah dikonfirmasi file hilang dari aplikasi File dan dari daftar; **Batal** tidak menghapus apa pun
- [ ] Hapus file lewat aplikasi File saat APK Manager terbuka → kembali ke aplikasi: daftar ikut berubah
- [ ] **Buka folder** membuka lokasi yang benar (atau menyalin path bila gagal)
- [ ] Build panjang: kartu Log menampilkan **log langsung** dan task terakhir bergerak; setelah selesai log akhir menggantikannya
- [ ] Layar mati saat build berjalan: build tetap lanjut (wake lock); **Batalkan** menghentikan proses `java` dan menghapus `live/<id>.log`
- [ ] Periksa lingkungan menampilkan butir Memori, Ruang penyimpanan, Wake lock

## Keterbatasan yang diketahui

- **Toolchain tetap berada di Termux.** Create App mengatur, menyalin, memantau, dan mengelola hasil; JDK, Gradle, dan Android SDK dijalankan
  Termux. Menanam toolchain sendiri di dalam APK adalah pekerjaan terpisah yang jauh lebih besar.
- Bila proses Create App dimatikan sistem saat build berjalan, hasil akhir dari Termux tidak diterima aplikasi; APK tetap ada di `out/<id>/`
  dan akan tampil di APK Manager, tetapi catatan riwayat build itu tidak dibuat.
- Log langsung berbasis salinan berkala (jeda ±2 detik) dan dibatasi 512 KB terakhir di layar.
- "Versi terpasang saat ini" tidak ditampilkan: Android 11+ menyembunyikan daftar aplikasi lain tanpa izin khusus; lebih baik tidak
  menampilkan data yang bisa salah.
- Folder bersama harus di penyimpanan internal utama (kartu SD ditolak, seperti Tahap 6).
- APK release belum ditandatangani sampai Tahap 8.
