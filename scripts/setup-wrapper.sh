#!/usr/bin/env sh
# Membuat Gradle Wrapper (gradlew, gradlew.bat, gradle/wrapper/gradle-wrapper.jar)
# memakai Gradle yang terpasang. Jalankan sekali, lalu commit ketiga file tersebut.
#
# Termux : pkg install gradle
# Catatan: file gradle/wrapper/gradle-wrapper.properties milik repository TIDAK ditimpa,
#          sehingga versi Gradle proyek tetap 8.13.
set -eu

GRADLE_VERSION="8.13"
ROOT="$(cd "$(dirname "$0")/.." && pwd)"

if ! command -v gradle >/dev/null 2>&1; then
  echo "Gradle belum terpasang. Di Termux jalankan: pkg install gradle" >&2
  exit 1
fi

# Dijalankan di folder sementara agar tidak bergantung pada kompatibilitas
# versi Gradle yang terpasang dengan plugin proyek.
TMP="$(mktemp -d)"
trap 'rm -rf "$TMP"' EXIT
touch "$TMP/settings.gradle"
( cd "$TMP" && gradle wrapper --gradle-version "$GRADLE_VERSION" --distribution-type bin )

mkdir -p "$ROOT/gradle/wrapper"
cp "$TMP/gradlew" "$TMP/gradlew.bat" "$ROOT/"
cp "$TMP/gradle/wrapper/gradle-wrapper.jar" "$ROOT/gradle/wrapper/"
chmod +x "$ROOT/gradlew"

echo "Wrapper siap. Commit: gradlew, gradlew.bat, gradle/wrapper/gradle-wrapper.jar"
echo "Uji dengan: ./gradlew --version"
