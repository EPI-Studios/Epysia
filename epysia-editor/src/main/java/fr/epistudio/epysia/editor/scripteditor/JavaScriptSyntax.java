package fr.epistudio.epysia.editor.scripteditor;

import imgui.extension.texteditor.TextEditorLanguageDefinition;

import java.util.List;
import java.util.Set;

public final class JavaScriptSyntax implements ScriptSyntax {

    private static final ImportStyle IMPORT_STYLE = ImportStyle.of(";", Set.of("java.lang"),
            List.of("class", "interface", "enum", "record"));

    @Override
    public String displayName() {
        return "Java";
    }

    @Override
    public Set<String> sourceExtensions() {
        return Set.of(".java");
    }

    @Override
    public TextEditorLanguageDefinition create(JavaSymbols symbols) {
        return JavaLanguageDefinition.create(symbols);
    }

    @Override
    public ImportStyle importStyle() {
        return IMPORT_STYLE;
    }
}
