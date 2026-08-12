package fr.epistudio.epysia.editor.scripteditor;

import imgui.extension.texteditor.TextEditorLanguage;

import java.util.Set;

public final class JavaLanguageDefinition {

    private static final Set<String> DECLARATIONS = Set.of(
            "abstract", "boolean", "byte", "char", "class", "double", "enum", "extends", "final",
            "float", "implements", "int", "interface", "long", "native", "permits", "private",
            "protected", "public", "record", "sealed", "short", "static", "strictfp",
            "synchronized", "transient", "var", "void", "volatile");

    private JavaLanguageDefinition() {
    }

    public static TextEditorLanguage create(JavaSymbols symbols) {
        return CurlyBraceLanguage.create("Java",
                CurlyBraceLanguage.without(JavaSymbols.keywords(), DECLARATIONS),
                DECLARATIONS, symbols);
    }
}
