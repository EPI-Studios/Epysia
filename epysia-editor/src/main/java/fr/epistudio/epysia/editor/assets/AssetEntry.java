package fr.epistudio.epysia.editor.assets;

import java.nio.file.Path;

public record AssetEntry(String displayName, String assetPath, AssetType type,
                         long byteSize, long modifiedMillis) {

    private static final long BYTES_PER_UNIT = 1024L;
    private static final String[] UNITS = {"B", "KB", "MB", "GB"};

    public static AssetEntry builtin(String presetPath, String label) {
        return new AssetEntry(label, presetPath, AssetType.PRESET, 0L, 0L);
    }

    public static AssetEntry folder(Path directory, long modifiedMillis) {
        return new AssetEntry(directory.getFileName().toString(),
                directory.toAbsolutePath().toString(), AssetType.FOLDER, 0L, modifiedMillis);
    }

    public boolean isBuiltin() {
        return type == AssetType.PRESET;
    }

    public boolean isFolder() {
        return type == AssetType.FOLDER;
    }

    public Path path() {
        return Path.of(assetPath);
    }

    public String formattedSize() {
        if (isBuiltin() || isFolder()) {
            return "";
        }
        double size = byteSize;
        int unit = 0;
        while (size >= BYTES_PER_UNIT && unit < UNITS.length - 1) {
            size /= BYTES_PER_UNIT;
            unit++;
        }
        return unit == 0 ? (long) size + " " + UNITS[0] : String.format("%.1f %s", size, UNITS[unit]);
    }
}
