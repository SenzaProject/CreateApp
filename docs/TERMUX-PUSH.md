# Push ke GitHub dari Termux

Ganti `EMAIL_GITHUB_ANDA` dengan email akun GitHub Anda. **Jangan pernah** menulis token di file
proyek atau memasukkannya ke commit.

## Pertama kali (belum ada repository lokal)

```sh
pkg update && pkg install -y git unzip
termux-setup-storage                       # sekali saja, izinkan akses penyimpanan

cd ~
unzip /sdcard/Download/CreateApp-Tahap3.zip   # sesuaikan lokasi file
cd CreateApp

git init -b main
git config user.name  "SenzaProject"
git config user.email "EMAIL_GITHUB_ANDA"

git add .
git status                                 # periksa: tidak ada *.jks, *.keystore, token
git commit -m "Tahap 3: Project Manager dan File Explorer"

git remote add origin https://github.com/SenzaProject/CreateApp.git
git push -u origin main
```

## Memperbarui repository yang sudah ada (setiap tahap baru)

Cara paling mudah memakai `scripts/termux-update.sh`. Skrip ini mengganti isi repo dengan isi zip (file yang dihapus di tahap
baru ikut terhapus, `.git` tidak disentuh), lalu commit dan push. Ia menolak berjalan bila bukan repo CreateApp, bila ada
perubahan lokal yang belum di-commit, atau bila isi zip tidak tampak seperti proyek CreateApp.

```sh
cd ~/CreateApp                                  # folder repo hasil clone/push sebelumnya (sesuaikan)
git status                                      # harus bersih ("nothing to commit")
git config user.email                           # pastikan email akun GitHub Anda; bila kosong:
# git config user.email "EMAIL_GITHUB_ANDA" && git config user.name "SenzaProject"

# Update pertama kali (repo lama belum punya skrip): ambil skrip dari zip dulu
mkdir -p ~/tmp-update
unzip -o -q /sdcard/Download/CreateApp-Tahap6.zip 'CreateApp/scripts/termux-update.sh' -d ~/tmp-update
sh ~/tmp-update/CreateApp/scripts/termux-update.sh /sdcard/Download/CreateApp-Tahap6.zip "Tahap 6: Gradle Build lewat Termux dan GitHub Actions"
```

Untuk tahap berikutnya skrip sudah ada di repo, cukup:

```sh
cd ~/CreateApp
sh scripts/termux-update.sh /sdcard/Download/CreateApp-TahapN.zip "Tahap N: ringkasan"
```

Bila file zip ada di Download dan Termux belum punya akses penyimpanan, jalankan `termux-setup-storage` sekali dan izinkan.
Lupa lokasi repo? `find ~ -maxdepth 3 -name .git -path "*CreateApp*"`.

### Cara manual (tanpa skrip)

```sh
cd ~
rm -rf CreateApp-repo CreateApp-baru
git clone https://github.com/SenzaProject/CreateApp.git CreateApp-repo
mkdir CreateApp-baru && unzip -q /sdcard/Download/CreateApp-Tahap6.zip -d CreateApp-baru

cd CreateApp-repo
git config user.name  "SenzaProject"
git config user.email "EMAIL_GITHUB_ANDA"

find . -mindepth 1 -maxdepth 1 ! -name .git -exec rm -rf {} +    # kosongkan isi lama, simpan .git
cp -a ../CreateApp-baru/CreateApp/. .                            # salin isi tahap baru

git add -A
git status                                 # periksa daftar berkas baru, diubah, dihapus
git commit -m "Tahap 6: Gradle Build lewat Termux dan GitHub Actions"
git push origin HEAD
```

Isi repository harus diganti seluruhnya (bukan sekadar disalin di atasnya), karena tahap baru bisa menghapus berkas lama
dan sisa berkas lama membuat build gagal.

## Autentikasi

Saat Git meminta kredensial:

- Username: `SenzaProject`
- Password: **Personal Access Token** GitHub (bukan password akun).

Token dibuat di GitHub -> Settings -> Developer settings -> Personal access tokens, dengan izin
minimal untuk repository `CreateApp` (Contents: Read and write).

Alternatif yang lebih aman dari mengetik token berulang: `pkg install gh` lalu `gh auth login`.

## Setelah push

Buka tab **Actions** di repository dan tunggu workflow **Android CI**. Workflow menguji helper Termux, menjalankan
`./gradlew test`, lalu `:app:assembleDebug`. Bila berhasil, unduh artifact **CreateApp-debug-apk**.
Bila gagal, salin log langkah yang merah (atau unduh artifact **laporan-test**) dan kirimkan untuk diperbaiki.

APK/AAB release (belum ditandatangani sampai Tahap 8): di tab Actions pilih **Android CI** → **Run workflow** → kolom
`task` pilih `assembleRelease` atau `bundleRelease`; hasilnya muncul sebagai artifact `CreateApp-release-apk-unsigned`
atau `CreateApp-release-aab-unsigned`.
