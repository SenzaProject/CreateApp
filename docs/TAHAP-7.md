# Tahap 7 — APK Manager (+ perbaikan build lokal)

Status: **kode, unit test Kotlin, dan helper Termux v2 ditulis. Helper Termux sudah diuji dengan proses nyata (119 pemeriksaan di dash dan
bash). Logika murni sudah diuji lewat port Java (24 pemeriksaan). Kotlin/Compose BELUM dikompilasi dan BELUM dijalankan di perangkat**
(lingkungan kerja tidak punya kompiler Kotlin, Gradle, Android SDK, maupun internet). Jangan centang Tahap 7 di `CHECKLIST.md`
sebelum CI hijau dan daftar uji perangkat di bawah lulus.

## A. APK Manager

Buka lewat tombol **APK Manager** di pojok kanan atas tab Build, atau tombol **Kelola di APK Manager** di kartu Hasil.

| Fitur | Cara kerja nyata |
|---|---|
| Daftar artefak | Dibaca langsung dari isi `out/<id proyek>/` di folder bersama lewat Storage Access Framework setiap layar dibuka/dimuat ulang. File yang sudah dihapus tidak pernah tampil; ukuran dan waktu ubah berasal dari file itu sendiri. |
| Detail | Keutuhan ZIP (manifest wajib ada, catatan akhir ZIP utuh), SHA-256, dan untuk APK: nama paket, nama aplikasi, versi, min/target SDK, dan jumlah penanda tangan — dibaca `PackageManager` dari file APK, bukan dari nama file. |
| Pasang | Memeriksa file lebih dulu (`InstallPolicy`): AAB ditolak, file tidak utuh ditolak, APK tanpa penanda tangan ditolak dengan alasan, izin "pasang aplikasi" dimintakan lewat layar pengaturan sistem. Bila lolos, file disalin ke cache privat (ukuran dicek) dan dibuka di pemasang paket lewat FileProvider. |
| Bagikan | Salinan cache yang sama dikirim lewat chooser Android. |
| Ekspor | Menyalin ke lokasi pilihan pengguna (`CreateDocument`); jumlah byte yang ditulis harus sama dengan ukuran sumber, bila tidak dilaporkan gagal. |
| Hapus | Dialog konfirmasi; file di folder keluaran dan salinan cache dihapus, folder proyek kosong ikut dihapus, lalu daftar dimuat ulang dari folder. |
| Buka folder / Salin path | Membuka folder di aplikasi File; bila tidak bisa, path disalin. |
| Riwayat | Riwayat build (Tahap 6) dihubungkan dengan isi folder: tiap file hasil build ditandai **masih ada**, **diganti build lebih baru** (waktu ubah file melewati akhir build itu), atau **sudah tidak ada**. |

### Batasan yang jujur
- Pemeriksaan tanda tangan hanya memeriksa **keberadaan** (`signerCount`), bukan keabsahan kriptografis. Verifikasi penuh: Tahap 8.
  Bila sistem tidak bisa menentukan (nilai "tidak dapat ditentukan"), aplikasi tidak memblokir pemasangan karena tebakan; hanya nama
  `...-unsigned.apk` yang jadi petunjuk.
- Tidak membandingkan dengan versi yang sudah terpasang (Android 11+ membatasi pembacaan paket lain tanpa izin khusus).
- AAB tidak bisa dipasang langsung; hanya bisa dibagikan/diekspor.
- Salinan cache memakai ruang dua kali ukuran APK; dibersihkan otomatis saat file asalnya hilang.

## B. Perbaikan build lokal (helper Termux v2)

Perubahan di `app/src/main/assets/createapp-build.sh` dan `BuildSession`:

1. **Log langsung.** Sebelumnya Termux hanya mengirim keluaran setelah build selesai (bisa puluhan menit tanpa kabar). Kini helper menulis
   keluaran Gradle langsung ke `<folder bersama>/logs/<id proyek>.log` dan aplikasi membacanya tiap detik: task terakhir, jumlah task,
   error/warning, dan log tampil selama build berjalan. Pembacaan memakai posisi byte dan hanya mengambil baris lengkap.
   *Cadangan*: bila file tidak bisa ditulis/dibaca, ekor log (600 baris) dari stdout Termux dipakai seperti sebelumnya; log tidak pernah
   ganda. Teks berawalan `@@CA@` di dalam log proyek **tidak** dianggap penanda helper.
2. **Build inkremental.** Salinan kerja `~/.createapp/work/<id>` dipertahankan: file sumber diganti seluruhnya (file yang dihapus ikut
   hilang), tetapi `build/` di akar tiap modul dan `.gradle` di akar proyek dipertahankan. Build kedua dan seterusnya jauh lebih cepat.
   `build/outputs` selalu dibuang sebelum Gradle jalan supaya APK lama bernama lain tidak ikut terkumpul. Paket sumber yang kebetulan
   bernama `build` tetap diperbarui (diuji). Tombol **Bersihkan folder kerja Termux** menghapus semuanya bila ingin build bersih.
3. Keamanan `--live`: hanya path absolut berakhiran `.log`, bukan folder; file lain tidak pernah dikosongkan (diuji).

## Uji yang sudah dijalankan di sini

| Apa | Hasil |
|---|---|
| `scripts/test-build-helper.sh` di dash dan bash (79 lama + 40 baru: log langsung, `--live` berbahaya, cadangan, inkremental, modul dihapus, folder `build` milik sumber, clean) | 119/119 lulus di keduanya |
| Uji mutasi: pemulihan `build/` dimatikan | 2 uji inkremental gagal (uji benar-benar menangkap) |
| Port Java: `InstallPolicy`, penghubung riwayat, `documentIdFor` | 24/24 lulus |
| Pemeriksaan statis: keseimbangan kurung semua berkas diubah, XML string valid, semua `R.string.*` terdefinisi (Indonesia) | Lulus |
| **Kompilasi Kotlin/Compose, 30+ unit test Kotlin baru, tampilan layar, SAF, FileProvider, pemasang paket** | **Belum terbukti** |

## Daftar uji perangkat (wajib sebelum mencentang Tahap 7)

- [ ] CI hijau: **Uji helper build Termux**, **Unit test**, **Build APK debug**
- [ ] Build Debug APK proyek kecil; **selama build** kartu Jalankan menampilkan "Log Gradle dibaca langsung" dan log bertambah
- [ ] Build kedua proyek yang sama jauh lebih cepat (inkremental) dan tetap menghasilkan APK baru yang benar
- [ ] APK Manager menampilkan APK itu dengan ukuran sama seperti di aplikasi File; detail menampilkan nama paket/versi yang benar
- [ ] **Pasang** APK debug: izin "sumber tidak dikenal" diminta, lalu terpasang dan bisa dibuka
- [ ] APK release `-unsigned`: **Pasang** ditolak dengan alasan jelas; AAB: ditolak
- [ ] **Bagikan** membuka chooser; **Ekspor** ke Download menghasilkan file berukuran sama
- [ ] **Hapus**: konfirmasi muncul; file hilang dari aplikasi File; daftar dan riwayat ("sudah tidak ada") ikut berubah
- [ ] Hapus APK lewat aplikasi File lalu kembali ke APK Manager: daftar otomatis memuat ulang
- [ ] Matikan Termux saat build: log langsung berhenti, hasil akhir tetap benar (gagal/dibatalkan), tidak ada log ganda

## Keterbatasan yang diketahui

- `values-en/strings.xml` belum memuat ±70 string dari Tahap 3–5 (sudah ada sebelum Tahap 7; kini tampil berbahasa Indonesia di perangkat
  berbahasa Inggris). `MissingTranslation` bisa menggagalkan **lint release**; build debug dan CI saat ini tidak terpengaruh.
- Proyek masih disalin ulang penuh ke folder bersama via SAF tiap build (lambat untuk proyek besar); hanya sisi Termux yang inkremental.
- Folder bersama harus di penyimpanan internal utama.
- Persentase kemajuan tidak ada (Gradle plain tidak melaporkannya); yang tampil hanya task terakhir dan jumlah task.
