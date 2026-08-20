package fr.epistudio.epysia.editor.scripteditor;

import java.util.Optional;

public record CompletionContext(String prefix, Optional<String> receiver, Optional<String> importPath) {

    public CompletionContext(String prefix, Optional<String> receiver) {
        this(prefix, receiver, Optional.empty());
    }

    public boolean isMember() {
        return receiver.isPresent();
    }

    public boolean isImport() {
        return importPath.isPresent();
    }
}
