package fr.epistudio.epysia.editor.importer;

import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

public record GltfImportResult(List<Path> meshFiles, List<Path> clipFiles, List<Path> materialFiles,
                               List<Path> impostorFiles, Optional<Path> prefabFile, List<String> warnings) {
}
