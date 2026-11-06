# Tahap 3 — Project Manager dan File Explorer

## Status verifikasi (baca ini dulu)

| Pemeriksaan | Hasil |
|---|---|
| Kode ditulis sesuai roadmap Tahap 3 | Ya |
| Unit test ditulis (domain 43, core/project 61 `@Test`) | Ya |
| **Kompilasi Kotlin / `./gradlew test` / `:app:assembleDebug`** | **Belum dijalankan** (lingkungan penulisan tidak punya kompiler Kotlin, Gradle, Android SDK, maupun internet) |
| Uji di perangkat Android | **Belum** |
| Pemeriksaan statis (kurung seimbang, import ada/tidak berlebih, simbol terdeklarasi, resource string dan jumlah argumen formatnya) | Lulus (`strings.xml` valid) |
| Asumsi perilaku JVM dan logika murni diuji ulang lewat port Java (32 pemeriksaan: atomic move, symlink, decoder UTF-8 ketat, `Properties`, slug, validasi nama/package, escape XML) | Lulus 32/32 |

Pemeriksaan statis dan port Java **bukan pengganti kompilator**. Kesalahan tipe, resolusi overload, atau
API Compose yang berbeda versi hanya akan ketahuan saat CI berjalan. Selain itu, dokumen Tahap 2 sendiri
mencatat kodenya belum pernah dikompilasi, sehingga dasar yang dipakai Tahap 3 juga belum terbukti.

**Cara membuktikan:** push ke GitHub (lihat `docs/TERMUX-PUSH.md`) dan lihat workflow **Android CI**.
Centang butir Tahap 3 di `docs/CHECKLIST.md` hanya setelah CI hijau **dan** daftar uji perangkat di bawah lulus.

## Cakupan (sesuai `docs/ROADMAP.md`)

- Membuat proyek: template **Kosong** (README) dan **Android Kotlin** (kerangka Gradle + satu Activity).
- Membuka proyek: ketuk proyek di daftar atau di "Proyek Terbaru" → File Explorer. "Buka folder" mengimpor folder luar lewat SAF.
- Mengganti nama, menandai favorit, mengekspor ke folder (SAF), dan menghapus proyek (dengan konfirmasi).
- File Explorer: jelajah folder, buat file (Kotlin, XML, JSON, Gradle, atau lainnya) dan folder, ganti nama, hapus (dengan konfirmasi).
- Membaca, menulis, dan menyimpan file teks UTF-8 (hingga 1 MB) dari Editor; penanda perubahan belum disimpan; konfirmasi saat keluar.
- Persistensi: proyek adalah folder di `filesDir/projects`, bertahan setelah aplikasi ditutup.
- Penanganan error yang jelas: setiap kegagalan dipetakan ke pesan yang dapat dilokalisasi (`ui/common/ErrorMessages.kt`).

## Keputusan desain

- **Penyimpanan privat + SAF.** Proyek hidup di penyimpanan privat aplikasi sehingga tidak perlu izin penyimpanan luas
  (`AndroidManifest.xml` tidak berubah). Akses ke folder luar hanya lewat pemilih folder sistem (SAF).
- **Folder = sumber kebenaran.** Tidak ada database. Metadata ada di `.createapp/project.properties`; bila rusak atau hilang,
  proyek tetap tampil dengan nama folder.
- **Id tetap saat ganti nama.** Ganti nama hanya mengubah nama tampilan.
- **Atomik dan aman.** Tulis ke `<nama>.tmp` lalu `ATOMIC_MOVE`; semua path melewati `ProjectStorage.resolve`
  (menolak `..`, absolut, folder metadata, symlink keluar proyek); dekode UTF-8 ketat; semua mutasi diserialkan dengan satu `Mutex`.
- **Impor lewat staging.** Isi disalin ke folder `.importing-<uuid>`, metadata ditulis, baru dipindahkan ke nama akhir; kegagalan
  atau pembatalan membersihkan staging, sisa lebih dari 24 jam dibersihkan saat pemindaian pertama.
- **Batas pengaman.** File teks ≤ 1 MB; impor ≤ 512 MB, ≤ 20.000 file, kedalaman ≤ 64; folder `build`, `.gradle`, `.cxx` tidak diimpor.

## Peta berkas

| Lokasi | Isi |
|---|---|
| `domain/.../project/` | `ProjectValidation`, `RelativePath`, `FileNode` (+ isi awal file), `ProjectTemplate`, kontrak `ProjectRepository`, `ProjectFilesRepository`, `ProjectTransferRepository` |
| `domain/.../result/` | `OperationFailure`, `OperationException`, `AppError.Operation` |
| `core/project/` | `ProjectStorage`, `FileProjectRepository`, `FileProjectFilesRepository`, `ProjectTemplates`, `TreeCopier`, `DocumentTree`, `SafProjectTransfer`, `ProjectNames`, `StorageExecutor`, `FileUtils` |
| `app/.../ui/projects/` | `ProjectsScreen`, `ProjectsViewModel`, `CreateProjectDialog` |
| `app/.../ui/explorer/` | `ExplorerScreen`, `ExplorerViewModel` |
| `app/.../ui/editor/` | Editor mode file: buka, simpan, penanda belum disimpan |
| `app/.../ui/components/Dialogs.kt` | `NameDialog`, `ConfirmDestructiveDialog` |

Dihapus: `EmptyProjectRepository` (digantikan `FileProjectRepository`).

## Cakupan unit test

- **domain:** validasi nama/package/slug, path relatif berbahaya, isi awal file dan penurunan package, pemetaan `AppError`.
- **core/project:** buat/ganti nama/favorit/hapus proyek; nama kembar dan slug kembar; rollback bila gagal; persistensi setelah
  "restart" (instance baru di folder yang sama); metadata rusak/hilang; sisa staging; operasi file (daftar terurut, buat, duplikat,
  ganti nama, hapus rekursif, UTF-8, biner, batas 1 MB, path traversal, symlink); sisa `.tmp` dari crash; `TreeCopier` (lewati folder
  build, batas file/ukuran/kedalaman, pembatalan, nama berbahaya dari penyedia dokumen); template proyek; deteksi package.
- **Tidak ada tes otomatis** untuk lapisan Compose/ViewModel dan untuk `SafProjectTransfer`/`DocumentTree` (butuh perangkat). Ini celah yang diketahui.

## Daftar uji manual di perangkat (wajib sebelum mencentang Tahap 3)

1. Buat proyek **Kosong** "Uji Satu" → muncul di daftar; buka → ada `README.md`.
2. Buat proyek **Android Kotlin** dengan package `com.contoh.uji` → ada `app/src/main/kotlin/com/contoh/uji/MainActivity.kt`.
3. Coba nama kosong, `a/b`, nama yang sudah ada, dan package `app` → pesan kesalahan jelas, tidak ada proyek setengah jadi.
4. Di Explorer: buat folder `src`, buat file Kotlin di dalamnya (isi awal berisi `class …`), XML, JSON, dan `x.gradle.kts`.
5. Ganti nama dan hapus file/folder; konfirmasi hapus muncul dan **Batal** tidak menghapus apa pun.
6. Buka file → ubah → tombol Simpan aktif dan tab bertanda •; tekan Kembali → dialog Simpan/Buang/Batal; Simpan → file tersimpan.
7. Tutup aplikasi sepenuhnya (geser dari recent) lalu buka lagi → proyek dan isi file yang tersimpan masih ada.
8. "Buka folder": pilih folder berisi proyek (mis. dari Download) → muncul sebagai proyek baru; folder asli tidak berubah.
9. Menu proyek → "Ekspor ke folder" → pilih folder → subfolder berisi proyek (tanpa `.createapp`) terbuat.
10. Batalkan pemilih folder (impor dan ekspor) → tidak terjadi apa-apa, tidak ada error.
11. Hapus proyek → konfirmasi → hilang dari daftar dan dari "Proyek Terbaru".
12. Putar layar saat di Explorer dan Editor → tidak crash, daftar dan isi tetap.

## Batasan yang diketahui

- Bila pembacaan penyimpanan gagal total, daftar proyek ditampilkan kosong (dan dicatat di log), bukan pesan error.
- Template Android hanya kerangka; belum ada Gradle Wrapper di dalamnya dan buildnya baru diverifikasi pada Tahap 6.
- Tidak ada simpan otomatis, undo/redo, atau multi-tab (Tahap 4). Perubahan yang belum disimpan hilang bila proses aplikasi dimatikan sistem.
- Impor menyalin isi (bukan menautkan); perubahan di folder asal tidak tersinkron. Beberapa folder akar (mis. seluruh Download) dibatasi sistem; pilih subfolder.
- Penyalinan besar menahan kunci penyimpanan sehingga operasi lain menunggu sampai selesai.
- Penyedia dokumen dengan entri tanpa nama membuat impor gagal dengan pesan folder tidak valid.
- Nama proyek tampilan harus unik (tanpa membedakan huruf besar/kecil).
