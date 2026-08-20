package fr.epistudio.epysia.editor.langpack;

public record LanguagePack(String identifier, String name, String description, String version,
                           String archiveName, String downloadUrl, long sizeBytes, String checksum,
                           String runtimeArchiveName, String runtimeUrl, String runtimeChecksum) {

    public long kilobytes() {
        return Math.max(1L, sizeBytes / 1024L);
    }

    public boolean hasRuntimeArchive() {
        return !runtimeArchiveName.isBlank();
    }
}
