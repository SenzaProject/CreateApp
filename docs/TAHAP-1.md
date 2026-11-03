# Tahap 1 — Persiapan proyek dan arsitektur

**Status: kode selesai ditulis. Kriteria selesai BELUM terbukti** (build dan instalasi belum dijalankan).
Sesuai aturan roadmap, tahap ini baru dinyatakan selesai setelah semua butir di "Verifikasi" lulus.

## Cakupan yang dikerjakan

- Struktur proyek Kotlin multi-modul: `:app`, `:domain`, `:data`, `:core:{common,editor,project,builder,terminal,git}`.
- Gradle (Kotlin DSL) + version catalog, Jetpack Compose, Material 3, minSdk 26, compile/target SDK 35.
- Navigasi dasar (5 tab bawah + 2 layar sekunder) dan komponen UI bersama (top bar, kartu, chip status, layar penanda).
- Arsitektur data/domain/presentation dengan satu alur nyata (informasi aplikasi di Beranda) sebagai contoh pola.
- DI manual (`AppContainer`), logging ke Logcat + file, penyamaran rahasia, laporan crash, `AppResult`/`AppError`.
- Tema gelap (default) dan terang sesuai palet desain; string Indonesia (default) dan English.
- Ikon launcher adaptive dari logo.
- Unit test: `:domain` (hasil/galat, use case), `:data` (repository), `:core:common` (penyamaran rahasia).
- Workflow GitHub Actions (JDK 17, Android SDK, test, APK debug, upload artifact).
- Skrip `setup-wrapper.sh` dan `check-env.sh`.

Tidak dikerjakan (bukan cakupan Tahap 1): splash, Project Manager, editor, terminal, build, GitHub, signing.
Layar-layar tersebut hanya menampilkan penanda jujur "belum tersedia, dijadwalkan pada Tahap N".

## Yang sudah diperiksa (statis, di luar perangkat)

- Semua XML (manifest, resource, adaptive icon) well-formed.
- `libs.versions.toml` valid dan setiap alias yang dipakai build file ada.
- Kunci string Indonesia dan English identik; setiap `R.string.*` yang dipakai kode ada.
- Impor antar-paket proyek mengarah ke kelas yang benar-benar ada.
- Kurung/kurawal pada semua berkas Kotlin seimbang.

**Tidak dapat diperiksa di lingkungan penyusunan** (tanpa Gradle, Android SDK, dan internet): kompilasi Kotlin,
resolusi dependency, eksekusi unit test, dan pemasangan APK. Karena itu versi dependency dan kode
**belum pernah dikompilasi**.

## Verifikasi (harus lulus sebelum Tahap 1 dinyatakan selesai)

| # | Uji | Cara | Hasil yang diharapkan |
|---|---|---|---|
| 1 | Proyek dapat dikompilasi | push ke GitHub, buka Actions → Android CI | Langkah "Build APK debug" hijau |
| 2 | Unit test lulus | langkah "Unit test" di workflow | Hijau |
| 3 | APK debug dapat dipasang | unduh artifact `CreateApp-debug-apk`, pasang | Terpasang tanpa error |
| 4 | Aplikasi terbuka | buka dari launcher | Beranda tampil, tidak crash |
| 5 | Navigasi bekerja | ketuk kelima tab, tombol Buka Editor / Buka Terminal, tombol kembali | Tidak ada layar kosong atau crash |
| 6 | Ikon dan nama | lihat launcher | Ikon Create App, nama "Create App" |
| 7 | Tidak ada error konfigurasi | baca log workflow | Tidak ada error; peringatan dicatat di bawah |

## Risiko dan masalah yang diketahui

1. **Versi dependency belum teruji kompilasi.** Bila build gagal pada resolusi versi atau API, salin log yang merah;
   perbaikan umumnya cukup di `gradle/libs.versions.toml`.
2. **Gradle Wrapper belum ada di repository.** CI membuatnya otomatis. Untuk build lokal dan agar `gradlew`
   masuk repository: `sh scripts/setup-wrapper.sh` lalu commit `gradlew`, `gradlew.bat`, `gradle/wrapper/gradle-wrapper.jar`.
3. **Ikon tab Terminal/GitHub sementara.** Memakai ikon bawaan yang tersedia; diganti pada polesan UI (Tahap 2).
4. **Light mode belum bisa dipilih.** Skema warna sudah ada, pemilih tema menunggu Settings (Tahap 13).
5. **LICENSE belum ada.** Lisensi perlu dipilih pemilik proyek.
6. **Release build** belum diatur (minify dan signing) — Tahap 8/14.

## Menutup Tahap 1

Setelah #1–#7 lulus:

1. Ubah status modul `:app`, `:domain`, `:data`, `:core:common` menjadi `ModuleStatus.READY`
   (`AppContainer.kt`, `ModuleInfo.kt`, `DataModule.kt`, `CommonModule.kt`).
2. Centang butir Tahap 1 di `docs/CHECKLIST.md`.
3. Commit, lalu mulai Tahap 2.
