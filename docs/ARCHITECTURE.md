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
(kontrak di `:domain`) → `EmptyProjectRepository` (sementara, `:core:project`) → `Flow<List<Project>>`.
Penyaringan dan pengurutan memakai fungsi murni `filterProjects` di `:domain` (diuji unit test).
Pada Tahap 3 hanya implementasi repository yang diganti; UI dan use case tidak berubah.

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
| Bottom navigation: Beranda, Proyek, Build, GitHub, Pengaturan | Mengikuti desain UI awal. Editor dan Terminal dibuka sebagai layar sekunder. |
| `material-icons-extended` | Ikon Folder/Code/Cloud; dikecilkan oleh R8 saat release diaktifkan (Tahap 8/14). |
| minSdk 26 | Adaptive icon tanpa PNG lama; cukup luas untuk perangkat modern. |
| Rute navigasi string | Menghindari plugin serialization. |
| `popUpTo(HOME)` untuk tab utama | Splash dihapus dari back stack, sehingga dasar back stack adalah Home. |
| Buffer editor di ViewModel level Activity | Bertahan saat layar Editor ditutup lalu dibuka lagi; bukan penyimpanan permanen. |
| `EmptyProjectRepository` | UI Tahap 2 tampil jujur tanpa data palsu; diganti penyimpanan nyata di Tahap 3. |
| Tidak ada izin di manifest | Izin ditambahkan hanya saat fiturnya ada. |

## Versi toolchain

Gradle 8.13 · AGP 8.10.1 · Kotlin 2.1.21 · Compose BOM 2025.06.01 · compile/target SDK 35 · minSdk 26 · JDK 17.
Semua versi berada di `gradle/libs.versions.toml`.
