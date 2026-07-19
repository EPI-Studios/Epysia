#!/usr/bin/env bash
set -euo pipefail

VERSION="${VERSION:?VERSION is required}"
TARGET="${TARGET:?TARGET is required}"

LAUNCHER_NAME="EpysiaGame"
NATIVE_ACCESS="--enable-native-access=ALL-UNNAMED"
GAME_MODULES="java.base,java.desktop,java.logging,java.management,java.naming,java.net.http,java.xml,jdk.unsupported,jdk.zipfs"
EDITOR_MODULES="${GAME_MODULES},java.compiler,jdk.compiler"

JLINK="${JAVA_HOME}/bin/jlink"
JPACKAGE="${JAVA_HOME}/bin/jpackage"
DIST="$(pwd)/dist"
WORK="$(pwd)/build/package"

rm -rf "$DIST" "$WORK"
mkdir -p "$DIST" "$WORK"

jlink_runtime() {
    "$JLINK" --add-modules "$1" \
        --strip-debug --no-header-files --no-man-pages --compress=zip-6 --output "$2"
}

make_zip() {
    local archive="$1" parent="$2" name="$3"
    if command -v 7z >/dev/null 2>&1; then
        (cd "$parent" && 7z a -tzip -bd "$archive" "$name" >/dev/null)
    else
        (cd "$parent" && zip -qr "$archive" "$name")
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
        --dest "$WORK/template"
    make_zip "$DIST/epysia-template-${TARGET}-${VERSION}.zip" "$WORK/template" "$LAUNCHER_NAME"
}

package_editor_windows() {
    "$JPACKAGE" --type exe --name Epysia --app-version "$VERSION" \
        --input "$WORK/editor-input" --main-jar epysia-editor.jar \
        --main-class fr.epistudio.epysia.editor.EditorMain \
        --runtime-image "$WORK/editor-runtime" --java-options "$NATIVE_ACCESS" \
        --win-dir-chooser --win-menu --win-shortcut --dest "$WORK/editor"
    mv "$WORK"/editor/*.exe "$DIST/Epysia-${VERSION}-${TARGET}.exe"
}

package_editor_linux() {
    "$JPACKAGE" --type deb --name Epysia --app-version "$VERSION" \
        --input "$WORK/editor-input" --main-jar epysia-editor.jar \
        --main-class fr.epistudio.epysia.editor.EditorMain \
        --runtime-image "$WORK/editor-runtime" --java-options "$NATIVE_ACCESS" \
        --linux-shortcut --dest "$WORK/editor"
    mv "$WORK"/editor/*.deb "$DIST/Epysia-${VERSION}-${TARGET}.deb"
    "$JPACKAGE" --type app-image --name Epysia --app-version "$VERSION" \
        --input "$WORK/editor-input" --main-jar epysia-editor.jar \
        --main-class fr.epistudio.epysia.editor.EditorMain \
        --runtime-image "$WORK/editor-runtime" --java-options "$NATIVE_ACCESS" \
        --dest "$WORK/editor-portable"
    (cd "$WORK/editor-portable" && tar -czf "$DIST/Epysia-${VERSION}-${TARGET}.tar.gz" Epysia)
}

package_editor() {
    mkdir -p "$WORK/editor-input"
    cp epysia-editor/build/libs/epysia-editor.jar "$WORK/editor-input/"
    jlink_runtime "$EDITOR_MODULES" "$WORK/editor-runtime"
    if [ "$TARGET" = "windows-x64" ]; then
        package_editor_windows
    else
        package_editor_linux
    fi
}

package_template
package_editor
ls -lh "$DIST"
