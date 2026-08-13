#!/usr/bin/env bash
set -euo pipefail

VERSION="${VERSION:?VERSION is required}"
TARGET="${TARGET:?TARGET is required}"

LAUNCHER_NAME="EpysiaGame"
SERVER_NAME="EpysiaServer"
NATIVE_ACCESS="--enable-native-access=ALL-UNNAMED"
EDITOR_JAVA_OPTIONS="$NATIVE_ACCESS -Dimgui.library.path=\$APPDIR"
WINDOWS_ICON="$(pwd)/packaging/epysia.ico"
LINUX_ICON="$(pwd)/src/main/resources/branding/epysia-icon-256.png"
GAME_MODULES="java.base,java.logging,java.management,java.naming,java.net.http,java.xml,jdk.unsupported,jdk.zipfs"
SERVER_MODULES="java.base,java.logging,java.management,java.naming,java.net.http,java.xml,jdk.unsupported,jdk.zipfs"
EDITOR_MODULES="${GAME_MODULES},java.compiler,jdk.compiler"

JLINK="${JAVA_HOME}/bin/jlink"
JPACKAGE="${JAVA_HOME}/bin/jpackage"
DIST="$(pwd)/dist"
CONSOLE_LAUNCHER="$(pwd)/build/package/console-launcher.properties"
WORK="$(pwd)/build/package"

rm -rf "$DIST" "$WORK"
mkdir -p "$DIST" "$WORK"

write_console_launcher_properties() {
    cat > "$CONSOLE_LAUNCHER" <<'PROPERTIES'
win-console=true
PROPERTIES
}
write_console_launcher_properties

jlink_runtime() {
    "$JLINK" --add-modules "$1" \
        --strip-debug --no-header-files --no-man-pages --compress=zip-6 --output "$2"
}

make_zip() {
    local archive="$1" directory="$2"
    if command -v 7z >/dev/null 2>&1; then
        (cd "$directory" && 7z a -tzip -bd "$archive" . >/dev/null)
    else
        (cd "$directory" && zip -qr "$archive" .)
    fi
}

package_template() {
    local input="$WORK/template-input"
    mkdir -p "$input"
    cp build/libs/epysia-engine.jar "$input/"
    jlink_runtime "$GAME_MODULES" "$WORK/template-runtime"
    "$JPACKAGE" --type app-image --name "$LAUNCHER_NAME" --app-version "$VERSION" \
        --input "$input" --main-jar epysia-engine.jar \
        --main-class fr.epistudio.epysia.GameLauncher \
        --runtime-image "$WORK/template-runtime" --java-options "$NATIVE_ACCESS" \
        --add-launcher "${LAUNCHER_NAME}-console=$CONSOLE_LAUNCHER" \
        --dest "$WORK/template"
    make_zip "$DIST/epysia-template-${TARGET}-${VERSION}.zip" "$WORK/template/$LAUNCHER_NAME"
}

package_editor_windows() {
    "$JPACKAGE" --type exe --name Epysia --app-version "$VERSION" \
        --input "$WORK/editor-input" --main-jar epysia-editor.jar \
        --main-class fr.epistudio.epysia.editor.EditorMain \
        --runtime-image "$WORK/editor-runtime" --java-options "$EDITOR_JAVA_OPTIONS" \
        --icon "$WINDOWS_ICON" \
        --add-launcher "Epysia-console=$CONSOLE_LAUNCHER" \
        --win-dir-chooser --win-menu --win-shortcut --dest "$WORK/editor"
    mv "$WORK"/editor/*.exe "$DIST/Epysia-${VERSION}-${TARGET}.exe"
}

package_editor_linux_bundle() {
    local kind="$1" extension="$2"
    if ! "$JPACKAGE" --type "$kind" --name Epysia --app-version "$VERSION" \
            --input "$WORK/editor-input" --main-jar epysia-editor.jar \
            --main-class fr.epistudio.epysia.editor.EditorMain \
            --runtime-image "$WORK/editor-runtime" --java-options "$EDITOR_JAVA_OPTIONS" \
            --icon "$LINUX_ICON" \
            --linux-shortcut --dest "$WORK/editor-$kind"; then
        echo "warning: could not build the $kind package, the tooling for it is probably missing" >&2
        return 0
    fi
    mv "$WORK/editor-$kind"/*."$extension" "$DIST/Epysia-${VERSION}-${TARGET}.${extension}"
}

package_editor_linux() {
    package_editor_linux_bundle deb deb
    package_editor_linux_bundle rpm rpm
    "$JPACKAGE" --type app-image --name Epysia --app-version "$VERSION" \
        --input "$WORK/editor-input" --main-jar epysia-editor.jar \
        --main-class fr.epistudio.epysia.editor.EditorMain \
        --runtime-image "$WORK/editor-runtime" --java-options "$EDITOR_JAVA_OPTIONS" \
        --icon "$LINUX_ICON" \
        --dest "$WORK/editor-portable"
    (cd "$WORK/editor-portable" && tar -czf "$DIST/Epysia-${VERSION}-${TARGET}.tar.gz" Epysia)
}

package_server() {
    local input="$WORK/server-input"
    mkdir -p "$input"
    cp build/libs/epysia-engine.jar "$input/"
    jlink_runtime "$SERVER_MODULES" "$WORK/server-runtime"
    "$JPACKAGE" --type app-image --name "$SERVER_NAME" --app-version "$VERSION" \
        --input "$input" --main-jar epysia-engine.jar \
        --main-class fr.epistudio.epysia.GameLauncher \
        --runtime-image "$WORK/server-runtime" --java-options "$NATIVE_ACCESS" \
        --arguments "--server" \
        --dest "$WORK/server"
    strip_graphics_natives "$WORK/server/$SERVER_NAME"
    make_zip "$DIST/epysia-server-${TARGET}-${VERSION}.zip" "$WORK/server/$SERVER_NAME"
}

strip_graphics_natives() {
    local root="$1"
    find "$root" -type f \( \
        -name '*glfw*' -o -name '*opengl*' -o -name '*openal*' -o -name '*opus*' -o \
        -name '*tinyexr*' -o -name '*shaderc*' -o -name '*spvc*' -o -name '*vulkan*' \
    \) -delete 2>/dev/null || true
}

copy_imgui_native() {
    local destination="$1" native
    case "$TARGET" in
        windows-x64) native=imgui-java64.dll ;;
        macos-*) native=libimgui-java64.dylib ;;
        *) native=libimgui-java64.so ;;
    esac
    local source
    for candidate in "imgui-java/bin/$native" "../imgui-java/bin/$native" \
            "${IMGUI_JAVA_HOME:-/nonexistent}/bin/$native"; do
        if [ -f "$candidate" ]; then
            source="$candidate"
            break
        fi
    done
    if [ -z "${source:-}" ]; then
        echo "error: $native not found, the packaged editor would fail to start" >&2
        exit 1
    fi
    cp "$source" "$destination/"
    echo "bundled $source"
}

package_editor() {
    mkdir -p "$WORK/editor-input"
    cp epysia-editor/build/libs/epysia-editor.jar "$WORK/editor-input/"
    copy_imgui_native "$WORK/editor-input"
    jlink_runtime "$EDITOR_MODULES" "$WORK/editor-runtime"
    if [ "$TARGET" = "windows-x64" ]; then
        package_editor_windows
    else
        package_editor_linux
    fi
}

package_template
package_server
package_editor
ls -lh "$DIST"
