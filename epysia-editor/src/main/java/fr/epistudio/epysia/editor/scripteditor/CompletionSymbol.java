package fr.epistudio.epysia.editor.scripteditor;

public record CompletionSymbol(String label, String insertText, CompletionKind kind) {
}
