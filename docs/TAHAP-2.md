# Tahap 2 — Implementasi UI

**Status: kode selesai ditulis. Kriteria selesai BELUM terbukti** (belum dikompilasi dan belum dijalankan di perangkat).
Tahap 2 dibangun di atas kode Tahap 1; hasil CI Tahap 1 belum dikonfirmasi saat berkas ini ditulis.

## Aturan yang dipegang

Tidak ada tombol tanpa fungsi, tidak ada data palsu, tidak ada fitur yang tampak aktif tetapi tidak bekerja.
Karena itu UI Tahap 2 membedakan dengan jelas apa yang **sudah bekerja** dan apa yang **baru dijadwalkan**.

## Yang sudah bekerja (nyata)

| Bagian | Isi |
|---|---|
| Splash | Logo + tagline + indikator indeterminate, tampil minimal 900 ms lalu pindah ke Dashboard (tidak muncul lagi saat tombol kembali). Indikator bukan persentase progres. |
| Dashboard | Kartu sambutan; 6 pintasan (Proyek Baru, Buka Proyek, Editor, Build APK, Terminal, GitHub) yang berpindah ke layar tujuan; daftar Proyek Terbaru; kartu laporan crash bila ada. Grid 2 kolom di ponsel, 3 kolom di layar lebar. |
| Project Manager | Kolom pencarian, filter Semua/Terbaru/Favorit, daftar proyek (logika filter diuji unit test), status kosong yang menjelaskan jadwal fiturnya. |
| Editor awal | Satu buffer teks sementara: nomor baris, scroll vertikal dan horizontal (tanpa pembungkusan baris), posisi kursor, jumlah baris/karakter. Buffer bertahan saat pindah layar dan rotasi selama aplikasi hidup. |
| Navigasi | 5 tab bawah (Beranda, Proyek, Build, GitHub, Pengaturan); bottom bar tersembunyi di Splash, Editor, dan Terminal; Editor dan Terminal dibuka sebagai layar penuh. |
| Tema | Gelap (default) sesuai palet; skema terang sudah ada, pemilihnya menunggu Settings (Tahap 13). |
| Pengaturan | Tentang, plus informasi aplikasi dan status modul (dipindahkan dari Dashboard). |
| Komponen reusable | `ContentContainer`, `EmptyState`, `ActionTile`/`ActionTileGrid`, `ProjectListItem`, `SectionCard`, `StatusChip`, `AppTopBar`, `PlaceholderScreen`. |

## Yang BELUM ada (dijadwalkan, ditandai jujur di UI)

- Membuat/membuka/mengelola proyek dan menyimpan file — Tahap 3. Daftar proyek selalu kosong karena
  `EmptyProjectRepository` hanya penampung sementara.
- Syntax highlighting, auto-indent, undo/redo, multi-tab, simpan file — Tahap 4 (dan simpan file Tahap 3).
- Terminal — Tahap 5. Build — Tahap 6. GitHub — Tahap 9.

## Verifikasi (harus lulus sebelum Tahap 2 dinyatakan selesai)

| # | Uji | Hasil yang diharapkan |
|---|---|---|
| 1 | Workflow Android CI | Hijau (build + unit test) |
| 2 | Buka aplikasi | Splash ±1 detik lalu Dashboard; tombol kembali dari Dashboard keluar aplikasi (bukan kembali ke Splash) |
| 3 | Ketuk 5 tab bawah bergantian | Tidak ada layar kosong/crash; bottom bar tetap tampil |
| 4 | Ketuk 6 pintasan Dashboard | Editor dan Terminal tampil layar penuh tanpa bottom bar; sisanya membuka tab tujuan |
| 5 | Proyek: ketik di pencarian, ganti filter | Tidak crash; status kosong tampil |
| 6 | Editor: ketik beberapa baris, baris panjang, Enter berkali-kali | Nomor baris sejajar; baris panjang bisa digulir ke samping; status "Baris, Kolom" benar |
| 7 | Editor: kembali, buka lagi; putar layar | Teks tetap ada |
| 8 | Editor: buka keyboard | Area teks tidak tertutup keyboard |
| 9 | Putar ke landscape di semua layar | Tata letak tetap terbaca, tidak terpotong |
| 10 | Pengaturan | Informasi aplikasi dan daftar modul tampil |

## Risiko dan keterbatasan yang diketahui

1. Kode belum pernah dikompilasi; error API Compose mungkin muncul di CI (perbaikan biasanya kecil).
2. `ProjectListItem` baru tampil saat ada data (Tahap 3); sebelum itu hanya terverifikasi lewat kompilasi dan Preview.
3. Isi buffer editor hilang saat aplikasi ditutup (belum ada penyimpanan).
4. Ikon Terminal adalah vektor buatan sendiri (`ic_terminal.xml`); tampilannya perlu dicek visual.
5. Layout tablet hanya dibatasi lebar konten (720 dp); belum ada tata letak dua panel.
6. Mode terang belum bisa dipilih pengguna.

## Menutup Tahap 2

Setelah #1–#10 lulus: centang butir Tahap 2 di `docs/CHECKLIST.md`, lalu mulai Tahap 3.
