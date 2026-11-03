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

## Alur data (contoh: kartu informasi aplikasi di Beranda)

`HomeScreen` → `HomeViewModel` → `GetAppDiagnosticsUseCase` → `DiagnosticsRepository` (kontrak di `:domain`)
→ `DiagnosticsRepositoryImpl` (di `:data`) → `AppResult<AppDiagnostics>` → `HomeUiState` (StateFlow).

Pola ini dipakai ulang untuk fitur berikutnya.

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
| Rute navigasi string | Menghindari plugin serialization pada Tahap 1. |
| Tidak ada izin di manifest | Izin ditambahkan hanya saat fiturnya ada. |

## Versi toolchain

Gradle 8.13 · AGP 8.10.1 · Kotlin 2.1.21 · Compose BOM 2025.06.01 · compile/target SDK 35 · minSdk 26 · JDK 17.
Semua versi berada di `gradle/libs.versions.toml`.
