package fr.epistudio.epysia.editor.scripteditor;

import fr.epistudio.epysia.scripting.compile.ScriptLanguage;
import fr.epistudio.epysia.scripting.editor.SyntaxDescriptor;
import imgui.extension.texteditor.TextEditorLanguage;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public final class DescriptorScriptSyntax implements ScriptSyntax {

    private final SyntaxDescriptor descriptor;
    private final Set<String> extensions;

    public DescriptorScriptSyntax(ScriptLanguage language, SyntaxDescriptor descriptor) {
        this.descriptor = descriptor;
        this.extensions = language.sourceExtensions();
    }

    @Override
    public String displayName() {
        return descriptor.displayName();
    }

    @Override
    public Set<String> sourceExtensions() {
        return extensions;
    }

    @Override
    public TextEditorLanguage create(JavaSymbols symbols) {
        List<String> plainKeywords = new ArrayList<>(descriptor.keywords());
        plainKeywords.removeAll(descriptor.declarationKeywords());
        return CurlyBraceLanguage.create(descriptor.displayName(), plainKeywords,
                descriptor.declarationKeywords(), symbols);
    }

    @Override
    public ImportStyle importStyle() {
        return ImportStyle.of(descriptor.importStatementSuffix(), descriptor.implicitPackages(),
                List.copyOf(descriptor.declarationKeywords()));
    }
}
