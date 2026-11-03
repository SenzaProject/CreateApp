# CREATE APP — ROADMAP PENGEMBANGAN HINGGA STABIL

**Developer:** SenzaProject  
**Package:** `com.senzaproject.createapp`  
**Teknologi:** Kotlin + Jetpack Compose  
**Sistem build:** Hybrid (Termux + GitHub Actions)  
**Target:** Android IDE & APK Builder  
**Target rilis:** Stable Release 1.0

> Dokumen ini adalah rencana pengerjaan. Status fitur tidak boleh dianggap selesai sebelum implementasi dan pengujiannya benar-benar berhasil.

## Prinsip pengerjaan

1. Kerjakan satu tahap pada satu waktu.
2. Jangan lanjut sebelum tahap aktif memenuhi kriteria selesai.
3. Jangan menampilkan fitur seolah-olah berfungsi jika belum diimplementasikan.
4. Setiap perubahan harus dapat dibangun, diuji, dan di-commit.
5. Lindungi source code, data proyek, token, password, dan keystore.
6. Catat bug, keputusan teknis, dan perubahan pada dokumentasi.

## FASE 1 — Fondasi aplikasi

### Tahap 1 — Persiapan proyek dan arsitektur
**Tujuan:** fondasi teknis yang konsisten dan mudah dikembangkan.

Pekerjaan:
- Siapkan struktur proyek Kotlin dan modul.
- Konfigurasikan Gradle, Gradle Wrapper, Android SDK, dan Jetpack Compose.
- Buat navigasi aplikasi dan komponen UI bersama.
- Tentukan arsitektur data, domain, dan presentation.
- Siapkan dependency injection bila diperlukan.
- Siapkan logging, error handling, dan konfigurasi build.
- Hubungkan Git dan repository GitHub.

Kriteria selesai:
- Proyek dapat dikompilasi.
- APK debug dapat dipasang dan dibuka.
- Modul dan navigasi dasar berjalan.
- Tidak ada error konfigurasi build yang diketahui.

### Tahap 2 — Implementasi UI Tahap 1
Layar:
- Splash Screen.
- Dashboard.
- Project Manager.
- Code Editor awal.
- Bottom Navigation dan tema gelap.

Kriteria selesai:
- Navigasi bekerja.
- Layout responsif pada ukuran layar yang ditargetkan.
- Tidak ada layar kosong atau crash saat berpindah halaman.
- Komponen visual dibuat reusable.

### Tahap 3 — Project Manager dan File Explorer
Pekerjaan:
- Membuat, membuka, mengganti nama, dan menghapus proyek.
- Menampilkan struktur folder dan file.
- Membuat file Kotlin, XML, JSON, dan Gradle.
- Membaca, menulis, dan menyimpan file.
- Menggunakan Android Storage Access Framework untuk akses file yang sesuai.
- Menangani izin dan lokasi penyimpanan.

Kriteria selesai:
- Proyek tetap tersedia setelah aplikasi ditutup dan dibuka kembali.
- Operasi file akurat dan aman.
- Penghapusan memerlukan konfirmasi.
- Kegagalan izin atau penyimpanan ditangani dengan jelas.

## FASE 2 — Fitur utama IDE

### Tahap 4 — Advanced Code Editor
Pekerjaan:
- Syntax highlighting Kotlin.
- Nomor baris, auto indentation, dan auto bracket.
- Pencarian dan replace.
- Undo/redo dan multi-tab.
- Auto-save serta indikator perubahan.
- Navigasi ke baris tertentu.
- Keyboard yang nyaman untuk coding.
- Dukungan bertahap untuk XML, JSON, dan Gradle Kotlin DSL.
- Evaluasi code completion dan code folding.

Kriteria selesai:
- File kecil dan besar yang ditargetkan dapat dibuka dan diedit.
- Undo/redo konsisten.
- Perubahan tidak hilang ketika aplikasi ditutup atau proses dihentikan.
- Editor tetap responsif pada perangkat target.

### Tahap 5 — Terminal dan integrasi Termux
Pekerjaan:
- Antarmuka terminal, output, error, dan riwayat perintah.
- Pengaturan working directory.
- Kontrol proses dan pembatalan.
- Integrasi Termux melalui mekanisme resmi yang didukung, misalnya RUN_COMMAND/Termux:API sesuai konfigurasi dan izin.
- Validasi lingkungan dan deteksi Termux.

Kriteria selesai:
- Perintah yang didukung berjalan dan hasilnya ditampilkan.
- Kegagalan izin atau Termux yang tidak tersedia dijelaskan.
- Proses dapat dihentikan dengan aman.
- UI tidak membeku saat perintah berjalan.

**Catatan:** Android membatasi komunikasi antar-aplikasi. Jangan mengasumsikan aplikasi dapat menjalankan shell Termux tanpa konfigurasi, izin, dan mekanisme integrasi yang sesuai.

### Tahap 6 — Gradle Build System
Pekerjaan:
- Integrasi Gradle Wrapper.
- Build Debug dan Release.
- Build AAB.
- Progress, log, dan status build.
- Pembatalan build.
- Pengumpulan error dan riwayat build.
- Jalur build lokal melalui Termux dan build cloud melalui GitHub Actions.

Kriteria selesai:
- Build menghasilkan artefak yang valid.
- Status sukses/gagal sesuai hasil proses nyata.
- Log dapat diperiksa.
- Build dapat dibatalkan bila mekanismenya mendukung.

**Catatan:** Build lokal memerlukan JDK, Android SDK, platform tools, dan versi Gradle/plugin yang kompatibel. Uji pada perangkat nyata sebelum menyatakan dukungan universal.

### Tahap 7 — APK Manager
Pekerjaan:
- Daftar APK/AAB dan detail ukuran serta waktu build.
- Install, share, export, dan hapus artefak.
- Membuka lokasi output.
- Riwayat hasil build.

Kriteria selesai:
- Aplikasi hanya menampilkan artefak yang benar-benar tersedia.
- APK dapat dipasang pada perangkat yang kompatibel.
- Ukuran dan lokasi akurat.
- Penghapusan aman dan terkonfirmasi.

## FASE 3 — Distribusi dan integrasi

### Tahap 8 — APK Signing
Pekerjaan:
- Membuat dan mengimpor keystore.
- Mengatur alias dan konfigurasi signing.
- Menandatangani APK/AAB.
- Memverifikasi signature.
- Menyediakan panduan backup keystore.

Kriteria selesai:
- Artefak release memiliki signature valid.
- Keystore dan password tidak masuk repository.
- Secret tidak muncul di log.
- Pengguna mendapat peringatan tentang pentingnya backup keystore.

### Tahap 9 — GitHub Integration
Pekerjaan:
- Autentikasi aman.
- Daftar repository.
- Clone, commit, push, pull, branch, merge, sync, dan status.
- Deteksi serta penanganan konflik.

Kriteria selesai:
- Operasi Git inti berhasil pada repository uji.
- Token tidak ditanam di source code atau log.
- Konflik ditampilkan tanpa menimpa perubahan secara diam-diam.
- Pengguna mengonfirmasi operasi yang berisiko.

### Tahap 10 — GitHub Actions Cloud Build
Pekerjaan:
- Workflow build.
- Memulai workflow dari aplikasi.
- Mengirim parameter yang diperlukan.
- Memantau status dan log.
- Mengunduh APK/AAB hasil build.

Kriteria selesai:
- Workflow dapat dipicu dengan autentikasi yang aman.
- Status dan log berasal dari workflow sebenarnya.
- Artefak dapat diunduh dan diverifikasi.
- Kegagalan jaringan dan workflow ditangani.

## FASE 4 — Fitur cerdas dan pengalaman pengguna

### Tahap 11 — Error Analyzer dan AI Code Assistant
Pekerjaan:
- Membaca error Kotlin dan Gradle.
- Menampilkan file, baris, kolom, dan log terkait.
- Mengelompokkan error dan warning.
- Menjelaskan masalah dan menyarankan perbaikan.
- Menampilkan diff/preview sebelum menerapkan perubahan.

Kriteria selesai:
- Lokasi error sesuai log/sumber.
- Saran diberi label sebagai saran, bukan kepastian.
- Kode tidak diubah tanpa persetujuan pengguna.
- Perubahan dapat dibatalkan.

### Tahap 12 — App Preview dan Device Tools
Pekerjaan:
- Preview yang sesuai dengan teknologi yang digunakan.
- Informasi ukuran layar dan orientasi.
- Install dan jalankan APK pada perangkat.
- Screenshot dan informasi perangkat.
- Evaluasi integrasi emulator bila layak.

Kriteria selesai:
- Preview tidak mengklaim sebagai emulator penuh bila bukan.
- APK yang diuji benar-benar terpasang dan dijalankan.
- Kegagalan kompatibilitas dijelaskan.

### Tahap 13 — Settings, keamanan, dan performa
Pekerjaan:
- Tema gelap/terang, bahasa, ukuran font.
- Pengaturan Gradle, SDK, dan terminal.
- Pengelolaan penyimpanan.
- Backup/restore.
- Pengelolaan izin, token, dan secret.
- Optimasi memori dan proses latar belakang.

Kriteria selesai:
- Preferensi tersimpan dan dipulihkan.
- Data sensitif disimpan dengan mekanisme aman.
- Izin diminta saat diperlukan.
- Kondisi penyimpanan rendah dan kegagalan proses ditangani.

## FASE 5 — Pengujian dan rilis

### Tahap 14 — Quality Assurance dan Stabilization
Pengujian wajib:
- Unit test.
- UI test.
- Integration test.
- Debug dan Release build.
- APK install test.
- Signing verification.
- GitHub dan cloud build test.
- Storage/persistence test.
- Security review.
- Performance dan crash test.
- Uji jaringan terputus, ruang penyimpanan rendah, build dibatalkan, dan aplikasi dihentikan saat menyimpan.

Kriteria selesai:
- Semua pengujian kritis lulus.
- Tidak ada bug blocker/critical yang diketahui.
- Bug lain tercatat dan diprioritaskan.
- Tidak ada secret di repository atau log.
- Tidak ada regresi pada fitur yang sebelumnya lulus.

### Tahap 15 — Alpha, Beta, Release Candidate, Stable 1.0
Urutan:
1. Internal Alpha: pengujian developer.
2. Closed Beta: pengujian pengguna terbatas dengan proyek nyata.
3. Release Candidate: fitur dibekukan, fokus pada bug.
4. Stable 1.0: APK/AAB final, release signing, dokumentasi, changelog, dan prosedur backup.

Kriteria rilis:
- Build release berhasil dan signature valid.
- Instalasi dan upgrade diuji.
- Catatan rilis dan known issues tersedia.
- Keystore dan backup penting aman.
- Ada prosedur pelaporan bug dan pemulihan.

## Prioritas pelaksanaan

| Prioritas | Tahapan |
|---|---|
| P0 | 1. Arsitektur, 2. UI dasar, 3. Project Manager/File Explorer, 4. Code Editor |
| P1 | 5. Termux, 6. Gradle Build, 7. APK Manager |
| P2 | 8. Signing, 9. GitHub, 10. Cloud Build |
| P3 | 11. Error Analyzer, 12. Preview, 13. Settings/Security |
| P4 | 14. QA/Stabilization, 15. Stable Release |

## Definition of Done — syarat setiap tahap dinyatakan selesai

- [ ] Source code sudah dibuat dan ditinjau.
- [ ] Build berhasil.
- [ ] Fitur benar-benar berjalan, bukan mockup.
- [ ] Pengujian terkait lulus.
- [ ] Tidak ada error kritis yang diketahui.
- [ ] Tidak merusak fitur sebelumnya (regression test).
- [ ] Perubahan sudah di-commit ke Git.
- [ ] Dokumentasi dan known issues diperbarui.
- [ ] APK pengujian dipasang jika relevan.

## Estimasi kasar

Perkiraan untuk satu developer dengan bantuan AI: **12–22 minggu**, sangat bergantung pada perangkat, kompatibilitas build lokal, cakupan editor, integrasi Termux/GitHub, dan hasil pengujian. Ini bukan jaminan tenggat.

## Alur kerja per tahap

1. Tentukan ruang lingkup dan kriteria penerimaan.
2. Implementasikan source code.
3. Build melalui Termux atau GitHub Actions.
4. Periksa log dan hasil pada perangkat.
5. Perbaiki bug sampai kriteria terpenuhi.
6. Jalankan regression test.
7. Commit dan dokumentasikan.
8. Baru mulai tahap berikutnya.

**Aturan utama:** jangan melanjutkan hanya karena UI selesai. Tahap dinyatakan selesai setelah fitur nyata dan pengujiannya lulus.
