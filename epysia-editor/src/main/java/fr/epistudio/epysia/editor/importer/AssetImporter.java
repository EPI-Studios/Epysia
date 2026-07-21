package fr.epistudio.epysia.editor.importer;

import java.nio.file.Path;
import java.util.Set;

public interface AssetImporter {

    String displayName();

    Set<String> supportedExtensions();

    Path primaryOutput(Path source, Path outputDirectory);

    ImportOutcome importSource(Path source, Path outputDirectory);
}
