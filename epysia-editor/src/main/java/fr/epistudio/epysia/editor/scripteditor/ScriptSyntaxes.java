package fr.epistudio.epysia.editor.scripteditor;

import imgui.extension.texteditor.TextEditorLanguageDefinition;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.ServiceLoader;

public final class ScriptSyntaxes {

    private static final String PLAIN_TEXT_NAME = "Text";

    private final List<ScriptSyntax> syntaxes;
    private final Map<String, TextEditorLanguageDefinition> definitionsByExtension = new HashMap<>();
    private TextEditorLanguageDefinition plainText;

    private ScriptSyntaxes(List<ScriptSyntax> syntaxes) {
        this.syntaxes = syntaxes;
    }

    public static ScriptSyntaxes discover() {
        List<ScriptSyntax> discovered = new ArrayList<>();
        ServiceLoader.load(ScriptSyntax.class).forEach(discovered::add);
        if (discovered.isEmpty()) {
            discovered.add(new JavaScriptSyntax());
        }
        return new ScriptSyntaxes(List.copyOf(discovered));
    }

    public List<ScriptSyntax> syntaxes() {
        return syntaxes;
    }

    public void rebuild(JavaSymbols symbols) {
        definitionsByExtension.clear();
        for (ScriptSyntax syntax : syntaxes) {
            TextEditorLanguageDefinition definition = syntax.create(symbols);
            syntax.sourceExtensions().forEach(extension ->
                    definitionsByExtension.put(extension, definition));
        }
        plainText = plainTextDefinition();
    }

    public TextEditorLanguageDefinition definitionFor(Path path) {
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

    private Optional<TextEditorLanguageDefinition> matching(Path path) {
        String name = path.getFileName().toString();
        return definitionsByExtension.entrySet().stream()
                .filter(entry -> name.endsWith(entry.getKey()))
                .map(Map.Entry::getValue)
                .findFirst();
    }

    private static TextEditorLanguageDefinition plainTextDefinition() {
        TextEditorLanguageDefinition definition = new TextEditorLanguageDefinition();
        definition.setName(PLAIN_TEXT_NAME);
        definition.setKeywords(new String[0]);
        definition.setIdentifiers(Map.of());
        definition.setAutoIndentation(true);
        definition.setmCaseSensitive(true);
        definition.setTokenRegexStrings(SourceTokenRegexes.curlyBraceFamily());
        return definition;
    }
}
