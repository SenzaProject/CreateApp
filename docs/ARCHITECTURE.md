# Arsitektur Create App

## Lapisan dan modul

```
            ┌───────────────────────────── :app ─────────────────────────────┐
            │  Compose UI · Navigasi · ViewModel · AppContainer (DI manual)  │
            └──────┬──────────────┬───────────────────────┬──────────────────┘
                   │              │                       │
                   ▼              ▼                       ▼
               :domain  ◄──────  :data              :core:common
        (model, kontrak,        (implementasi        (logging, dispatcher,
         use case — JVM)         repository)          crash reporter)
                   ▲
                   │
   :core:project · :core:editor · :core:terminal · :core:builder · :core:git
              (kerangka; diisi pada tahap masing-masing)
```

Aturan dependensi:

1. `:domain` tidak boleh bergantung pada Android atau modul lain (Kotlin JVM murni).
2. `:data` dan `:core:*` boleh bergantung pada `:domain`, bukan sebaliknya.
3. `:app` adalah satu-satunya tempat modul dirakit (DI) dan satu-satunya yang memiliki UI.
4. Antar modul `:core:*` tidak saling bergantung kecuali lewat kontrak di `:domain`.

## Alur data

Contoh 1 — informasi aplikasi (Pengaturan):
`SettingsScreen` → `SettingsViewModel` → `GetAppDiagnosticsUseCase` → `DiagnosticsRepository` (kontrak di `:domain`)
→ `DiagnosticsRepositoryImpl` (di `:data`) → `AppResult<AppDiagnostics>` → `SettingsUiState` (StateFlow).

Contoh 2 — daftar proyek (Dashboard dan Project Manager):
`ProjectsScreen` / `HomeScreen` → `ProjectsViewModel` / `HomeViewModel` → `ObserveProjectsUseCase` → `ProjectRepository`
(kontrak di `:domain`) → `FileProjectRepository` (`:core:project`, membaca folder `filesDir/projects`) → `Flow<List<Project>>`.
Penyaringan dan pengurutan memakai fungsi murni `filterProjects` di `:domain` (diuji unit test).
Daftar disegarkan setiap kali `ProjectStorage.changes` naik (buat/ganti nama/hapus/simpan file).

Contoh 3 — File Explorer dan simpan file (Tahap 3):
`ExplorerScreen` → `ExplorerViewModel` → `ProjectFilesRepository` (kontrak di `:domain`) → `FileProjectFilesRepository`
(`:core:project`) → `AppResult<…>`. Mengetuk file memanggil `EditorViewModel.openProjectFile` (berskala Activity) lalu membuka
layar Editor; `EditorViewModel.save` menulis lewat `ProjectFilesRepository.writeText` (atomik).
Impor/ekspor folder: `ProjectsViewModel` → `ProjectTransferRepository` → `SafProjectTransfer` (SAF; penyalinan lewat `TreeCopier`).

State yang ditulis pengguna secara langsung (teks pencarian, isi editor) disimpan sebagai state Compose sinkron di
ViewModel agar pengetikan tidak tertunda atau melompat.

## Error handling dan logging

- Operasi yang bisa gagal mengembalikan `AppResult<T>` (`Success` / `Failure(AppError)`), dibungkus
  dengan `runCatchingApp`. `CancellationException` selalu dilempar ulang.
- `Logger` adalah kontrak di `:domain`; implementasi `AndroidLogger` menulis ke Logcat dan ke file
  (`files/logs/app.log`, diputar pada 512 KB).
- `LogRedactor` menyamarkan token GitHub dan pola `password=`/`token=`/`secret=` sebelum ditulis.
  Kode tetap tidak boleh mengirim rahasia ke logger.
- `CrashReporter` menyimpan crash terakhir ke `files/logs/last_crash.txt`; Beranda menampilkannya
  pada peluncuran berikutnya.

## Keputusan teknis

| Keputusan | Alasan |
|---|---|
| DI manual (`AppContainer`) | Tidak butuh KSP/kapt sehingga build Tahap 1 sederhana dan stabil. Evaluasi Hilt/Koin bila graf membesar. |
| Folder `core/builder` (bukan `core/build`) | `build/` adalah folder output Gradle dan diabaikan `.gitignore`; nama itu akan menyembunyikan source. |
| Paket `domain.builder` dan `ui.builder` (bukan `...build`) | Alasan sama: `.gitignore` mengabaikan setiap folder bernama `build/`. Folder bernama `build` di sumber hilang dari GitHub tanpa peringatan. |
| Bottom navigation: Beranda, Proyek, Build, GitHub, Pengaturan | Mengikuti desain UI awal. Editor dan Terminal dibuka sebagai layar sekunder. |
| `material-icons-extended` | Ikon Folder/Code/Cloud; dikecilkan oleh R8 saat release diaktifkan (Tahap 8/14). |
| minSdk 26 | Adaptive icon tanpa PNG lama; cukup luas untuk perangkat modern. |
| Rute navigasi string | Menghindari plugin serialization. |
| `popUpTo(HOME)` untuk tab utama | Splash dihapus dari back stack, sehingga dasar back stack adalah Home. |
| Buffer editor di ViewModel level Activity | Bertahan saat layar Editor ditutup lalu dibuka lagi; bukan penyimpanan permanen. |
| Proyek = folder di `filesDir/projects` (tanpa database) | Folder adalah satu-satunya sumber kebenaran, jadi tidak ada indeks yang bisa tidak sinkron. Metadata (`.createapp/project.properties`) ditulis atomik; metadata rusak/hilang tidak membuat proyek hilang. Tidak butuh izin penyimpanan luas. |
| Impor/ekspor lewat SAF (salin, bukan tautan) | Pengguna memilih folder lewat pemilih sistem; impor menyalin ke penyimpanan privat lewat folder staging lalu dipindahkan, sehingga proyek tidak setengah jadi dan tidak bergantung pada izin SAF yang bisa dicabut. |
| Id proyek = nama folder, tetap saat ganti nama | Ganti nama hanya mengubah nama tampilan; tidak ada path yang berubah dan tidak ada tabrakan folder. |
| Penulisan file atomik (`.tmp` + `ATOMIC_MOVE`) | Bila proses mati saat menyimpan, file lama tetap utuh. Dekode UTF-8 ketat agar file non-UTF-8 tidak rusak saat disimpan ulang. |
| Semua path melewati `ProjectStorage.resolve` | Menolak `..`, path absolut, folder metadata, dan symlink yang keluar dari folder proyek. |
| Tidak ada izin di manifest | Izin ditambahkan hanya saat fiturnya ada. |

## Versi toolchain

Gradle 8.13 · AGP 8.10.1 · Kotlin 2.1.21 · Compose BOM 2025.06.01 · compile/target SDK 35 · minSdk 26 · JDK 17.
Semua versi berada di `gradle/libs.versions.toml`.
