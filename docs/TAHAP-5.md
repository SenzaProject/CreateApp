# Tahap 5 — Terminal dan integrasi Termux

## Status verifikasi (baca ini dulu)

| Pemeriksaan | Hasil |
|---|---|
| Kode ditulis sesuai roadmap Tahap 5 | Ya |
| Unit test ditulis (domain 67, core/terminal 31 `@Test`) | Ya |
| **Kompilasi Kotlin / `./gradlew test` / `:app:assembleDebug`** | **Belum dijalankan** (lingkungan penulisan tidak punya kompiler Kotlin, Gradle, Android SDK, maupun internet) |
| Uji di perangkat Android (Termux, izin, shell Android) | **Belum** |
| Pemeriksaan statis (kurung seimbang, import internal terselesaikan, semua `R.string` ada, `strings.xml` valid dan tanpa escape yang salah) | Lulus |
| Logika murni dan alur runner diuji lewat **port Java 1:1** di JDK 21 (107 pemeriksaan: resolver path, pemecah kata, parser `cd`, riwayat, perakit baris + regex ANSI, decoder UTF-8; 31 pemeriksaan proses nyata: stdout/stderr, kode keluar, cwd, stdin ditutup, 50.000 baris, UTF-8, 200 proses singkat tanpa output hilang, pembatalan mematikan proses **turunan**) | Lulus 138/138 |
| Perintah pengaktif `allow-external-apps` untuk Termux, dijalankan sungguhan di `dash` dan `bash` | Lulus 18/18 skenario (menemukan dan memperbaiki satu bug: baris terakhir file tanpa newline akan rusak) |
| `scripts/termux-update.sh`, dijalankan sungguhan dengan repo git + remote lokal | Lulus (jalur normal, tanpa perubahan, file dihapus, remote lebih maju, dan 7 pengaman) |
| Detail API `RUN_COMMAND` Termux | Dicocokkan dengan wiki resmi Termux dan sumber lain; bagian yang hanya terkonfirmasi dari sumber sekunder dicatat di bawah |

Port Java dan pemeriksaan statis **bukan pengganti kompilator**. Kesalahan tipe, resolusi overload, atau API
Compose yang berbeda versi hanya akan ketahuan saat CI berjalan. Kode Tahap 3 yang menjadi dasar juga belum
pernah dikompilasi, jadi kegagalan CI bisa berasal dari bagian lama maupun baru.

**Cara membuktikan:** push ke GitHub (lihat `docs/TERMUX-PUSH.md`) dan lihat workflow **Android CI**. Centang butir
Tahap 5 di `docs/CHECKLIST.md` hanya setelah CI hijau **dan** daftar uji perangkat di bawah lulus.

## Temuan: Tahap 4 belum ada di kode

Berkas `CreateAppTahap4.zip` ternyata berisi kode **Tahap 3**: editor belum punya syntax highlighting, undo/redo,
multi-tab, atau simpan otomatis, dan `README` serta `docs/` hanya sampai Tahap 3. Tahap 5 sengaja dikerjakan di atas
kode itu apa adanya (Terminal tidak bergantung pada editor), tanpa ikut menulis Tahap 4. Tahap 4 masih harus dikerjakan.

## Cakupan (sesuai `docs/ROADMAP.md`)

- **Antarmuka terminal:** output stdout dan stderr (stderr berwarna merah), perintah yang diketik, riwayat (tombol ▲▼
  dan perintah `history`), pesan status terlokalisasi, tombol salin seluruh output untuk dikirim sebagai laporan.
- **Working directory:** `cd` bawaan (mendukung `~`, `..`, path relatif/absolut, tanda kutip), menu folder (direktori
  awal dan folder proyek untuk shell lokal). Direktori disimpan terpisah per backend.
- **Kontrol proses:** tombol Hentikan; layar tidak membeku karena pembacaan output dilakukan di thread IO.
- **Dua backend:** *Shell lokal* (`/system/bin/sh` di proses aplikasi) dan *Termux* (intent resmi `RUN_COMMAND`).
- **Validasi lingkungan dan deteksi Termux:** terpasang atau belum, izin sudah diberikan atau belum, tombol minta izin,
  buka pengaturan aplikasi, buka Termux, dan perintah siap-salin untuk mengaktifkan `allow-external-apps`.
- **Kegagalan dijelaskan:** Termux tidak terpasang, izin ditolak, Termux menolak/tidak terjangkau, `allow-external-apps`
  belum aktif, tidak ada respons, waktu habis, direktori tidak valid, proses tidak bisa dimulai.

Perintah bawaan: `help`, `clear`/`cls`, `history`, `cd`. Perintah lain dijalankan di shell backend terpilih.

## Keputusan desain

- **Domain murni Kotlin.** Parser perintah, riwayat, `cd`, perakit baris, dan `TerminalSession` ada di `:domain` tanpa
  Android sehingga diuji di JVM dengan runner palsu. Backend ada di `:core:terminal` di balik `CommandRunner`.
- **Setiap perintah = proses shell baru.** Variabel dan `cd` di dalam shell tidak bertahan, jadi `cd` dibuat bawaan dan
  direktori kerja dikelola sesi. `cd x && ls` (ada operator shell) diserahkan ke shell dan hanya berlaku untuk baris itu.
- **Polling `available()`, bukan `read()` yang memblokir.** Membaca pipa yang memblokir tidak bisa dibatalkan bila proses
  turunan masih memegang pipa; dengan polling, pembatalan selalu bisa mematikan proses dan menyelesaikan aliran.
  Setelah proses mati, sisa isi pipa dikuras dulu agar tidak ada output hilang.
- **stdin ditutup segera**, sehingga `cat` tanpa argumen mendapat EOF, bukan menggantung selamanya.
- **Pembatalan shell lokal:** SIGTERM, tunggu 500 ms, SIGKILL. Di Android 13+ proses turunan ikut dimatikan
  (`ProcessHandle`); di Android 8–12 turunan dari perintah majemuk bisa tetap hidup sampai selesai sendiri.
- **Termux: pembatalan hanya berhenti menunggu.** Termux tidak menyediakan cara mematikan perintah latar belakang dari
  aplikasi lain. UI menyatakannya terang-terangan ("Proses di Termux mungkin masih berjalan").
- **Termux: output tidak mengalir.** Hasil baru tiba saat perintah selesai (maks. ±100 KB, bagian awal dipotong).
- **PendingIntent `FLAG_MUTABLE` wajib** (Termux menyisipkan bundle hasil ke intent itu; versi immutable tidak pernah
  menerima hasil). Intent eksplisit ke paket sendiri agar sah untuk Android 14+.
- **`err` dari Termux bernilai -1 (`RESULT_OK`) bila sukses**, bukan 0; pemetaan menerima -1, 0, dan kosong sebagai sukses.
- **Riwayat hanya di memori**, tidak ditulis ke disk, agar token atau password yang mungkin diketik tidak tersimpan.
  Isi perintah tidak pernah dicatat ke log.
- **Batas pengaman:** scrollback 3.000 baris, satu baris maks. 4.000 karakter, satu perintah maks. 2 juta karakter output
  (lewat itu perintah dihentikan); escape ANSI dan karakter kontrol dibuang.
- **Izin minimal:** satu-satunya izin baru adalah `com.termux.permission.RUN_COMMAND` plus `<queries>` untuk `com.termux`
  (Android 11+). Tidak ada INTERNET.

## Prasyarat Termux (di luar kendali aplikasi)

1. Termux (build F-Droid atau GitHub) terpasang dan pernah dibuka sekali.
2. Izin **Run commands in Termux environment** diberikan ke Create App (tombol *Beri izin* di layar Terminal; bila dialog
   tidak muncul: Pengaturan aplikasi → Izin → Izin tambahan). Bila Create App dipasang *sebelum* Termux dan izinnya tidak
   muncul, pasang ulang Create App.
3. Di Termux: `allow-external-apps=true` di `~/.termux/termux.properties`. Layar Terminal menyediakan perintah siap-salin
   (tombol *Salin perintah*); aman dijalankan berulang dan tidak merusak isi file yang ada.

Opsi `allow-external-apps` **tidak bisa dideteksi** dari aplikasi lain; kegagalannya baru terlihat saat perintah pertama
(pesan "allow-external-apps belum aktif", atau setelah 15 detik petunjuk "belum ada respons").

## Peta berkas

| Lokasi | Isi |
|---|---|
| `domain/.../terminal/` | `TerminalModels` (backend, peristiwa, kontrak `CommandRunner`/`TermuxEnvironment`), `TerminalState`, `TerminalSession`, `CommandParser`, `ShellWords`, `PathResolver`, `CommandHistory`, `LineAssembler` |
| `core/terminal/` | `LocalShellRunner`, `ProcessTree`, `Utf8ChunkDecoder`, `TermuxCommandRunner`, `TermuxResult` (+`TermuxResultReader`), `AndroidTermuxEnvironment`, `TermuxPaths`, `TerminalModule` |
| `app/.../ui/terminal/` | `TerminalScreen`, `TerminalViewModel`, `TerminalText` (pemetaan pesan terlokalisasi) |
| `app/.../di/AppContainer.kt` | Merangkai sesi terminal dan kedua backend; `projectDirectoryPath` |
| `app/src/main/AndroidManifest.xml` | `uses-permission` RUN_COMMAND dan `<queries>` Termux |
| `scripts/termux-update.sh` | Sinkronisasi zip tahap baru ke repo lokal + commit + push (lihat `docs/TERMUX-PUSH.md`) |

Berubah: tile Terminal di Beranda; layar Terminal menggantikan placeholder; string `placeholder_terminal_desc` dihapus.

## Cakupan unit test

- **domain (67):** resolver path, pemecah kata (kutip, escape), parser perintah (bawaan, `cd` dengan operator/kutip),
  riwayat (kapasitas, dedup, navigasi + draf), perakit baris (potongan terpisah, CRLF, `\r` progres, ANSI, kontrol, Unicode,
  pemotongan), dan `TerminalSession` dengan runner palsu (output/stderr, kode keluar, kegagalan, `cd`, `clear`, `history`,
  pembatalan, penolakan masukan saat berjalan, batas output, batas scrollback, direktori per backend).
- **core/terminal (31):** `LocalShellRunner` dengan proses `/bin/sh` sungguhan (hanya berjalan di Linux/macOS; dilewati bila
  tidak ada `/bin/sh`), decoder UTF-8 per potongan, pemetaan hasil Termux.
- **Tidak ada tes otomatis** untuk `TermuxCommandRunner`, `AndroidTermuxEnvironment`, `TermuxResultReader`, dan UI
  Compose/ViewModel (butuh perangkat). Ini celah yang diketahui.

## Daftar uji manual di perangkat (wajib sebelum mencentang Tahap 5)

Shell lokal:
1. Beranda → Terminal. Ketik `help` → bantuan tampil. Ketik `echo halo` → `halo` dan `✓ Selesai (kode keluar 0)`.
2. `ls /system/bin | head` dan `echo error 1>&2` → stderr berwarna merah. `exit 3` → `✗ Gagal (kode keluar 3)`.
3. `cd /system` lalu `pwd` → `/system`; `cd ..`, `cd ~`, `cd tidakada` → pesan folder tidak ditemukan.
4. Menu folder → pilih sebuah proyek → direktori berpindah; `ls` menampilkan isi proyek.
5. `sleep 60` lalu tekan **Hentikan** → `■ Dihentikan.` dan input aktif lagi; `sleep 60 &` bukan untuk diuji (latar belakang).
6. `cat` (tanpa argumen) → selesai segera, tidak menggantung.
7. `seq 1 100000` → layar tetap responsif; output dipotong dengan pesan batas bila melebihi 2 juta karakter (`yes` untuk uji batas).
8. Tombol ▲▼ menelusuri riwayat; `history` menampilkan daftar bernomor; tombol salin menyalin seluruh output.
9. Putar layar saat perintah berjalan → perintah tetap berjalan, scrollback tetap ada. Pindah ke Beranda lalu kembali → sama.

Termux:
10. Tanpa Termux terpasang: pilih backend Termux → kartu "Termux belum terpasang"; jalankan `ls` → pesan jelas.
11. Pasang Termux, kembali ke aplikasi → status berubah tanpa restart; tombol *Beri izin* memunculkan dialog (atau arahkan ke
    pengaturan). Tolak izin → perintah gagal dengan pesan izin.
12. Beri izin tetapi belum aktifkan `allow-external-apps` → `ls` gagal dengan pesan `allow-external-apps` (atau petunjuk
    "belum ada respons" setelah 15 detik; **Hentikan** harus berhasil).
13. Salin perintah → tempel di Termux → jalankan → ulangi `ls` di Create App → output Termux tampil setelah selesai, kode keluar benar.
14. `echo satu; echo dua 1>&2; exit 2` → stdout, stderr merah, `✗ Gagal (kode keluar 2)`.
15. `cd ~` lalu `pwd` → `/data/data/com.termux/files/home`. Perintah dengan direktori yang tidak ada → pesan error dari Termux.
16. `sleep 30` lalu **Hentikan** → pesan "Berhenti menunggu… mungkin masih berjalan" (ini perilaku yang dinyatakan, bukan bug).

## Batasan yang diketahui

- **Shell lokal:** program di folder data aplikasi tidak bisa dieksekusi langsung (Android 10+; pakai `sh skrip.sh`);
  hanya perintah sistem (toybox) tersedia. Untuk alat pengembangan penuh gunakan Termux.
- **Folder proyek Create App tidak bisa diakses dari Termux** (UID berbeda); menu folder proyek hanya untuk shell lokal. Build
  lewat Termux (Tahap 6) membutuhkan ekspor/sinkronisasi proyek ke penyimpanan bersama.
- Tidak interaktif: tidak ada stdin, jadi `vim`, `top`, prompt `y/n` tidak bisa dipakai. Tidak ada emulasi terminal penuh.
- Termux: output tidak mengalir, dipotong ±100 KB, dan Hentikan tidak mematikan proses (lihat di atas).
- Android 8–12: Hentikan bisa meninggalkan proses turunan dari perintah majemuk.
- Tidak ada seleksi teks per-baris; gunakan tombol salin seluruh output.
- Riwayat hilang saat aplikasi ditutup (disengaja).
- Strings bahasa Inggris untuk bagian lama proyek (Tahap 3) masih sebagian; yang baru lengkap di kedua bahasa.

## Bila CI gagal: titik yang paling mungkin bermasalah

Karena belum pernah dikompilasi, ini daftar tebakan terbaik sebelum membaca log:
`TerminalSession.runExternal` (ekspresi `when` sebagai statement terakhir di lambda `collect`), pemakaian
`ProcessHandle`/`Process.descendants()` terhadap `android.jar` (API 33), `KeyboardOptions(autoCorrectEnabled = …)` dan
`LifecycleEventEffect` pada versi Compose/lifecycle di `gradle/libs.versions.toml`, resolusi overload
`Intent.putExtra(String, Array<String>)`, dan `ProducerScope.pump` sebagai ekstensi privat di `LocalShellRunner`.
Kirim log merah apa adanya; jangan menebak.
