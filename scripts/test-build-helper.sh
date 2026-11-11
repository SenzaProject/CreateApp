#!/bin/sh
# Menguji app/src/main/assets/createapp-build.sh dengan proses NYATA (shell, find, cp, kill, /proc),
# memakai gradlew palsu yang meniru keluaran Gradle. Tidak butuh Java, Gradle, atau Android SDK.
#
# Pemakaian (dari akar repo):
#   sh scripts/test-build-helper.sh            # memakai "sh"
#   bash scripts/test-build-helper.sh          # menguji di bash juga
# Opsional: HELPER=/path/createapp-build.sh SH_UNDER_TEST=dash sh scripts/test-build-helper.sh

ROOT=$(cd "$(dirname "$0")/.." && pwd)
HELPER=${HELPER:-$ROOT/app/src/main/assets/createapp-build.sh}
SH_UNDER_TEST=${SH_UNDER_TEST:-sh}

PASS=0
FAIL=0
TMPROOT=$(mktemp -d)
trap 'rm -rf "$TMPROOT"' EXIT

# Java palsu: uji tidak boleh bergantung pada ada/tidaknya JDK asli di mesin ini.
FAKEJAVA="$TMPROOT/fakejava"
mkdir -p "$FAKEJAVA"
printf '#!/bin/sh\necho "openjdk version \\"17.0.0\\" (palsu)" >&2\n' >"$FAKEJAVA/java"
chmod +x "$FAKEJAVA/java"
PATH="$FAKEJAVA:$PATH"
export PATH

# PATH minimal berisi hanya perkakas dasar (tanpa java dan tanpa gradle).
make_minbin() {
    mb=$1
    mkdir -p "$mb"
    for tool in sh dash bash cat sed head tr ls rm mkdir cp find wc date mktemp sleep grep uname dirname tail echo printf; do
        p=$(command -v "$tool" 2>/dev/null)
        case "$p" in /*) ln -sf "$p" "$mb/$tool" ;; esac
    done
}

ok() {
    PASS=$((PASS + 1))
    printf '  ok   %s\n' "$1"
}

bad() {
    FAIL=$((FAIL + 1))
    printf '  GAGAL %s\n' "$1"
}

check() {
    # $1 = deskripsi, $2.. = perintah uji
    desc=$1
    shift
    if "$@"; then ok "$desc"; else bad "$desc"; fi
}

has_line() { printf '%s\n' "$OUTPUT" | grep -q -F -x -- "$1"; }
has_text() { printf '%s\n' "$OUTPUT" | grep -q -F -- "$1"; }
no_text() { ! printf '%s\n' "$OUTPUT" | grep -q -F -- "$1"; }
rc_is() { [ "$RC" -eq "$1" ]; }
last_line_is() { [ "$(printf '%s\n' "$OUTPUT" | tail -n 1)" = "$1" ]; }
exists() { [ -e "$1" ]; }
missing() { [ ! -e "$1" ]; }

new_env() {
    # Lingkungan terisolasi per uji.
    CASE=$(mktemp -d "$TMPROOT/case.XXXXXX")
    export HOME="$CASE/home"
    export CREATEAPP_HOME="$CASE/home/.createapp"
    mkdir -p "$HOME"
    OUTDIR="$CASE/shared/out"
    SRC="$CASE/shared/src/proj"
    mkdir -p "$OUTDIR" "$SRC"
}

# Membuat proyek palsu lengkap dengan gradlew yang berperilaku sesuai FAKE_MODE.
make_project() {
    dir=$1
    mkdir -p "$dir/app/src" "$dir/gradle/wrapper"
    : >"$dir/settings.gradle.kts"
    echo 'class Main' >"$dir/app/src/Main.kt"
    : >"$dir/gradle/wrapper/gradle-wrapper.jar"
    cat >"$dir/gradlew" <<'EOF'
#!/bin/sh
for last; do :; done
echo "> Task :app:preBuild UP-TO-DATE"
case "$FAKE_MODE" in
  fail)
    echo "> Task :app:compileDebugKotlin FAILED"
    echo "e: file:///tmp/x/Main.kt:3:5 Unresolved reference 'foo'"
    echo "FAILURE: Build failed with an exception."
    echo "BUILD FAILED in 2s"
    exit 1 ;;
  noart)
    echo "BUILD SUCCESSFUL in 1s"
    exit 0 ;;
  hang)
    echo "> Task :app:compileDebugKotlin"
    sh -c 'sleep 3171' &
    wait
    exit 0 ;;
esac
case "$last" in
  assembleDebug)
    mkdir -p app/build/outputs/apk/debug
    printf 'DEBUGAPK' >app/build/outputs/apk/debug/app-debug.apk ;;
  assembleRelease)
    mkdir -p app/build/outputs/apk/debug app/build/outputs/apk/release
    printf 'DEBUGAPK' >app/build/outputs/apk/debug/app-debug.apk
    printf 'RELEASEAPK' >app/build/outputs/apk/release/app-release-unsigned.apk ;;
  bundleRelease)
    mkdir -p app/build/outputs/bundle/release
    printf 'BUNDLEDATA' >app/build/outputs/bundle/release/app-release.aab ;;
esac
echo "BUILD SUCCESSFUL in 3s"
exit 0
EOF
}

run_helper() {
    OUTPUT=$("$SH_UNDER_TEST" "$HELPER" "$@" 2>&1)
    RC=$?
}

run_build() {
    run_helper build --source "$SRC" --name proj --task "$1" --out "$OUTDIR"
}

echo "Menguji $HELPER dengan: $SH_UNDER_TEST"

echo "[1] build sukses assembleDebug"
new_env
make_project "$SRC"
FAKE_MODE=ok run_build assembleDebug
check "kode keluar 0" rc_is 0
check "penanda helper" has_line "@@CA@helper=1"
check "fase prepare" has_line "@@CA@phase=prepare"
check "fase wrapper" has_line "@@CA@phase=wrapper"
check "fase build" has_line "@@CA@phase=build"
check "fase collect" has_line "@@CA@phase=collect"
check "log gradle ikut tercetak" has_text "> Task :app:preBuild UP-TO-DATE"
check "exit=0 tercetak" has_line "@@CA@exit=0"
check "artefak dilaporkan dengan ukuran" has_line "@@CA@artifact=APK|8|app-debug.apk"
check "selesai (done=1) tercetak paling akhir" last_line_is "@@CA@done=1"
check "APK disalin ke folder keluaran" exists "$OUTDIR/proj/app-debug.apk"
check "isi APK tersalin utuh" sh -c "[ \"\$(cat '$OUTDIR/proj/app-debug.apk')\" = DEBUGAPK ]"
check "berkas pid dibersihkan" missing "$CREATEAPP_HOME/build.pid"
check "log tersimpan di folder log helper" sh -c "ls '$CREATEAPP_HOME'/logs/proj-*.log >/dev/null 2>&1"

echo "[2] build gagal"
new_env
make_project "$SRC"
FAKE_MODE=fail run_build assembleDebug
check "kode keluar 1 diteruskan dari gradle" rc_is 1
check "exit=1 tercetak" has_line "@@CA@exit=1"
check "baris error Kotlin ada di keluaran" has_text "e: file:///tmp/x/Main.kt:3:5 Unresolved reference 'foo'"
check "tidak ada artefak dilaporkan" no_text "@@CA@artifact="
check "done=1 tetap tercetak" has_line "@@CA@done=1"
check "folder keluaran tidak dibuat" missing "$OUTDIR/proj"

echo "[3] gradle sukses tetapi tanpa artefak"
new_env
make_project "$SRC"
FAKE_MODE=noart run_build assembleDebug
check "kode keluar 5" rc_is 5
check "error NO_ARTIFACT" has_line "@@CA@error=NO_ARTIFACT"

echo "[4] bundleRelease menghasilkan AAB"
new_env
make_project "$SRC"
FAKE_MODE=ok run_build bundleRelease
check "kode keluar 0" rc_is 0
check "artefak AAB" has_line "@@CA@artifact=AAB|10|app-release.aab"

echo "[5] assembleRelease hanya mengambil APK release"
new_env
make_project "$SRC"
FAKE_MODE=ok run_build assembleRelease
check "kode keluar 0" rc_is 0
check "APK release dilaporkan" has_line "@@CA@artifact=APK|10|app-release-unsigned.apk"
check "APK debug TIDAK ikut" no_text "app-debug.apk"
check "APK debug tidak disalin" missing "$OUTDIR/proj/app-debug.apk"

echo "[6] argumen tidak valid ditolak"
new_env
make_project "$SRC"
run_helper build --source "$SRC" --name proj --task 'assembleDebug; touch /tmp/pwned' --out "$OUTDIR"
check "task di luar daftar izin ditolak" has_line "@@CA@error=BAD_ARGS"
check "kode keluar 2" rc_is 2
check "tidak ada eksekusi tersisip" missing /tmp/pwned
run_helper build --source "$SRC" --name '../escape' --task assembleDebug --out "$OUTDIR"
check "nama dengan .. ditolak" has_line "@@CA@error=BAD_ARGS"
run_helper build --source "$SRC" --name 'a b' --task assembleDebug --out "$OUTDIR"
check "nama dengan spasi ditolak" has_line "@@CA@error=BAD_ARGS"
run_helper build --source "relatif/path" --name proj --task assembleDebug --out "$OUTDIR"
check "path sumber relatif ditolak" has_line "@@CA@error=BAD_ARGS"
run_helper build --source "$SRC" --name proj --task assembleDebug --out /
check "folder keluaran / ditolak" has_line "@@CA@error=BAD_ARGS"
run_helper build --source "$SRC" --name proj --task
check "argumen tanpa nilai ditolak" has_line "@@CA@error=BAD_ARGS"
run_helper bogus
check "perintah tidak dikenal ditolak" has_line "@@CA@error=BAD_ARGS"

echo "[7] sumber tidak valid"
new_env
run_helper build --source "$CASE/shared/src/tidak-ada" --name proj --task assembleDebug --out "$OUTDIR"
check "folder sumber tidak ada -> NO_SOURCE" has_line "@@CA@error=NO_SOURCE"
check "kode keluar 4" rc_is 4
run_build assembleDebug
check "bukan proyek Gradle -> NOT_GRADLE_PROJECT" has_line "@@CA@error=NOT_GRADLE_PROJECT"

echo "[8] Java tidak ada"
new_env
make_project "$SRC"
MINBIN="$CASE/minbin"
make_minbin "$MINBIN"
OUTPUT=$(PATH="$MINBIN" FAKE_MODE=ok "$SH_UNDER_TEST" "$HELPER" build --source "$SRC" --name proj --task assembleDebug --out "$OUTDIR" 2>&1)
RC=$?
check "tanpa java -> NO_JAVA" has_line "@@CA@error=NO_JAVA"
check "kode keluar 4" rc_is 4

echo "[9] wrapper tidak ada dan Gradle tidak terpasang"
new_env
make_project "$SRC"
rm -f "$SRC/gradlew" "$SRC/gradle/wrapper/gradle-wrapper.jar"
MINBIN="$CASE/minbin"
make_minbin "$MINBIN"
ln -sf "$FAKEJAVA/java" "$MINBIN/java"
OUTPUT=$(PATH="$MINBIN" FAKE_MODE=ok "$SH_UNDER_TEST" "$HELPER" build --source "$SRC" --name proj --task assembleDebug --out "$OUTDIR" 2>&1)
RC=$?
check "tanpa gradlew dan tanpa gradle -> NO_WRAPPER" has_line "@@CA@error=NO_WRAPPER"
check "kode keluar 4" rc_is 4

echo "[10] wrapper dibuat dari gradle terpasang"
new_env
make_project "$SRC"
rm -f "$SRC/gradlew" "$SRC/gradle/wrapper/gradle-wrapper.jar"
printf 'distributionUrl=https\\://services.gradle.org/distributions/gradle-8.11.1-bin.zip\n' >"$SRC/gradle/wrapper/gradle-wrapper.properties"
FAKEBIN="$CASE/fakebin"
mkdir -p "$FAKEBIN"
cat >"$FAKEBIN/gradle" <<EOF
#!/bin/sh
echo "\$@" >"$CASE/gradle-args.txt"
mkdir -p gradle/wrapper
cat >gradlew <<'INNER'
#!/bin/sh
for last; do :; done
mkdir -p app/build/outputs/apk/debug
printf 'GENAPK' >app/build/outputs/apk/debug/app-debug.apk
echo "BUILD SUCCESSFUL in 1s"
INNER
: >gradlew.bat
: >gradle/wrapper/gradle-wrapper.jar
echo "distributionUrl=GENERATED" >gradle/wrapper/gradle-wrapper.properties
EOF
chmod +x "$FAKEBIN/gradle"
OUTPUT=$(PATH="$FAKEBIN:$PATH" "$SH_UNDER_TEST" "$HELPER" build --source "$SRC" --name proj --task assembleDebug --out "$OUTDIR" 2>&1)
RC=$?
check "build sukses dengan wrapper hasil buatan" rc_is 0
check "versi Gradle diambil dari gradle-wrapper.properties proyek" sh -c "grep -q -- '--gradle-version 8.11.1' '$CASE/gradle-args.txt'"
check "jenis distribusi bin" sh -c "grep -q -- '--distribution-type bin' '$CASE/gradle-args.txt'"
check "properties milik proyek tidak ditimpa" sh -c "grep -q 'gradle-8.11.1-bin.zip' '$CREATEAPP_HOME/work/proj/gradle/wrapper/gradle-wrapper.properties'"
check "artefak hasil wrapper buatan" has_line "@@CA@artifact=APK|6|app-debug.apk"
check "sumber asli tidak tersentuh (tetap tanpa gradlew)" missing "$SRC/gradlew"

echo "[11] pembuatan wrapper gagal"
new_env
make_project "$SRC"
rm -f "$SRC/gradlew" "$SRC/gradle/wrapper/gradle-wrapper.jar"
FAKEBIN="$CASE/fakebin"
mkdir -p "$FAKEBIN"
printf '#!/bin/sh\necho "gradle rusak" >&2\nexit 1\n' >"$FAKEBIN/gradle"
chmod +x "$FAKEBIN/gradle"
OUTPUT=$(PATH="$FAKEBIN:$PATH" "$SH_UNDER_TEST" "$HELPER" build --source "$SRC" --name proj --task assembleDebug --out "$OUTDIR" 2>&1)
RC=$?
check "WRAPPER_FAILED" has_line "@@CA@error=WRAPPER_FAILED"
check "kode keluar 4" rc_is 4
check "pesan gradle ikut tercetak untuk diagnosis" has_text "gradle rusak"

echo "[12] local.properties dibuat dari ANDROID_HOME"
new_env
make_project "$SRC"
mkdir -p "$CASE/sdk"
OUTPUT=$(ANDROID_HOME="$CASE/sdk" FAKE_MODE=ok "$SH_UNDER_TEST" "$HELPER" build --source "$SRC" --name proj --task assembleDebug --out "$OUTDIR" 2>&1)
RC=$?
check "local.properties berisi sdk.dir" sh -c "grep -q 'sdk.dir=$CASE/sdk' '$CREATEAPP_HOME/work/proj/local.properties'"
check "sumber asli tidak diubah" missing "$SRC/local.properties"

echo "[13] path dengan spasi dan tanda kutip"
new_env
SRC="$CASE/shared/my proj's \"dir\""
OUTDIR="$CASE/shared/out dir's"
mkdir -p "$SRC" "$OUTDIR"
make_project "$SRC"
FAKE_MODE=ok run_build assembleDebug
check "kode keluar 0" rc_is 0
check "APK tersalin ke folder dengan nama aneh" exists "$OUTDIR/proj/app-debug.apk"

echo "[14] build kedua menggantikan hasil lama"
new_env
make_project "$SRC"
FAKE_MODE=ok run_build assembleDebug
printf 'STALE' >"$OUTDIR/proj/stale.apk"
FAKE_MODE=ok run_build assembleDebug
check "sukses lagi" rc_is 0
check "hasil lama dibersihkan" missing "$OUTDIR/proj/stale.apk"

echo "[15] hanya 10 log terakhir disimpan"
new_env
make_project "$SRC"
mkdir -p "$CREATEAPP_HOME/logs"
i=1
while [ "$i" -le 14 ]; do
    : >"$CREATEAPP_HOME/logs/proj-2020010$(printf '%02d' "$i")-000000.log"
    i=$((i + 1))
done
FAKE_MODE=ok run_build assembleDebug
count=$(ls "$CREATEAPP_HOME"/logs/proj-*.log | wc -l | tr -d ' ')
check "jumlah log <= 11 (10 lama + 1 baru)" sh -c "[ $count -le 11 ]"

echo "[16] check"
new_env
make_project "$SRC"
run_helper check --source "$SRC"
check "kode keluar 0" rc_is 0
check "penanda arsitektur" has_text "@@CA@check=arch|OK|"
check "wrapper di proyek terdeteksi" has_line "@@CA@check=wrapper|OK|gradlew ada di proyek"
check "sumber terdeteksi" has_text "@@CA@check=source|OK|"
check "selesai" has_line "@@CA@done=1"
run_helper check --source "$CASE/tidak-ada"
check "sumber hilang dilaporkan MISSING" has_text "@@CA@check=source|MISSING|"

echo "[17] pembatalan: seluruh pohon proses dimatikan"
new_env
make_project "$SRC"
( FAKE_MODE=hang "$SH_UNDER_TEST" "$HELPER" build --source "$SRC" --name proj --task assembleDebug --out "$OUTDIR" >"$CASE/build.out" 2>&1; echo $? >"$CASE/build.rc" ) &
BGPID=$!
tries=0
while [ ! -s "$CREATEAPP_HOME/build.pid" ] && [ "$tries" -lt 30 ]; do sleep 1; tries=$((tries + 1)); done
tries=0
while ! grep -q 'phase=build' "$CASE/build.out" 2>/dev/null && [ "$tries" -lt 30 ]; do sleep 1; tries=$((tries + 1)); done
sleep 2
run_build assembleDebug
check "build kedua saat build berjalan ditolak" has_line "@@CA@error=ALREADY_RUNNING"
check "kode keluar 3" rc_is 3
sleep_alive() {
    for d in /proc/[0-9]*; do
        if [ -r "$d/cmdline" ] && tr '\0' ' ' <"$d/cmdline" 2>/dev/null | grep -q 'sleep 3171'; then
            return 0
        fi
    done
    return 1
}
check "proses anak (sleep) hidup sebelum dibatalkan" sleep_alive
run_helper cancel
check "cancel melaporkan killed" has_line "@@CA@cancel=killed"
wait "$BGPID"
check "build yang dibatalkan keluar 143" sh -c "[ \"\$(cat '$CASE/build.rc')\" = 143 ]"
check "build melaporkan cancelled=1" sh -c "grep -q -x '@@CA@cancelled=1' '$CASE/build.out'"
check "build yang dibatalkan tidak melaporkan artefak" sh -c "! grep -q '@@CA@artifact=' '$CASE/build.out'"
not_sleep_alive() { ! sleep_alive; }
check "proses anak (sleep) ikut mati" not_sleep_alive
check "berkas pid dibersihkan" missing "$CREATEAPP_HOME/build.pid"
run_helper cancel
check "cancel tanpa build berjalan -> none" has_line "@@CA@cancel=none"

echo "[18] clean"
new_env
make_project "$SRC"
FAKE_MODE=ok run_build assembleDebug
check "folder kerja ada sebelum clean" exists "$CREATEAPP_HOME/work/proj"
run_helper clean
check "clean ok" has_line "@@CA@clean=ok"
check "folder kerja terhapus" missing "$CREATEAPP_HOME/work"

echo
echo "Hasil: $PASS lulus, $FAIL gagal"
[ "$FAIL" -eq 0 ]
