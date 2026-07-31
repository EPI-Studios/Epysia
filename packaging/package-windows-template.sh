#!/usr/bin/env bash
set -euo pipefail

VERSION="${VERSION:-1.0-SNAPSHOT}"
TARGET="windows-x64"

LAUNCHER_NAME="EpysiaGame"
MAIN_CLASS="fr.epistudio.epysia.GameLauncher"
ENGINE_JAR="epysia-engine.jar"
NATIVE_ACCESS="--enable-native-access=ALL-UNNAMED"
GAME_MODULES="java.base,java.desktop,java.logging,java.management,java.naming,java.net.http,java.xml,jdk.unsupported,jdk.zipfs"
WINDOWS_JDK_URL="${WINDOWS_JDK_URL:-https://cdn.azul.com/zulu/bin/zulu25.36.15-ca-jdk25.0.4-win_x64.zip}"

JAVA_HOME="${JAVA_HOME:?JAVA_HOME is required and must point at a JDK 25 installation}"
JLINK="${JAVA_HOME}/bin/jlink"
JMOD="${JAVA_HOME}/bin/jmod"
DOWNLOADS="${EPYSIA_DOWNLOAD_CACHE:-${HOME}/.cache/epysia-packaging}"
DIST="$(pwd)/dist"
WORK="$(pwd)/build/package-windows"
TEMPLATE="$WORK/template"
INSTALLED="${TEMPLATE_CACHE:-${HOME}/.epysia/templates}/${TARGET}/${VERSION}"

rm -rf "$WORK"
mkdir -p "$DIST" "$WORK" "$DOWNLOADS"

fetch_windows_jdk() {
    local archive="$DOWNLOADS/$(basename "$WINDOWS_JDK_URL")"
    local unpacked="$DOWNLOADS/windows-jdk"
    if [ ! -f "$archive" ]; then
        curl -fSL -o "$archive" "$WINDOWS_JDK_URL"
    fi
    if [ ! -d "$unpacked" ]; then
        mkdir -p "$unpacked"
        unzip -q "$archive" -d "$unpacked"
    fi
    WINDOWS_JDK="$(find "$unpacked" -maxdepth 2 -type d -name jmods -print -quit)"
    WINDOWS_JDK="$(dirname "${WINDOWS_JDK:?the downloaded Windows JDK has no jmods directory}")"
}

build_engine_jar() {
    ./gradlew engineRuntimeJar -PreleaseVersion="$VERSION" -PlwjglNatives=natives-windows
}

link_windows_runtime() {
    "$JLINK" --module-path "$WINDOWS_JDK/jmods" --add-modules "$GAME_MODULES" \
        --strip-debug --no-header-files --no-man-pages --compress=zip-6 --output "$TEMPLATE/runtime"
}

extract_launchers() {
    local stubs="$WORK/jpackage-stubs"
    "$JMOD" extract --dir "$stubs" "$WINDOWS_JDK/jmods/jdk.jpackage.jmod"
    local resources="$stubs/classes/jdk/jpackage/internal/resources"
    cp "$resources/jpackageapplauncherw.exe" "$TEMPLATE/${LAUNCHER_NAME}.exe"
    cp "$resources/jpackageapplauncher.exe" "$TEMPLATE/${LAUNCHER_NAME}-console.exe"
}

write_launcher_config() {
    cat > "$TEMPLATE/app/$1.cfg" <<CONFIG
[Application]
app.classpath=\$APPDIR/${ENGINE_JAR}
app.mainclass=${MAIN_CLASS}

[JavaOptions]
java-options=-Djpackage.app-version=${VERSION}
java-options=${NATIVE_ACCESS}
CONFIG
}

windows_jdk_version() {
    tr -d '\r' < "$WINDOWS_JDK/release" | sed -n 's/^JAVA_VERSION="\(.*\)"$/\1/p'
}

write_application_directory() {
    mkdir -p "$TEMPLATE/app"
    cp "build/libs/${ENGINE_JAR}" "$TEMPLATE/app/"
    write_launcher_config "$LAUNCHER_NAME"
    write_launcher_config "${LAUNCHER_NAME}-console"
    cat > "$TEMPLATE/app/.jpackage.xml" <<STATE
<?xml version="1.0" ?>
<jpackage-state version="$(windows_jdk_version)" platform="windows">
  <app-version>${VERSION}</app-version>
  <main-launcher>${LAUNCHER_NAME}</main-launcher>
  <main-class>${MAIN_CLASS}</main-class>
  <add-launcher name="${LAUNCHER_NAME}-console" service="false"></add-launcher></jpackage-state>
STATE
}

require_windows_binary() {
    if ! file -b "$1" | grep -q '^PE32+ executable'; then
        echo "not a Windows binary: $1 ($(file -b "$1"))" >&2
        exit 1
    fi
}

verify_template() {
    require_windows_binary "$TEMPLATE/${LAUNCHER_NAME}.exe"
    require_windows_binary "$TEMPLATE/${LAUNCHER_NAME}-console.exe"
    require_windows_binary "$TEMPLATE/runtime/bin/java.exe"
    require_windows_binary "$TEMPLATE/runtime/bin/server/jvm.dll"
    if unzip -l "$TEMPLATE/app/${ENGINE_JAR}" | grep -q 'linux/x64/org/lwjgl'; then
        echo "the engine jar carries Linux LWJGL natives" >&2
        exit 1
    fi
}

install_template() {
    rm -rf "$INSTALLED"
    mkdir -p "$INSTALLED"
    cp -a "$TEMPLATE/." "$INSTALLED/"
}

archive_template() {
    local archive="$DIST/epysia-template-${TARGET}-${VERSION}.zip"
    rm -f "$archive"
    (cd "$TEMPLATE" && zip -qr "$archive" .)
}

fetch_windows_jdk
build_engine_jar
link_windows_runtime
extract_launchers
write_application_directory
verify_template
archive_template
install_template
echo "template installed in $INSTALLED"
ls -lh "$DIST/epysia-template-${TARGET}-${VERSION}.zip"
