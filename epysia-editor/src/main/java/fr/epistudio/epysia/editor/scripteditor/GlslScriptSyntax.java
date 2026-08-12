package fr.epistudio.epysia.editor.scripteditor;

import imgui.extension.texteditor.TextEditorLanguage;

import java.util.List;
import java.util.Set;

public final class GlslScriptSyntax implements ScriptSyntax {

    private static final ImportStyle IMPORT_STYLE = ImportStyle.of("", Set.of(), List.of());

    @Override
    public String displayName() {
        return "GLSL";
    }

    @Override
    public Set<String> sourceExtensions() {
        return Set.of(".glsl", ".vert", ".frag", ".geom", ".comp", ".tesc", ".tese");
    }

    @Override
    public TextEditorLanguage create(JavaSymbols symbols) {
        return GlslLanguageDefinition.create(symbols);
    }

    @Override
    public ImportStyle importStyle() {
        return IMPORT_STYLE;
    }
}
