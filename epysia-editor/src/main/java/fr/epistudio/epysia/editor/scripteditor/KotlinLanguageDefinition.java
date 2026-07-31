package fr.epistudio.epysia.editor.scripteditor;

import imgui.extension.texteditor.TextEditorLanguageDefinition;

import java.util.List;

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

    private KotlinLanguageDefinition() {
    }

    public static TextEditorLanguageDefinition create(JavaSymbols symbols) {
        TextEditorLanguageDefinition definition = new TextEditorLanguageDefinition();
        definition.setName("Kotlin");
        definition.setKeywords(KEYWORDS.toArray(String[]::new));
        definition.setIdentifiers(SourceTokenRegexes.knownIdentifiers(symbols));
        definition.setCommentStart("/*");
        definition.setCommentEnd("*/");
        definition.setSingleLineComment("//");
        definition.setAutoIndentation(true);
        definition.setmCaseSensitive(true);
        definition.setTokenRegexStrings(SourceTokenRegexes.curlyBraceFamily());
        return definition;
    }

    public static List<String> keywords() {
        return KEYWORDS;
    }
}
