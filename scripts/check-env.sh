#!/usr/bin/env sh
# Diagnostik lingkungan build lokal (Termux / Linux). Hanya membaca, tidak mengubah apa pun.
ok()   { printf '  [OK]      %s\n' "$1"; }
miss() { printf '  [KURANG]  %s\n' "$1"; MISSING=1; }
MISSING=0

echo "Create App - diagnostik lingkungan build"

if command -v java >/dev/null 2>&1; then
  ok "Java: $(java -version 2>&1 | head -n 1)"
else
  miss "Java belum terpasang (Termux: pkg install openjdk-17)"
fi

if command -v gradle >/dev/null 2>&1; then
  ok "Gradle terpasang (hanya dibutuhkan untuk membuat wrapper)"
else
  miss "Gradle belum terpasang (Termux: pkg install gradle)"
fi

if [ -x ./gradlew ] && [ -f gradle/wrapper/gradle-wrapper.jar ]; then
  ok "Gradle Wrapper ada"
else
  miss "Gradle Wrapper belum ada (jalankan: sh scripts/setup-wrapper.sh)"
fi

SDK="${ANDROID_HOME:-${ANDROID_SDK_ROOT:-}}"
if [ -n "$SDK" ] && [ -d "$SDK" ]; then
  ok "Android SDK: $SDK"
  [ -d "$SDK/platforms/android-35" ] && ok "SDK Platform 35" || miss "SDK Platform 35 belum ada"
  ls "$SDK/build-tools" >/dev/null 2>&1 && ok "Build-tools: $(ls "$SDK/build-tools" | tr '\n' ' ')" || miss "Build-tools belum ada"
else
  miss "ANDROID_HOME belum diatur / SDK belum ada"
fi

command -v git >/dev/null 2>&1 && ok "Git: $(git --version)" || miss "Git belum terpasang"

echo
if [ "$MISSING" -eq 0 ]; then
  echo "Lingkungan siap untuk build lokal."
else
  echo "Ada komponen yang kurang. Alternatif: build lewat GitHub Actions (tanpa SDK di HP)."
fi
exit 0
