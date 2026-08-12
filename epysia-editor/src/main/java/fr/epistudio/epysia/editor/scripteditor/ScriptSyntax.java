package fr.epistudio.epysia.editor.scripteditor;

import imgui.extension.texteditor.TextEditorLanguage;

import java.util.Set;

public interface ScriptSyntax {

    String displayName();

    Set<String> sourceExtensions();

    TextEditorLanguage create(JavaSymbols symbols);

    ImportStyle importStyle();
}
