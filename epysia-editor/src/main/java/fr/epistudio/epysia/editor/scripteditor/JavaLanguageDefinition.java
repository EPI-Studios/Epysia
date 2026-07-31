package fr.epistudio.epysia.editor.scripteditor;

import imgui.extension.texteditor.TextEditorLanguageDefinition;

public final class JavaLanguageDefinition {

    private JavaLanguageDefinition() {
    }

    public static TextEditorLanguageDefinition create(JavaSymbols symbols) {
        TextEditorLanguageDefinition definition = new TextEditorLanguageDefinition();
        definition.setName("Java");
        definition.setKeywords(JavaSymbols.keywords().toArray(String[]::new));
        definition.setIdentifiers(SourceTokenRegexes.knownIdentifiers(symbols));
        definition.setCommentStart("/*");
        definition.setCommentEnd("*/");
        definition.setSingleLineComment("//");
        definition.setAutoIndentation(true);
        definition.setmCaseSensitive(true);
        definition.setTokenRegexStrings(SourceTokenRegexes.curlyBraceFamily());
        return definition;
    }
}
