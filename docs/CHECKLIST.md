# CREATE APP — CHECKLIST PELAKSANAAN

Gunakan checklist ini untuk melacak progres. Tandai hanya setelah bukti implementasi dan pengujian tersedia.

## Tahap 1 — Arsitektur
- [ ] Struktur proyek dan modul
- [ ] Gradle, SDK, Compose
- [ ] Navigasi dan komponen bersama
- [ ] Logging dan error handling
- [ ] Debug APK berhasil dipasang

## Tahap 2 — UI dasar
- [ ] Splash
- [ ] Dashboard
- [ ] Project Manager
- [ ] Editor awal
- [ ] Navigasi dan tema

## Tahap 3 — Project/File Explorer
> Kode dan unit test ditulis (lihat `docs/TAHAP-3.md`), tetapi **belum dikompilasi dan belum dijalankan**. Jangan dicentang
> sebelum ada bukti: CI hijau (`./gradlew test`, `:app:assembleDebug`) dan uji manual di perangkat.
- [ ] Buat/buka proyek
- [ ] Operasi file/folder
- [ ] Persistensi
- [ ] SAF dan izin
- [ ] Uji pemulihan data

## Tahap 4 — Code Editor
> Catatan: berkas `CreateAppTahap4.zip` yang menjadi dasar Tahap 5 ternyata masih berisi kode Tahap 3 (editor belum punya highlighting,
> undo/redo, atau multi-tab). Tahap 4 **belum dikerjakan** dan tidak dicentang.
- [ ] Syntax highlighting
- [ ] Line numbers dan indentasi
- [ ] Search/replace
- [ ] Undo/redo dan multi-tab
- [ ] Auto-save dan uji file besar

## Tahap 5 — Termux
> Kode dan unit test ditulis (lihat `docs/TAHAP-5.md`), tetapi **belum dikompilasi dan belum dijalankan di perangkat**. Jangan dicentang
> sebelum ada bukti: CI hijau dan daftar uji perangkat di `docs/TAHAP-5.md` lulus (khususnya integrasi Termux).
- [ ] Deteksi Termux
- [ ] Izin dan integrasi resmi
- [ ] Jalankan perintah
- [ ] Output/error/history
- [ ] Pembatalan proses

## Tahap 6 — Gradle Build
> Kode, 93 unit test Kotlin, dan skrip Termux ditulis (lihat `docs/TAHAP-6.md`). Skrip helper dan logika inti sudah diuji dengan proses
> nyata (79 pemeriksaan shell, 232 pemeriksaan port JVM), tetapi Kotlin/Compose **belum dikompilasi** dan **belum dijalankan di
> perangkat**. Jangan dicentang sebelum CI hijau dan daftar uji perangkat di `docs/TAHAP-6.md` lulus.
- [ ] Gradle Wrapper
- [ ] Debug build
- [ ] Release build
- [ ] AAB build
- [ ] Log/progress/cancel

## Tahap 7 — APK Manager
> Kode, 57 unit test baru (domain), dan 30 pemeriksaan shell baru ditulis dan lulus di lingkungan pengembangan (lihat `docs/TAHAP-7.md`).
> Layar Compose **belum dikompilasi** dan semuanya **belum dijalankan di perangkat**. Jangan dicentang sebelum CI hijau dan daftar uji
> perangkat di `docs/TAHAP-7.md` lulus.
- [ ] Daftar artefak
- [ ] Detail APK/AAB
- [ ] Install/share/export
- [ ] Riwayat build

## Tahap 8 — Signing
- [ ] Buat/impor keystore
- [ ] Signing APK/AAB
- [ ] Verifikasi signature
- [ ] Proteksi secret dan backup

## Tahap 9 — GitHub
- [ ] Autentikasi aman
- [ ] Repository
- [ ] Clone/commit/push/pull
- [ ] Branch/merge/sync
- [ ] Konflik dan keamanan token

## Tahap 10 — Cloud Build
- [ ] Workflow
- [ ] Trigger workflow
- [ ] Status dan log
- [ ] Download artefak
- [ ] Verifikasi hasil

## Tahap 11 — Error Analyzer/AI
- [ ] Parser error
- [ ] Lokasi error
- [ ] Saran perbaikan
- [ ] Preview diff
- [ ] Persetujuan pengguna

## Tahap 12 — Preview/Device Tools
- [ ] Preview
- [ ] Informasi perangkat
- [ ] Install dan jalankan APK
- [ ] Screenshot
- [ ] Uji kompatibilitas

## Tahap 13 — Settings/Security
- [ ] Tema dan bahasa
- [ ] Pengaturan editor/build
- [ ] Backup/restore
- [ ] Izin dan secret
- [ ] Uji performa

## Tahap 14 — QA
- [ ] Unit/UI/integration tests
- [ ] Debug/release build
- [ ] Install/signing test
- [ ] Storage/security/performance
- [ ] Regression dan crash test

## Tahap 15 — Release
- [ ] Alpha
- [ ] Closed Beta
- [ ] Release Candidate
- [ ] Stable 1.0
- [ ] Dokumentasi dan changelog
