#!/usr/bin/env bash
# ==============================================================================
# OnlineFix Linux Launcher (OFLL) — сборка и запуск
#
# Что делает скрипт:
#   1. проверяет Java (нужен JDK 17+, лучше 21) и утилиты;
#   2. при первом запуске скачивает портативный JavaFX SDK в папку проекта
#      (системный JavaFX не нужен и не используется);
#   3. компилирует LauncherApp.java в папку build/ (только если исходник изменился);
#   4. запускает лаунчер.
#
# Использование:
#   ./start.sh             — обычный запуск
#   ./start.sh --rebuild   — принудительно пересобрать
#   ./start.sh --help      — эта справка
#
# Необязательные переменные окружения:
#   OFLL_GDK_BACKEND   — GDK_BACKEND для окна лаунчера (по умолчанию x11)
#   OFLL_PRISM_ORDER   — порядок рендереров JavaFX (по умолчанию es2,sw;
#                        если окно пустое или падает — попробуйте sw)
#   JFX_URL            — свой адрес архива JavaFX SDK
# ==============================================================================

set -u

# Работаем из папки скрипта, чтобы запуск из любого места вёл себя одинаково.
cd "$(dirname "$(readlink -f "${BASH_SOURCE[0]}")")" || exit 1

JFX_VER="21.0.3"
JFX_DIR="$PWD/javafx-sdk-$JFX_VER"
JFX_PATH="$JFX_DIR/lib"
JFX_MODULES="javafx.controls,javafx.graphics"
SRC="LauncherApp.java"
BUILD_DIR="build"
STAMP="$BUILD_DIR/.built-with-$JFX_VER"

REBUILD=0
for arg in "$@"; do
    case "$arg" in
        --rebuild) REBUILD=1 ;;
        -h|--help)
            awk 'NR > 2 && /^# ====/ { exit } NR > 2 { sub(/^# ?/, ""); print }' "${BASH_SOURCE[0]}"
            exit 0
            ;;
    esac
done

die() { echo "[ОШИБКА] $*" >&2; exit 1; }

# ------------------------------------------------------------------ 1. Java --
command -v java  >/dev/null 2>&1 || die "Java не найдена. Установите JDK 21:
    Debian/Ubuntu:  sudo apt install openjdk-21-jdk
    Fedora:         sudo dnf install java-21-openjdk-devel
    Arch/Manjaro:   sudo pacman -S jdk21-openjdk"
command -v javac >/dev/null 2>&1 || die "Найдена только среда выполнения Java, а нужен полный JDK (javac).
    Debian/Ubuntu:  sudo apt install openjdk-21-jdk
    Fedora:         sudo dnf install java-21-openjdk-devel
    Arch/Manjaro:   sudo pacman -S jdk21-openjdk"

JAVA_MAJOR="$(java -version 2>&1 | head -n 1 | sed -E 's/^[^"]*"([0-9]+).*/\1/')"
case "$JAVA_MAJOR" in
    ''|*[!0-9]*) JAVA_MAJOR=0 ;;
esac
if [ "$JAVA_MAJOR" -gt 0 ] && [ "$JAVA_MAJOR" -lt 17 ]; then
    die "Установлена Java $JAVA_MAJOR, а нужна 17 или новее (рекомендуется 21)."
fi

# --------------------------------------------------------------- 2. JavaFX ---
if [ ! -d "$JFX_PATH" ]; then
    case "$(uname -m)" in
        x86_64|amd64)  JFX_ARCH="x64" ;;
        aarch64|arm64) JFX_ARCH="aarch64" ;;
        *) die "Архитектура $(uname -m) не поддерживается портативным JavaFX SDK." ;;
    esac
    JFX_ZIP="openjfx-${JFX_VER}_linux-${JFX_ARCH}_bin-sdk.zip"
    JFX_URL="${JFX_URL:-https://download2.gluonhq.com/openjfx/${JFX_VER}/${JFX_ZIP}}"

    echo "======================================================="
    echo "📦 Скачиваю портативный JavaFX SDK $JFX_VER (~40 МБ)..."
    echo "   Он ставится только в папку проекта и не трогает систему."
    echo "======================================================="

    TMP_ZIP="$JFX_ZIP.part"
    rm -f "$TMP_ZIP"
    if command -v wget >/dev/null 2>&1; then
        wget --show-progress -q -O "$TMP_ZIP" "$JFX_URL"
    elif command -v curl >/dev/null 2>&1; then
        curl -fL --progress-bar -o "$TMP_ZIP" "$JFX_URL"
    else
        die "Нужен wget или curl для загрузки JavaFX (sudo apt install wget)."
    fi
    if [ $? -ne 0 ] || [ ! -s "$TMP_ZIP" ]; then
        rm -f "$TMP_ZIP"
        die "Не удалось скачать JavaFX. Проверьте интернет или скачайте архив вручную:
    $JFX_URL
и положите javafx-sdk-$JFX_VER в папку проекта."
    fi

    echo "⚙️  Распаковка архива..."
    TMP_DIR="$(mktemp -d "$PWD/.jfx-unpack.XXXXXX")" || die "Не удалось создать временную папку."
    if command -v unzip >/dev/null 2>&1; then
        unzip -q "$TMP_ZIP" -d "$TMP_DIR"
    elif command -v python3 >/dev/null 2>&1; then
        python3 -m zipfile -e "$TMP_ZIP" "$TMP_DIR"
    else
        rm -rf "$TMP_DIR" "$TMP_ZIP"
        die "Нужен unzip (sudo apt install unzip)."
    fi
    if [ $? -ne 0 ] || [ ! -d "$TMP_DIR/javafx-sdk-$JFX_VER/lib" ]; then
        rm -rf "$TMP_DIR" "$TMP_ZIP"
        die "Архив JavaFX повреждён или имеет неожиданную структуру. Запустите скрипт ещё раз."
    fi
    mv "$TMP_DIR/javafx-sdk-$JFX_VER" "$JFX_DIR" || die "Не удалось переместить JavaFX в $JFX_DIR."
    rm -rf "$TMP_DIR" "$TMP_ZIP"
    echo "✅ JavaFX установлен: $JFX_DIR"
fi

# -------------------------------------------------------------- 3. Сборка ----
[ -f "$SRC" ] || die "Не найден $SRC рядом со start.sh."

if [ "$REBUILD" -eq 1 ] || [ ! -f "$STAMP" ] || [ "$SRC" -nt "$STAMP" ]; then
    echo "⚙️  Компиляция $SRC..."
    rm -rf "$BUILD_DIR"
    mkdir -p "$BUILD_DIR"
    if ! javac -encoding UTF-8 -Xlint:-options \
            --module-path "$JFX_PATH" --add-modules "$JFX_MODULES" \
            -d "$BUILD_DIR" "$SRC"; then
        rm -rf "$BUILD_DIR"
        die "Компиляция не удалась. Если вы правили $SRC — проверьте сообщения выше."
    fi
    touch "$STAMP"
fi

# -------------------------------------------------------------- 4. Запуск ----
echo "🚀 Запуск OnlineFix Linux Launcher..."

# Окну лаунчера нужен X11/XWayland (так стабильнее всего). Исходное значение
# запоминаем — лаунчер вернёт его играм и Winetricks, чтобы не ломать им графику.
export OFLL_GDK_BACKEND_ORIG="${GDK_BACKEND-}"
export GDK_BACKEND="${OFLL_GDK_BACKEND:-x11}"
export _JAVA_AWT_WM_NONREPARENTING=1

ARGS=()
for arg in "$@"; do
    [ "$arg" = "--rebuild" ] || ARGS+=("$arg")
done

exec java \
    -Dfile.encoding=UTF-8 \
    -Dprism.order="${OFLL_PRISM_ORDER:-es2,sw}" \
    --enable-native-access=ALL-UNNAMED,javafx.graphics,javafx.controls \
    --module-path "$JFX_PATH" \
    --add-modules "$JFX_MODULES" \
    -cp "$BUILD_DIR" \
    LauncherApp "${ARGS[@]+"${ARGS[@]}"}"