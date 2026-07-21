package fr.epistudio.epysia.editor.scripteditor;

import java.util.Optional;

public record CompletionSymbol(String label, String insertText, CompletionKind kind,
                               Optional<String> qualifiedName) {

    public CompletionSymbol(String label, String insertText, CompletionKind kind) {
        this(label, insertText, kind, Optional.empty());
    }

    public Optional<String> packageName() {
        return qualifiedName.flatMap(CompletionSymbol::packageOf);
    }

    private static Optional<String> packageOf(String qualifiedName) {
        int lastDotIndex = qualifiedName.lastIndexOf('.');
        return lastDotIndex < 0
                ? Optional.empty()
                : Optional.of(qualifiedName.substring(0, lastDotIndex));
    }
}
