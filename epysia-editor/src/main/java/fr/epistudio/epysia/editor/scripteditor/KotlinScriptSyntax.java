package fr.epistudio.epysia.editor.scripteditor;

import imgui.extension.texteditor.TextEditorLanguage;

import java.util.List;
import java.util.Set;

public final class KotlinScriptSyntax implements ScriptSyntax {

    private static final ImportStyle IMPORT_STYLE = ImportStyle.of("",
            Set.of("kotlin", "kotlin.annotation", "kotlin.collections", "kotlin.comparisons",
                    "kotlin.io", "kotlin.jvm", "kotlin.ranges", "kotlin.sequences", "kotlin.text",
                    "java.lang"),
            List.of("class", "interface", "object", "fun"));

    @Override
    public String displayName() {
        return "Kotlin";
    }

    @Override
    public Set<String> sourceExtensions() {
        return Set.of(".kt");
    }

    @Override
    public TextEditorLanguage create(JavaSymbols symbols) {
        return KotlinLanguageDefinition.create(symbols);
    }

    @Override
    public ImportStyle importStyle() {
        return IMPORT_STYLE;
    }
}
