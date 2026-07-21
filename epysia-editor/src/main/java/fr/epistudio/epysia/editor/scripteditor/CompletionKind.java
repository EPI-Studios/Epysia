package fr.epistudio.epysia.editor.scripteditor;

public enum CompletionKind {
    KEYWORD("K"),
    TYPE("T"),
    METHOD("M"),
    PACKAGE("P"),
    FIELD("F"),
    LOCAL("L");

    private final String tag;

    CompletionKind(String tag) {
        this.tag = tag;
    }

    public String tag() {
        return tag;
    }
}
