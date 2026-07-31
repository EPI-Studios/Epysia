package fr.epistudio.epysia.editor.scripteditor;

import imgui.extension.texteditor.flag.TextEditorPaletteIndex;

import java.util.LinkedHashMap;
import java.util.Map;

final class SourceTokenRegexes {

    private static final String STRING_TOKEN = "\\\"(\\\\.|[^\\\"])*\\\"";
    private static final String CHARACTER_TOKEN = "\\'\\\\?[^\\']\\'";
    private static final String HEX_NUMBER_TOKEN = "0[xX][0-9a-fA-F_]+[lL]?";
    private static final String NUMBER_TOKEN =
            "[+-]?([0-9][0-9_]*([.][0-9_]*)?|[.][0-9][0-9_]*)([eE][+-]?[0-9]+)?[fFdDlL]?";
    private static final String IDENTIFIER_TOKEN = "[a-zA-Z_][a-zA-Z0-9_]*";
    private static final String PUNCTUATION_TOKEN =
            "[\\[\\]\\{\\}\\!\\%\\^\\&\\*\\(\\)\\-\\+\\=\\~\\|\\<\\>\\?\\/\\;\\,\\.\\:\\@]";

    private SourceTokenRegexes() {
    }

    static Map<String, Integer> curlyBraceFamily() {
        Map<String, Integer> tokens = new LinkedHashMap<>();
        tokens.put(STRING_TOKEN, TextEditorPaletteIndex.String);
        tokens.put(CHARACTER_TOKEN, TextEditorPaletteIndex.CharLiteral);
        tokens.put(HEX_NUMBER_TOKEN, TextEditorPaletteIndex.Number);
        tokens.put(NUMBER_TOKEN, TextEditorPaletteIndex.Number);
        tokens.put(IDENTIFIER_TOKEN, TextEditorPaletteIndex.Identifier);
        tokens.put(PUNCTUATION_TOKEN, TextEditorPaletteIndex.Punctuation);
        return tokens;
    }

    static Map<String, String> knownIdentifiers(JavaSymbols symbols) {
        Map<String, String> identifiers = new LinkedHashMap<>();
        for (String typeName : symbols.typeNames()) {
            identifiers.put(typeName, "Type " + typeName);
        }
        return identifiers;
    }
}
