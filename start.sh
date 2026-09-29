#!/usr/bin/env bash
# ==============================================================================
# OnlineFix Linux Launcher (OFLL) — сборка и запуск
#
# Что делает скрипт:
#   1. при первом запуске скачивает портативный JDK 21 (Temurin) в папку проекта
#      (системная Java не нужна и не используется);
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
#   JDK_URL            — свой адрес архива JDK (tar.gz)
#   OFLL_SYSTEM_JAVA=1 — не скачивать JDK, а взять системный (нужен JDK 17+)
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
JDK_DIR="$PWD/jdk-local"

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
# Скачивает файл: download URL ФАЙЛ (wget или curl). Возвращает 0 при успехе.
download() {
    local url="$1" out="$2"
    rm -f "$out"
    if command -v curl >/dev/null 2>&1; then
        curl -fL --progress-bar -o "$out" "$url" || return 1
    elif command -v wget >/dev/null 2>&1; then
        wget --show-progress -q -O "$out" "$url" || return 1
    else
        die "Нужен wget или curl для загрузки файлов (sudo apt install curl)."
    fi
    [ -s "$out" ]
}

if [ "${OFLL_SYSTEM_JAVA:-0}" = "1" ]; then
    # Явно запрошена системная Java.
    command -v java  >/dev/null 2>&1 || die "Java не найдена (OFLL_SYSTEM_JAVA=1). Уберите переменную — JDK скачается сам."
    command -v javac >/dev/null 2>&1 || die "Нужен полный JDK (javac), а найдена только среда выполнения."
    JDK_TAG="system"
else
    if [ ! -x "$JDK_DIR/bin/javac" ] || [ ! -x "$JDK_DIR/bin/java" ]; then
        case "$(uname -m)" in
            x86_64|amd64)  JDK_ARCH="x64" ;;
            aarch64|arm64) JDK_ARCH="aarch64" ;;
            *) die "Архитектура $(uname -m) не поддерживается: не могу скачать портативный JDK.
    Поставьте JDK 21 вручную и запустите: OFLL_SYSTEM_JAVA=1 ./start.sh" ;;
        esac
        JDK_URL="${JDK_URL:-https://api.adoptium.net/v3/binary/latest/21/ga/linux/${JDK_ARCH}/jdk/hotspot/normal/eclipse}"

        echo "======================================================="
        echo "☕ Скачиваю портативный JDK 21 (~200 МБ)..."
        echo "   Он ставится только в папку проекта и не трогает систему."
        echo "======================================================="

        JDK_TGZ="$PWD/jdk-download.tar.gz.part"
        if ! download "$JDK_URL" "$JDK_TGZ"; then
            rm -f "$JDK_TGZ"
            die "Не удалось скачать JDK. Проверьте интернет или укажите свой архив:
    JDK_URL=<адрес tar.gz> ./start.sh
или поставьте JDK 21 вручную и запустите: OFLL_SYSTEM_JAVA=1 ./start.sh"
        fi

        echo "⚙️  Распаковка JDK..."
        command -v tar >/dev/null 2>&1 || { rm -f "$JDK_TGZ"; die "Нужен tar (sudo apt install tar)."; }
        JDK_TMP="$(mktemp -d "$PWD/.jdk-unpack.XXXXXX")" || { rm -f "$JDK_TGZ"; die "Не удалось создать временную папку."; }
        if ! tar -xzf "$JDK_TGZ" -C "$JDK_TMP"; then
            rm -rf "$JDK_TMP" "$JDK_TGZ"
            die "Архив JDK повреждён. Запустите скрипт ещё раз."
        fi
        JDK_INNER="$(find "$JDK_TMP" -mindepth 1 -maxdepth 1 -type d | head -n 1)"
        if [ -z "$JDK_INNER" ] || [ ! -x "$JDK_INNER/bin/javac" ]; then
            rm -rf "$JDK_TMP" "$JDK_TGZ"
            die "В архиве JDK нет bin/javac — неожиданная структура архива."
        fi
        rm -rf "$JDK_DIR"
        mv "$JDK_INNER" "$JDK_DIR" || { rm -rf "$JDK_TMP" "$JDK_TGZ"; die "Не удалось переместить JDK в $JDK_DIR."; }
        rm -rf "$JDK_TMP" "$JDK_TGZ"
        echo "✅ JDK установлен: $JDK_DIR"
    fi
    export JAVA_HOME="$JDK_DIR"
    export PATH="$JDK_DIR/bin:$PATH"
    JDK_TAG="local"
fi

JAVA_MAJOR="$(java -version 2>&1 | head -n 1 | sed -E 's/^[^"]*"([0-9]+).*/\1/')"
case "$JAVA_MAJOR" in
    ''|*[!0-9]*) JAVA_MAJOR=0 ;;
esac
if [ "$JAVA_MAJOR" -gt 0 ] && [ "$JAVA_MAJOR" -lt 17 ]; then
    die "Установлена Java $JAVA_MAJOR, а нужна 17 или новее (рекомендуется 21)."
fi

# Отметка сборки: если сменилась Java или JavaFX — исходник пересоберётся.
STAMP="$BUILD_DIR/.built-with-$JFX_VER-$JDK_TAG$JAVA_MAJOR"

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
    if ! download "$JFX_URL" "$TMP_ZIP"; then
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
