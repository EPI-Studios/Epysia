package fr.epistudio.epysia.editor.export;

import java.nio.file.Path;
import java.util.Optional;

public record ExportOutcome(Optional<Path> destination, Optional<String> failure) {

    public static ExportOutcome exported(Path destination) {
        return new ExportOutcome(Optional.of(destination), Optional.empty());
    }

    public static ExportOutcome failed(String message) {
        return new ExportOutcome(Optional.empty(), Optional.of(message));
    }
}
