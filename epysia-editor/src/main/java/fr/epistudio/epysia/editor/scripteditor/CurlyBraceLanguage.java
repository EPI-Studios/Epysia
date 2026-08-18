package fr.epistudio.epysia.editor.scripteditor;

import imgui.extension.texteditor.TextEditorLanguage;

import java.util.Collection;
import java.util.Set;

final class CurlyBraceLanguage {

    private static final String TEXT_BLOCK_DELIMITER = "\"\"\"";

    private CurlyBraceLanguage() {
    }

    static TextEditorLanguage create(String name, Collection<String> keywords,
                                     Set<String> declarations, JavaSymbols symbols) {
        return create(name, keywords, declarations, symbols, "//");
    }

    static TextEditorLanguage create(String name, Collection<String> keywords,
                                     Set<String> declarations, JavaSymbols symbols,
                                     String lineComment) {
        TextEditorLanguage language = TextEditorLanguage.copyOf(TextEditorLanguage.Cpp());
        language.setName(name);
        language.setCaseSensitive(true);
        language.setPreprocess(0);
        language.setSingleLineComment(lineComment);
        language.setCommentStart("/*");
        language.setCommentEnd("*/");
        language.setHasSingleQuotedStrings(true);
        language.setHasDoubleQuotedStrings(true);
        language.setOtherStringStart(TEXT_BLOCK_DELIMITER);
        language.setOtherStringEnd(TEXT_BLOCK_DELIMITER);
        language.setStringEscape('\\');
        language.setKeywords(keywords.toArray(String[]::new));
        language.setDeclarations(declarations.toArray(String[]::new));
        language.setIdentifiers(symbols.typeNames().toArray(String[]::new));
        return language;
    }

    static Collection<String> without(Collection<String> words, Set<String> excluded) {
        return words.stream().filter(word -> !excluded.contains(word)).toList();
    }
}
