package fr.epistudio.epysia.editor.scripteditor;

import imgui.extension.texteditor.TextEditorLanguageDefinition;

import java.util.Set;

public interface ScriptSyntax {

    String displayName();

    Set<String> sourceExtensions();

    TextEditorLanguageDefinition create(JavaSymbols symbols);

    ImportStyle importStyle();
}
