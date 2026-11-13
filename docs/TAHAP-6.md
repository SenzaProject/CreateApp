# Tahap 6 — Gradle Build System

Status: **kode, unit test, dan skrip ditulis; logika inti dan skrip Termux sudah diuji dengan proses nyata di JVM dan shell; belum
dikompilasi sebagai Kotlin/Android dan belum dijalankan di perangkat.** Jangan centang Tahap 6 di `CHECKLIST.md` sebelum CI hijau
dan daftar uji perangkat di bawah lulus.

## Yang dibangun

Layar **Build** (tab Build) menggantikan placeholder. Dua jalur build:

| Jalur | Cara kerja | Status |
|---|---|---|
| **Lokal (Termux)** | Proyek disalin ke folder bersama, helper shell di Termux menjalankan `gradlew`, hasil APK/AAB ditulis kembali ke folder bersama dan diperiksa aplikasi | Diimplementasikan, belum diuji di perangkat |
| **Cloud (GitHub Actions)** | Workflow `Android CI` bisa dijalankan manual dengan pilihan `assembleRelease` atau `bundleRelease`; hasil diunduh dari Artifacts | Diimplementasikan di workflow; **pemicu dari dalam aplikasi baru di Tahap 10** |

Fitur layar Build: pilih proyek, pilih jenis (Debug APK, Release APK, Release AAB), pilih folder bersama, periksa lingkungan Termux,
mulai/batalkan build, status dan fase nyata, task Gradle terakhir, daftar error/warning dengan lokasi file:baris, log (salin),
hasil artefak dengan pemeriksaan keabsahan, riwayat build (log tersimpan, dapat dibuka), bersihkan folder kerja Termux.

## Alur build lokal

1. Pengguna memilih satu kali **folder bersama** di penyimpanan internal (mis. `Download/CreateAppBuild`) lewat Storage Access Framework.
   Folder privat proyek tidak bisa dibaca Termux (UID berbeda), jadi tanpa folder ini build lokal tidak mungkin.
2. **Mulai build** → aplikasi menyalin proyek ke `<folder>/src/<id proyek>` (menggantikan salinan lama).
3. Aplikasi mengirim satu perintah ke Termux (`RUN_COMMAND`) yang **memasang helper** (`createapp-build.sh`, aset aplikasi) ke
   `~/.createapp/` lalu menjalankannya. Helper menyalin proyek ke `~/.createapp/work/<id>`, membuat `local.properties` bila
   `ANDROID_HOME` ada, membuat Gradle Wrapper bila proyek belum punya (butuh `gradle` terpasang), menjalankan
   `sh ./gradlew --console=plain --no-daemon <task>`, lalu menyalin APK/AAB ke `<folder>/out/<id proyek>/`.
4. Helper mencetak penanda `@@CA@...` (fase, kode keluar Gradle, daftar artefak, penanda selesai). Aplikasi memisahkannya dari log
   Gradle, mengurai task/error/warning, dan menentukan hasil.
5. **Build dinyatakan berhasil hanya bila**: perintah keluar 0, helper melaporkan `exit=0` dan penanda selesai, ada ≥1 artefak, dan
   aplikasi sendiri memeriksa file di folder bersama (ada, ukuran sama, ZIP utuh, berisi manifest). Penanda teks saja tidak cukup.

Tugas Gradle yang dijalankan dibatasi ke tiga nilai tetap (`assembleDebug`, `assembleRelease`, `bundleRelease`) di aplikasi **dan**
di helper (daftar izin), sehingga tidak ada argumen bebas yang masuk ke shell.

## Keputusan desain

- **Helper dipasang pada setiap perintah**, bukan langkah "pasang" terpisah: tidak ada keadaan usang atau langkah yang terlewat.
  Dipasang lewat file sementara + `mv` (rename atomik) supaya `cancel`/`check` yang berjalan saat build aktif tidak merusak skrip
  yang sedang dibaca shell. Ini diuji (lihat di bawah).
- **Pembatalan sungguhan**: helper mencatat PID-nya; `cancel` mengirim TERM, helper mematikan seluruh pohon proses Gradle
  (lewat `/proc`), memberi waktu 5 detik, lalu KILL. Bila Termux tidak membalas dalam 60 detik, aplikasi berhenti menunggu dan
  menandai "mungkin masih berjalan" — tidak berpura-pura sudah berhenti.
- **Status dari bukti, bukan animasi**: tidak ada persentase palsu. Gradle mode plain tidak melaporkan total task, jadi layar hanya
  menampilkan fase, jumlah task yang terlihat, dan task terakhir.
- Satu build pada satu waktu (helper juga menolak build kedua dengan `ALREADY_RUNNING`).
- Riwayat disimpan di `filesDir/builds/` (30 build terakhir); log disamarkan (`LogRedactor`) dan dipotong 1 MB.

## Status verifikasi (jujur)

| Apa | Bagaimana | Hasil |
|---|---|---|
| Skrip helper Termux (`createapp-build.sh`) | `scripts/test-build-helper.sh`: proses nyata (sh, find, cp, kill, `/proc`) dengan `gradlew` palsu; dijalankan di **dash dan bash**, juga dengan `gradle` ada di PATH (meniru runner CI) | **79/79 lulus** di tiap kondisi |
| Logika murni (parser log, protokol helper, kutip shell, path SAF, codec riwayat, validator ZIP, pembuat perintah, penentu hasil) | Dipindahkan 1:1 ke Java dan dijalankan di JDK 21 | **232/232 lulus** |
| Alur ujung-ke-ujung | Perintah dari pembuat perintah dijalankan lewat `bash -c` ke helper **asli**: build sukses (APK valid ZIP), gagal (error Kotlin terbaca), **batal (pohon proses mati, pid dibersihkan)**, `check` saat build aktif, build kedua ditolak, `clean`, id proyek jahat | Lulus |
| Kutip shell | 17 string berbahaya (`$(...)`, backtick, `'; ...`, newline, unicode) diteruskan lewat dash dan bash nyata | Harfiah, tidak ada eksekusi tersisip |
| `.gitignore` | Repo percobaan dengan `git add -A`: tidak ada file yang terabaikan | Lulus |
| Workflow YAML | Diurai dengan PyYAML | Valid |
| String (id/en) | XML valid; semua `R.string.bld_*` yang dipakai terdefinisi di kedua bahasa | Lulus |
| Kurung, paket≠folder, impor proyek | Pemeriksaan statis atas 33 file Kotlin | Lulus (sisa: false positive lexer / impor lama) |
| **Kompilasi Kotlin dan Compose** | **Tidak bisa** di lingkungan ini (tidak ada kompiler Kotlin, Gradle, Android SDK, internet) | **Belum terbukti** |
| **93 unit test Kotlin** (`domain/.../builder`, `core/builder`) | Ditulis, **belum dijalankan** (butuh Gradle). Mencerminkan kasus yang sudah lulus di port Java, ditambah tes `BuildSession` dengan fake runner | **Belum terbukti** |
| Tampilan layar Build, SAF, `RUN_COMMAND` | Hanya bisa dibuktikan di perangkat | **Belum terbukti** |
| Gradle + AGP asli di Termux (JDK, SDK, aapt2 ARM) | Butuh perangkat | **Belum terbukti** |

### Temuan selama pengujian (sudah diperbaiki)

1. Paket layar Build awalnya bernama `ui/build`; `.gitignore` mengabaikan semua folder `build/`, sehingga kode layar akan hilang dari
   GitHub. Diganti menjadi `ui/builder` (dan domain memakai `builder`). Diperiksa dengan git nyata.
2. Validator APK awalnya meloloskan APK **terpotong** bila `AndroidManifest.xml` ada di awal file (JDK menganggap data terpotong
   sebagai akhir ZIP). Kini validator juga memeriksa catatan akhir ZIP (End-of-Central-Directory) dan menolak file terpotong atau
   bersisa data.
3. Uji helper awalnya bergantung pada ada/tidaknya Java dan Gradle asli di mesin; kini memakai Java palsu dan PATH minimal.

## Daftar uji perangkat (wajib sebelum mencentang Tahap 6)

Prasyarat di Termux: `pkg install openjdk-17 gradle`, `termux-setup-storage`, `allow-external-apps = true` di
`~/.termux/termux.properties`, Android SDK (`ANDROID_HOME`, platform + build-tools). Di arsitektur ARM, `aapt2` bawaan Gradle
tidak jalan; biasanya diatasi dengan `aapt2` untuk Termux dan `android.aapt2FromMavenOverride` di `~/.gradle/gradle.properties`
(belum diuji di sini).

- [ ] CI hijau: langkah **Uji helper build Termux**, **Unit test** (93 tes baru), **Build APK debug**
- [ ] Tab Build terbuka, daftar proyek muncul, jenis build bisa dipilih
- [ ] Pilih folder bersama di penyimpanan internal → path tampil; pilih kartu SD → ditolak dengan pesan
- [ ] **Periksa lingkungan** menampilkan butir Java/Wrapper/SDK/Platform/Build-tools/aapt2/Folder dengan benar
- [ ] Build **Debug APK** proyek kecil: status Berhasil, APK muncul di `out/<id>/` dan berstatus **Valid**, dapat dipasang
- [ ] Build dengan sengaja salah (hapus satu titik koma): status **Gagal**, error muncul dengan file:baris
- [ ] **Batalkan** saat build berjalan: status Dibatalkan, proses `java` di Termux benar-benar hilang (`ps`/`top`)
- [ ] Mulai build kedua saat yang pertama berjalan: ditolak
- [ ] Release APK: hasil bertanda **belum ditandatangani**; Release AAB: hasil `.aab` Valid
- [ ] Tutup aplikasi saat build berjalan lalu buka lagi: status dan hasil tetap benar
- [ ] Riwayat memuat build tadi; membuka log menampilkan log; **Hapus riwayat** bekerja
- [ ] **Bersihkan folder kerja Termux** menghapus `~/.createapp/work`
- [ ] Cloud: jalankan workflow dengan `assembleRelease` dan `bundleRelease`, artefak terunduh

## Keterbatasan yang diketahui

- **Log akhir tampil setelah build selesai** (Termux mengirim keluaran hanya saat perintah berakhir dan memotong ke ±100 KB). Sejak
  Tahap 7 ada log langsung lewat berkas `live/<id>.log` di folder bersama (lihat `docs/TAHAP-7.md`).
- **Build lokal di HP berat dan rapuh**: butuh JDK, Android SDK, dan `aapt2` yang cocok dengan arsitektur; build pertama mengunduh
  dependensi. Tidak ada klaim dukungan universal; gunakan jalur cloud bila Termux sulit.
- Setiap build menyalin ulang proyek dan membangun dari nol (tanpa build inkremental) demi hasil yang benar dan sederhana.
- Folder bersama harus di **penyimpanan internal utama**; kartu SD ditolak.
- Gradle Wrapper belum ikut di repository (jar biner tidak bisa dibuat tanpa jaringan). Helper membuatnya dari `gradle` terpasang;
  CI membuatnya lewat `scripts/setup-wrapper.sh`.
- Release/AAB **belum ditandatangani** (Tahap 8). `assembleRelease` juga menjalankan lint vital yang bisa gagal pada kode yang belum
  dirapikan; karena itu build release di CI hanya berjalan manual.
- Pemicu cloud dari dalam aplikasi, status workflow, dan unduh artefak: Tahap 10.
- Tahap 4 (Code Editor) **belum dikerjakan**; Tahap 3 dan 5 belum pernah dikompilasi/diuji. Tahap 6 bergantung pada keduanya hanya
  lewat Terminal/Termux dan penyimpanan proyek.
