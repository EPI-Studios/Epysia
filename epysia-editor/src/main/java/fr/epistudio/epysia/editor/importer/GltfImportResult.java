package fr.epistudio.epysia.editor.importer;

import java.nio.file.Path;
import java.util.List;

public record GltfImportResult(List<Path> meshFiles, List<Path> clipFiles, List<String> warnings) {
}
