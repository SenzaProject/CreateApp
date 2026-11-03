# Push ke GitHub dari Termux

Ganti `EMAIL_GITHUB_ANDA` dengan email akun GitHub Anda. **Jangan pernah** menulis token di file
proyek atau memasukkannya ke commit.

```sh
pkg update && pkg install -y git unzip
termux-setup-storage                       # sekali saja, izinkan akses penyimpanan

cd ~
unzip /sdcard/Download/CreateApp-Tahap1.zip   # sesuaikan lokasi file
cd CreateApp

git init -b main
git config user.name  "SenzaProject"
git config user.email "EMAIL_GITHUB_ANDA"

git add .
git status                                 # periksa: tidak ada *.jks, *.keystore, token
git commit -m "Tahap 1: fondasi proyek dan arsitektur"

git remote add origin https://github.com/SenzaProject/CreateApp.git
git push -u origin main
```

## Autentikasi

Saat Git meminta kredensial:

- Username: `SenzaProject`
- Password: **Personal Access Token** GitHub (bukan password akun).

Token dibuat di GitHub → Settings → Developer settings → Personal access tokens, dengan izin
minimal untuk repository `CreateApp` (Contents: Read and write).

Alternatif yang lebih aman dari mengetik token berulang: `pkg install gh` lalu `gh auth login`.

## Setelah push

Buka tab **Actions** di repository dan tunggu workflow **Android CI**. Bila berhasil, unduh artifact
**CreateApp-debug-apk**. Bila gagal, salin log langkah yang merah.
