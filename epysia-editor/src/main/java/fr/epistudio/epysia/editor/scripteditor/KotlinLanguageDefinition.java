package fr.epistudio.epysia.editor.scripteditor;

import imgui.extension.texteditor.TextEditorLanguage;

import java.util.List;
import java.util.Set;

public final class KotlinLanguageDefinition {

    private static final List<String> KEYWORDS = List.of(
            "as", "break", "class", "continue", "do", "else", "false", "for", "fun", "if", "in",
            "interface", "is", "null", "object", "package", "return", "super", "this", "throw",
            "true", "try", "typealias", "typeof", "val", "var", "when", "while",
            "by", "catch", "constructor", "delegate", "dynamic", "field", "file", "finally", "get",
            "import", "init", "param", "property", "receiver", "set", "setparam", "value", "where",
            "abstract", "actual", "annotation", "companion", "const", "crossinline", "data", "enum",
            "expect", "external", "final", "infix", "inline", "inner", "internal", "lateinit",
            "noinline", "open", "operator", "out", "override", "private", "protected", "public",
            "reified", "sealed", "suspend", "tailrec", "vararg");

    private static final Set<String> DECLARATIONS = Set.of(
            "abstract", "actual", "annotation", "class", "companion", "const", "crossinline",
            "data", "enum", "expect", "external", "final", "fun", "infix", "init", "inline",
            "inner", "interface", "internal", "lateinit", "noinline", "object", "open", "operator",
            "out", "override", "private", "protected", "public", "reified", "sealed", "suspend",
            "tailrec", "typealias", "val", "var", "vararg");

    private KotlinLanguageDefinition() {
    }

    public static TextEditorLanguage create(JavaSymbols symbols) {
        return CurlyBraceLanguage.create("Kotlin",
                CurlyBraceLanguage.without(KEYWORDS, DECLARATIONS), DECLARATIONS, symbols);
    }

    public static List<String> keywords() {
        return KEYWORDS;
    }
}
