package fr.epistudio.epysia.editor.importer;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

public final class AssetImporterRegistry {

    private final List<AssetImporter> importers = new ArrayList<>();

    public void register(AssetImporter importer) {
        importers.add(importer);
    }

    public Optional<AssetImporter> forPath(Path source) {
        String extension = extensionOf(source);
        if (extension.isEmpty()) {
            return Optional.empty();
        }
        for (AssetImporter importer : importers) {
            if (importer.supportedExtensions().contains(extension)) {
                return Optional.of(importer);
            }
        }
        return Optional.empty();
    }

    private static String extensionOf(Path source) {
        String fileName = source.getFileName().toString().toLowerCase(Locale.ROOT);
        int dot = fileName.lastIndexOf('.');
        return dot > 0 ? fileName.substring(dot) : "";
    }
}
