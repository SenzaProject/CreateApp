#!/bin/sh
# Create App - pembantu build untuk Termux (versi 1).
#
# Dipasang dan dijalankan otomatis oleh aplikasi Create App lewat intent RUN_COMMAND.
# Ditulis untuk POSIX sh (dash/bash) dan hanya memakai perintah dasar Termux.
#
# Pemakaian:
#   sh build.sh check  --source DIR
#   sh build.sh build  --source DIR --name NAMA --task TASK --out DIR [--run TOKEN]
#   sh build.sh cancel
#   sh build.sh clean
#
# Keluaran yang dibaca aplikasi: baris berawalan "@@CA@" (lihat HelperProtocol.kt).
# Skrip ini tidak pernah mencetak variabel lingkungan, token, atau password.

CA_HELPER_VERSION=1
CA_HOME="${CREATEAPP_HOME:-${HOME:-/tmp}/.createapp}"
CA_PIDFILE="$CA_HOME/build.pid"
CA_LOGDIR="$CA_HOME/logs"
CA_WORKROOT="$CA_HOME/work"
CA_DEFAULT_GRADLE="8.13"

say() {
    printf '@@CA@%s\n' "$1"
}

# --- Penelusuran proses (hanya memakai /proc; bila gagal, hanya proses akar yang dimatikan) ---

children_of() {
    target=$1
    for st in /proc/[0-9]*/stat; do
        [ -r "$st" ] || continue
        p=${st#/proc/}
        p=${p%/stat}
        s=$(cat "$st" 2>/dev/null) || continue
        # Format: "pid (nama proses) S ppid ...". Nama bisa berisi spasi/kurung, jadi potong sampai ") " terakhir.
        s=${s##*) }
        set -- $s
        if [ "${2:-}" = "$target" ]; then
            printf '%s\n' "$p"
        fi
    done
}

tree_pids() {
    printf '%s\n' "$1"
    for c in $(children_of "$1"); do
        tree_pids "$c"
    done
}

kill_tree() {
    root=$1
    sig=$2
    for p in $(tree_pids "$root"); do
        kill "-$sig" "$p" 2>/dev/null
    done
}

is_alive() {
    [ -n "$1" ] && kill -0 "$1" 2>/dev/null
}

# --- Penguraian argumen ---

SOURCE=""
NAME=""
TASK=""
OUT=""
RUN=""

parse_args() {
    while [ $# -gt 0 ]; do
        if [ $# -lt 2 ]; then
            say "error=BAD_ARGS"
            exit 2
        fi
        case "$1" in
            --source) SOURCE=$2 ;;
            --name) NAME=$2 ;;
            --task) TASK=$2 ;;
            --out) OUT=$2 ;;
            --run) RUN=$2 ;;
            *)
                say "error=BAD_ARGS"
                exit 2
                ;;
        esac
        shift 2
    done
}

require_absolute() {
    case "$1" in
        /*) return 0 ;;
        *) return 1 ;;
    esac
}

# --- check ---

item() {
    say "check=$1|$2|$3"
}

cmd_check() {
    say "helper=$CA_HELPER_VERSION"
    arch=$(uname -m 2>/dev/null || echo unknown)
    item arch OK "$arch"

    if command -v java >/dev/null 2>&1; then
        jv=$(java -version 2>&1 | head -n 1 | tr '|' '/')
        item java OK "$jv"
    else
        item java MISSING "pkg install openjdk-17"
    fi

    have_wrapper=0
    if [ -n "$SOURCE" ] && [ -f "$SOURCE/gradlew" ] && [ -f "$SOURCE/gradle/wrapper/gradle-wrapper.jar" ]; then
        have_wrapper=1
    fi
    if [ "$have_wrapper" = 1 ]; then
        item wrapper OK "gradlew ada di proyek"
    elif command -v gradle >/dev/null 2>&1; then
        item wrapper OK "wrapper akan dibuat dari Gradle terpasang"
    else
        item wrapper MISSING "pkg install gradle (untuk membuat wrapper)"
    fi

    sdk=${ANDROID_HOME:-${ANDROID_SDK_ROOT:-}}
    if [ -n "$sdk" ] && [ -d "$sdk" ]; then
        item sdk OK "$sdk"
        if [ -d "$sdk/platforms" ] && [ -n "$(ls "$sdk/platforms" 2>/dev/null)" ]; then
            item platform OK "$(ls "$sdk/platforms" | tr '\n' ' ')"
        else
            item platform MISSING "belum ada SDK Platform di $sdk/platforms"
        fi
        if [ -d "$sdk/build-tools" ] && [ -n "$(ls "$sdk/build-tools" 2>/dev/null)" ]; then
            item buildtools OK "$(ls "$sdk/build-tools" | tr '\n' ' ')"
        else
            item buildtools MISSING "belum ada build-tools di $sdk/build-tools"
        fi
    else
        item sdk MISSING "ANDROID_HOME belum diatur atau folder SDK tidak ada"
    fi

    case "$arch" in
        x86_64 | amd64)
            item aapt2 OK "arsitektur $arch memakai aapt2 bawaan Gradle"
            ;;
        *)
            gp="${HOME:-/tmp}/.gradle/gradle.properties"
            if [ -f "$gp" ] && grep -q 'android.aapt2FromMavenOverride' "$gp" 2>/dev/null; then
                item aapt2 OK "android.aapt2FromMavenOverride diatur"
            else
                item aapt2 WARN "arsitektur $arch: aapt2 bawaan Gradle tidak jalan; atur android.aapt2FromMavenOverride"
            fi
            ;;
    esac

    mem_kb=$(sed -n 's/^MemTotal:[[:space:]]*\([0-9][0-9]*\) kB.*/\1/p' /proc/meminfo 2>/dev/null | head -n 1)
    if [ -n "$mem_kb" ]; then
        mem_mb=$((mem_kb / 1024))
        if [ "$mem_mb" -ge 3000 ]; then
            item memory OK "${mem_mb} MB RAM"
        else
            item memory WARN "${mem_mb} MB RAM: build bisa kehabisan memori; tutup aplikasi lain dan pakai build Debug"
        fi
    fi

    set -- $(df -Pk "${HOME:-/}" 2>/dev/null | tail -n 1)
    free_kb=${4:-}
    case "$free_kb" in
        '' | *[!0-9]*) ;;
        *)
            free_mb=$((free_kb / 1024))
            if [ "$free_mb" -ge 3000 ]; then
                item storage OK "${free_mb} MB ruang kosong"
            else
                item storage WARN "${free_mb} MB ruang kosong: Gradle butuh beberapa GB untuk cache dan hasil kompilasi"
            fi
            ;;
    esac

    if command -v termux-wake-lock >/dev/null 2>&1; then
        item wakelock OK "build menahan CPU tetap aktif (termux-wake-lock)"
    else
        item wakelock WARN "termux-wake-lock tidak ada; layar mati bisa menghentikan build (pkg install termux-tools)"
    fi

    if [ -n "$SOURCE" ]; then
        if [ -d "$SOURCE" ]; then
            item source OK "$SOURCE"
        else
            item source MISSING "folder tidak terbaca; jalankan termux-setup-storage lalu sinkronkan proyek"
        fi
    fi
    say "done=1"
    return 0
}

# --- cancel ---

cmd_cancel() {
    pid=""
    [ -f "$CA_PIDFILE" ] && pid=$(cat "$CA_PIDFILE" 2>/dev/null)
    if ! is_alive "$pid"; then
        rm -f "$CA_PIDFILE" 2>/dev/null
        say "cancel=none"
        return 0
    fi
    kill -TERM "$pid" 2>/dev/null
    i=0
    while is_alive "$pid" && [ "$i" -lt 8 ]; do
        sleep 1
        i=$((i + 1))
    done
    if is_alive "$pid"; then
        kill_tree "$pid" KILL
    fi
    rm -f "$CA_PIDFILE" 2>/dev/null
    say "cancel=killed"
    return 0
}

# --- clean ---

cmd_clean() {
    pid=""
    [ -f "$CA_PIDFILE" ] && pid=$(cat "$CA_PIDFILE" 2>/dev/null)
    if is_alive "$pid"; then
        say "error=ALREADY_RUNNING"
        return 3
    fi
    rm -rf "$CA_WORKROOT"
    say "clean=ok"
    return 0
}

# --- build ---

GPID=""
SPID=""
CANCELLED=0
WAKELOCK=0
LIVE=""

build_cleanup() {
    rm -f "$CA_PIDFILE"
    if [ -n "$SPID" ]; then
        kill "$SPID" 2>/dev/null
    fi
    if [ -n "$LIVE" ]; then
        rm -f "$LIVE" "$LIVE.new" 2>/dev/null
    fi
    if [ "$WAKELOCK" = 1 ]; then
        termux-wake-unlock >/dev/null 2>&1
    fi
}

# Menyalin log Gradle ke folder bersama (header + isi) agar aplikasi bisa menampilkannya langsung.
# Ditulis ke file sementara lalu dipindah supaya pembaca tidak pernah melihat file setengah jadi.
publish_live() {
    {
        printf '# createapp-run %s\n' "$RUN"
        tail -c 1048576 "$LOG" 2>/dev/null
    } >"$LIVE.new" 2>/dev/null && mv -f "$LIVE.new" "$LIVE" 2>/dev/null
    return 0
}

on_signal() {
    CANCELLED=1
    if [ -n "$GPID" ]; then
        kill_tree "$GPID" TERM
    fi
}

fail() {
    # $1 = kode error, $2 = kode keluar
    say "error=$1"
    say "done=1"
    exit "$2"
}

make_wrapper() {
    work=$1
    version=$CA_DEFAULT_GRADLE
    props="$work/gradle/wrapper/gradle-wrapper.properties"
    if [ -f "$props" ]; then
        found=$(sed -n 's/.*gradle-\([0-9][0-9.]*\)-\(bin\|all\)\.zip.*/\1/p' "$props" | head -n 1)
        [ -n "$found" ] && version=$found
    fi
    tmpw=$(mktemp -d 2>/dev/null) || return 1
    : >"$tmpw/settings.gradle"
    if ! (cd "$tmpw" && gradle wrapper --gradle-version "$version" --distribution-type bin) >>"$LOG" 2>&1; then
        rm -rf "$tmpw"
        return 1
    fi
    if [ ! -f "$tmpw/gradlew" ] || [ ! -f "$tmpw/gradle/wrapper/gradle-wrapper.jar" ]; then
        rm -rf "$tmpw"
        return 1
    fi
    mkdir -p "$work/gradle/wrapper"
    cp "$tmpw/gradlew" "$work/gradlew"
    [ -f "$tmpw/gradlew.bat" ] && cp "$tmpw/gradlew.bat" "$work/gradlew.bat"
    cp "$tmpw/gradle/wrapper/gradle-wrapper.jar" "$work/gradle/wrapper/gradle-wrapper.jar"
    if [ ! -f "$props" ] && [ -f "$tmpw/gradle/wrapper/gradle-wrapper.properties" ]; then
        cp "$tmpw/gradle/wrapper/gradle-wrapper.properties" "$props"
    fi
    rm -rf "$tmpw"
    return 0
}

cmd_build() {
    say "helper=$CA_HELPER_VERSION"
    say "phase=prepare"

    case "$NAME" in
        '' | *[!A-Za-z0-9_-]*) fail BAD_ARGS 2 ;;
    esac
    case "$TASK" in
        assembleDebug | assembleRelease | bundleRelease) ;;
        *) fail BAD_ARGS 2 ;;
    esac
    require_absolute "$SOURCE" || fail BAD_ARGS 2
    require_absolute "$OUT" || fail BAD_ARGS 2
    [ "$OUT" != "/" ] || fail BAD_ARGS 2
    case "$RUN" in
        *[!A-Za-z0-9_-]*) fail BAD_ARGS 2 ;;
    esac
    [ "${#RUN}" -le 64 ] || fail BAD_ARGS 2

    pid=""
    [ -f "$CA_PIDFILE" ] && pid=$(cat "$CA_PIDFILE" 2>/dev/null)
    if is_alive "$pid"; then
        fail ALREADY_RUNNING 3
    fi

    [ -d "$SOURCE" ] || fail NO_SOURCE 4
    if [ ! -f "$SOURCE/settings.gradle.kts" ] && [ ! -f "$SOURCE/settings.gradle" ]; then
        fail NOT_GRADLE_PROJECT 4
    fi
    command -v java >/dev/null 2>&1 || fail NO_JAVA 4

    mkdir -p "$CA_LOGDIR" "$CA_WORKROOT" || fail COPY_FAILED 6
    echo "$$" >"$CA_PIDFILE"
    trap 'build_cleanup' EXIT
    trap on_signal TERM INT HUP

    # Tahan CPU agar build panjang tidak terhenti saat layar mati (bila Termux menyediakannya).
    if command -v termux-wake-lock >/dev/null 2>&1; then
        termux-wake-lock >/dev/null 2>&1 && WAKELOCK=1
    fi

    stamp=$(date +%Y%m%d-%H%M%S 2>/dev/null || echo run)
    LOG="$CA_LOGDIR/$NAME-$stamp.log"
    : >"$LOG"
    # Simpan hanya 10 log terakhir per proyek.
    old=$(ls -1t "$CA_LOGDIR"/"$NAME"-*.log 2>/dev/null | sed -n '11,$p')
    [ -n "$old" ] && printf '%s\n' "$old" | while IFS= read -r f; do rm -f "$f"; done

    WORK="$CA_WORKROOT/$NAME"
    rm -rf "$WORK"
    mkdir -p "$WORK" || fail COPY_FAILED 6
    if ! cp -R "$SOURCE"/. "$WORK"/ 2>>"$LOG"; then
        fail COPY_FAILED 6
    fi

    sdk=${ANDROID_HOME:-${ANDROID_SDK_ROOT:-}}
    if [ ! -f "$WORK/local.properties" ] && [ -n "$sdk" ] && [ -d "$sdk" ]; then
        printf 'sdk.dir=%s\n' "$sdk" >"$WORK/local.properties"
    fi

    say "phase=wrapper"
    if [ ! -f "$WORK/gradlew" ] || [ ! -f "$WORK/gradle/wrapper/gradle-wrapper.jar" ]; then
        if ! command -v gradle >/dev/null 2>&1; then
            fail NO_WRAPPER 4
        fi
        if ! make_wrapper "$WORK"; then
            [ -s "$LOG" ] && cat "$LOG"
            fail WRAPPER_FAILED 4
        fi
    fi

    say "phase=build"
    say "log=$LOG"
    if [ -n "$RUN" ]; then
        LIVE="${OUT%/*}/live/$NAME.log"
        mkdir -p "${LIVE%/*}" 2>/dev/null
        printf '# createapp-run %s\n' "$RUN" >"$LIVE" 2>/dev/null || LIVE=""
    fi
    (cd "$WORK" && exec sh ./gradlew --console=plain --no-daemon "$TASK") >>"$LOG" 2>&1 &
    GPID=$!
    if [ -n "$LIVE" ]; then
        HELPER_PID=$$
        (
            while kill -0 "$HELPER_PID" 2>/dev/null; do
                sleep 2
                publish_live
            done
        ) >/dev/null 2>&1 &
        SPID=$!
    fi
    wait "$GPID"
    CODE=$?
    if [ -n "$SPID" ]; then
        kill "$SPID" 2>/dev/null
        SPID=""
    fi

    if [ "$CANCELLED" = 1 ]; then
        i=0
        while is_alive "$GPID" && [ "$i" -lt 5 ]; do
            sleep 1
            i=$((i + 1))
        done
        if is_alive "$GPID"; then
            kill_tree "$GPID" KILL
        fi
        cat "$LOG"
        say "cancelled=1"
        say "done=1"
        exit 143
    fi

    cat "$LOG"
    say "exit=$CODE"

    if [ "$CODE" -ne 0 ]; then
        say "done=1"
        exit "$CODE"
    fi

    say "phase=collect"
    case "$TASK" in
        assembleDebug) pattern='*/build/outputs/apk/debug/*.apk' ;;
        assembleRelease) pattern='*/build/outputs/apk/release/*.apk' ;;
        bundleRelease) pattern='*/build/outputs/bundle/release/*.aab' ;;
    esac

    list="$CA_HOME/artifacts.$$"
    find "$WORK" -type f -path "$pattern" >"$list" 2>/dev/null
    if [ ! -s "$list" ]; then
        rm -f "$list"
        fail NO_ARTIFACT 5
    fi

    dest="$OUT/$NAME"
    rm -rf "$dest"
    if ! mkdir -p "$dest"; then
        rm -f "$list"
        fail COPY_FAILED 6
    fi
    copied=0
    while IFS= read -r f; do
        base=${f##*/}
        if cp "$f" "$dest/$base"; then
            size=$(wc -c <"$dest/$base" | tr -d ' ')
            case "$base" in
                *.aab) kind=AAB ;;
                *) kind=APK ;;
            esac
            say "artifact=$kind|$size|$base"
            copied=$((copied + 1))
        fi
    done <"$list"
    rm -f "$list"

    if [ "$copied" -eq 0 ]; then
        fail COPY_FAILED 6
    fi
    say "done=1"
    exit 0
}

# --- titik masuk ---

CMD=${1:-}
if [ $# -gt 0 ]; then
    shift
fi

case "$CMD" in
    check)
        parse_args "$@"
        cmd_check
        exit 0
        ;;
    build)
        parse_args "$@"
        cmd_build
        ;;
    cancel)
        cmd_cancel
        exit 0
        ;;
    clean)
        cmd_clean
        exit $?
        ;;
    *)
        say "error=BAD_ARGS"
        exit 2
        ;;
esac
