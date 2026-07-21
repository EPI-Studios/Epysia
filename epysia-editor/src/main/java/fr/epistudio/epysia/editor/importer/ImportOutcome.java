package fr.epistudio.epysia.editor.importer;

import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

public record ImportOutcome(List<Path> outputs, Optional<Path> instantiable, List<String> warnings) {

    public static ImportOutcome empty() {
        return new ImportOutcome(List.of(), Optional.empty(), List.of());
    }
}
