#!/bin/sh
# Menyinkronkan isi sebuah zip tahap baru ke repo lokal, lalu commit dan push ke GitHub.
#
# Pemakaian (jalankan DI DALAM folder repo CreateApp hasil clone):
#   sh scripts/termux-update.sh /path/ke/CreateApp-TahapN.zip "Tahap N: ringkasan"
#
# Yang dilakukan:
#   1. Memeriksa bahwa ini benar repo CreateApp dan tidak ada perubahan lokal yang belum di-commit.
#   2. git pull --ff-only (agar sinkron dengan GitHub).
#   3. Mengosongkan isi repo (kecuali .git) lalu menyalin isi zip, sehingga file yang dihapus di
#      tahap baru ikut terhapus. Folder .git di dalam zip (bila ada) diabaikan.
#   4. git add -A, commit, push.
#
# Seluruh skrip dibungkus { ... } agar shell membacanya utuh sebelum berjalan; skrip ini sendiri
# ikut tergantikan oleh isi zip saat penyalinan.
{
    set -eu

    ZIP="${1:-}"
    MESSAGE="${2:-Update Create App}"

    fail() {
        echo "GAGAL: $1" >&2
        exit 1
    }

    [ -n "$ZIP" ] || fail "Pemakaian: sh scripts/termux-update.sh /path/ke/file.zip \"pesan commit\""
    [ -f "$ZIP" ] || fail "Berkas zip tidak ditemukan: $ZIP (bila di Download, jalankan termux-setup-storage dulu)"
    command -v unzip >/dev/null 2>&1 || fail "unzip belum terpasang. Jalankan: pkg install unzip"
    command -v git >/dev/null 2>&1 || fail "git belum terpasang. Jalankan: pkg install git"

    # --- Pengaman: pastikan kita berada di repo CreateApp, bukan folder lain ---
    [ -d .git ] || fail "Folder ini bukan akar repo git. Masuk dulu ke folder CreateApp (cd ~/CreateApp)."
    [ -f settings.gradle.kts ] || fail "settings.gradle.kts tidak ada di folder ini; ini bukan repo CreateApp."
    REMOTE="$(git remote get-url origin 2>/dev/null || true)"
    case "$REMOTE" in
        *[Cc]reate[Aa]pp*) ;;
        *) fail "Remote origin ($REMOTE) tidak tampak seperti repo CreateApp. Batal demi keamanan." ;;
    esac

    if ! git diff --quiet || ! git diff --cached --quiet; then
        fail "Ada perubahan lokal yang belum di-commit. Commit atau batalkan dulu (git status)."
    fi

    BRANCH="$(git rev-parse --abbrev-ref HEAD)"
    [ "$BRANCH" != "HEAD" ] || fail "Repo dalam keadaan detached HEAD. Pindah ke branch (git checkout main)."

    echo "==> Sinkron dengan GitHub (git pull --ff-only)"
    git pull --ff-only origin "$BRANCH" || fail "git pull gagal. Selesaikan dulu (mis. git pull --rebase), lalu ulangi."

    # --- Ekstrak ke folder sementara dan validasi sebelum menghapus apa pun ---
    TMP="$(mktemp -d)"
    cleanup() { rm -rf "$TMP"; }
    trap cleanup EXIT INT TERM

    echo "==> Mengekstrak $ZIP"
    unzip -q "$ZIP" -d "$TMP" || fail "Gagal mengekstrak zip."

    SRC="$TMP"
    ENTRIES="$(ls -A "$TMP" | wc -l | tr -d ' ')"
    if [ "$ENTRIES" = "1" ]; then
        ONLY="$(ls -A "$TMP")"
        if [ -d "$TMP/$ONLY" ]; then SRC="$TMP/$ONLY"; fi
    fi
    [ -f "$SRC/settings.gradle.kts" ] || fail "Isi zip tidak tampak seperti proyek CreateApp (settings.gradle.kts tidak ada). Tidak ada yang diubah."
    [ -d "$SRC/app" ] || fail "Isi zip tidak punya folder app/. Tidak ada yang diubah."
    rm -rf "$SRC/.git"

    echo "==> Mengganti isi repo (kecuali .git)"
    find . -mindepth 1 -maxdepth 1 ! -name .git -exec rm -rf {} +
    cp -a "$SRC"/. .

    git add -A
    if git diff --cached --quiet; then
        echo "Tidak ada perubahan dibanding versi di GitHub. Tidak ada yang di-push."
        exit 0
    fi

    echo "==> Ringkasan perubahan"
    git status --short | head -n 40
    CHANGED="$(git status --short | wc -l | tr -d ' ')"
    echo "(${CHANGED} berkas berubah)"

    git commit -q -m "$MESSAGE"
    echo "==> Push ke origin/$BRANCH"
    git push origin "$BRANCH" || fail "Push gagal. Bila ditolak (non-fast-forward): git pull --rebase origin $BRANCH lalu git push origin $BRANCH."

    echo "Selesai. Buka tab Actions di GitHub untuk melihat hasil build."
    exit 0
}
