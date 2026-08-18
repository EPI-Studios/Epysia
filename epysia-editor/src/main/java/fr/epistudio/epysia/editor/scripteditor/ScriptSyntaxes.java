package fr.epistudio.epysia.editor.scripteditor;

import fr.epistudio.epysia.scripting.compile.ScriptLanguage;
import fr.epistudio.epysia.scripting.compile.ScriptLanguages;
import imgui.extension.texteditor.TextEditorLanguage;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.ServiceLoader;

public final class ScriptSyntaxes {


    private final List<ScriptSyntax> syntaxes;
    private final Map<String, TextEditorLanguage> definitionsByExtension = new HashMap<>();
    private final List<TextEditorLanguage> ownedDefinitions = new ArrayList<>();
    private TextEditorLanguage plainText;

    private ScriptSyntaxes(List<ScriptSyntax> syntaxes) {
        this.syntaxes = syntaxes;
    }

    public static ScriptSyntaxes discover() {
        return discover(ScriptLanguages.discover());
    }

    public static ScriptSyntaxes discover(ScriptLanguages languages) {
        List<ScriptSyntax> discovered = new ArrayList<>();
        ServiceLoader.load(ScriptSyntax.class).forEach(discovered::add);
        for (ScriptLanguage language : languages.authoringOrder()) {
            language.syntax().ifPresent(descriptor ->
                    discovered.add(new DescriptorScriptSyntax(language, descriptor)));
        }
        if (discovered.isEmpty()) {
            discovered.add(new JavaScriptSyntax());
        }
        return new ScriptSyntaxes(List.copyOf(discovered));
    }

    public List<ScriptSyntax> syntaxes() {
        return syntaxes;
    }

    public void release() {
        ownedDefinitions.forEach(TextEditorLanguage::destroy);
        ownedDefinitions.clear();
        definitionsByExtension.clear();
    }

    public void rebuild(JavaSymbols symbols) {
        release();
        for (ScriptSyntax syntax : syntaxes) {
            TextEditorLanguage definition = syntax.create(symbols);
            ownedDefinitions.add(definition);
            syntax.sourceExtensions().forEach(extension ->
                    definitionsByExtension.put(extension, definition));
        }
        plainText = plainTextDefinition();
    }

    public TextEditorLanguage definitionFor(Path path) {
        return matching(path).orElse(plainText);
    }

    public Optional<ImportStyle> importStyleFor(Path path) {
        return syntaxFor(path).map(ScriptSyntax::importStyle);
    }

    private Optional<ScriptSyntax> syntaxFor(Path path) {
        String name = path.getFileName().toString();
        return syntaxes.stream()
                .filter(syntax -> syntax.sourceExtensions().stream().anyMatch(name::endsWith))
                .findFirst();
    }

    private Optional<TextEditorLanguage> matching(Path path) {
        String name = path.getFileName().toString();
        return definitionsByExtension.entrySet().stream()
                .filter(entry -> name.endsWith(entry.getKey()))
                .map(Map.Entry::getValue)
                .findFirst();
    }

    private static TextEditorLanguage plainTextDefinition() {
        return TextEditorLanguage.Markdown();
    }
}
