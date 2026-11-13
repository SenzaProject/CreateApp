# Build APK langsung di HP (tanpa GitHub)

Alur: **Create App → Termux → Gradle → hasil kembali ke Create App.** Tidak ada push ke GitHub untuk membuild.

## Persiapan sekali saja (di Termux)

```sh
pkg update && pkg install -y openjdk-17 gradle unzip git
termux-setup-storage
mkdir -p ~/.termux && echo "allow-external-apps = true" >> ~/.termux/termux.properties
termux-reload-settings            # atau tutup Termux sepenuhnya lalu buka lagi
```

1. Pasang **Android SDK** untuk Termux dan set `ANDROID_HOME` (platform + build-tools sesuai proyek) di `~/.bashrc`.
2. Di ARM, `aapt2` bawaan Gradle sering tidak jalan. Gunakan `aapt2` untuk Termux dan arahkan lewat
   `android.aapt2FromMavenOverride=/path/ke/aapt2` di `~/.gradle/gradle.properties`.
3. Android: Setelan → Aplikasi → Termux → izin **Run commands in Termux environment** untuk Create App, dan matikan
   **penghemat baterai** untuk Termux dan Create App (HiOS/Infinix cepat mematikan proses latar).

## Memakai

1. Tab **Build** → pilih folder bersama (mis. `Download/CreateAppBuild`) satu kali.
2. **Periksa lingkungan**: semua butir wajib harus OK (Memori/Ruang/Wake lock hanya peringatan).
3. Pilih proyek dan jenis → **Mulai build**. Kartu Log menampilkan log langsung dan task terakhir.
4. Selesai → **Buka APK Manager** untuk memasang/membagikan/mengekspor.

## Bila bermasalah

| Gejala | Penyebab umum | Tindakan |
|---|---|---|
| "Termux tidak merespons" | izin/`allow-external-apps` belum aktif | ulangi persiapan nomor 3 |
| Build berhenti sendiri | Termux dimatikan sistem | matikan penghemat baterai; tutup aplikasi lain; pakai Debug |
| `OutOfMemoryError` | RAM kurang | tutup aplikasi lain; naikkan `org.gradle.jvmargs` di `gradle.properties` proyek |
| Gagal di `aapt2` | aapt2 bawaan tidak cocok ARM | lihat persiapan nomor 2 |
| Release tidak bisa dipasang | belum ditandatangani | pasang APK Debug; penandatanganan di Tahap 8 |
