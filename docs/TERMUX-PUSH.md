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

## Memperbarui repository yang sudah ada (Tahap 2 -> Tahap 3)

Tahap baru **menghapus** satu berkas lama (`EmptyProjectRepository.kt`). Menyalin berkas di atas repository
lama saja akan meninggalkan berkas itu dan membuat build gagal, jadi isi repository diganti seluruhnya
(kecuali `.git`):

```sh
cd ~
rm -rf CreateApp-repo CreateApp-tahap3
git clone https://github.com/SenzaProject/CreateApp.git CreateApp-repo
mkdir CreateApp-tahap3 && unzip -q /sdcard/Download/CreateApp-Tahap3.zip -d CreateApp-tahap3

cd CreateApp-repo
git config user.name  "SenzaProject"
git config user.email "EMAIL_GITHUB_ANDA"

find . -mindepth 1 -maxdepth 1 ! -name .git -exec rm -rf {} +    # kosongkan isi lama, simpan .git
cp -a ../CreateApp-tahap3/CreateApp/. .                          # salin isi Tahap 3

git add -A
git status                                 # periksa daftar: berkas baru, diubah, dan 1 dihapus
git commit -m "Tahap 3: Project Manager dan File Explorer"
git push origin HEAD
```

## Autentikasi

Saat Git meminta kredensial:

- Username: `SenzaProject`
- Password: **Personal Access Token** GitHub (bukan password akun).

Token dibuat di GitHub -> Settings -> Developer settings -> Personal access tokens, dengan izin
minimal untuk repository `CreateApp` (Contents: Read and write).

Alternatif yang lebih aman dari mengetik token berulang: `pkg install gh` lalu `gh auth login`.

## Setelah push

Buka tab **Actions** di repository dan tunggu workflow **Android CI**. Workflow menjalankan
`./gradlew test` lalu `:app:assembleDebug`. Bila berhasil, unduh artifact **CreateApp-debug-apk**.
Bila gagal, salin log langkah yang merah (atau unduh artifact **laporan-test**) dan kirimkan untuk diperbaiki.
