package fr.epistudio.epysia.assets;

public final class AssetPaths {

    private AssetPaths() {
    }

    public static String fileNameOf(String reference) {
        String trimmed = reference.strip();
        int lastSlash = Math.max(trimmed.lastIndexOf('/'), trimmed.lastIndexOf('\\'));
        return lastSlash < 0 ? trimmed : trimmed.substring(lastSlash + 1);
    }

    public static String stemOf(String reference) {
        String fileName = fileNameOf(reference);
        int lastDot = fileName.lastIndexOf('.');
        return lastDot <= 0 ? fileName : fileName.substring(0, lastDot);
    }

    public static String extensionOf(String reference) {
        String fileName = fileNameOf(reference);
        int lastDot = fileName.lastIndexOf('.');
        return lastDot < 0 ? "" : fileName.substring(lastDot);
    }
}
