package fr.epistudio.epysia.lang.python;

import fr.epistudio.epysia.scripting.compile.BehaviourTemplate;
import fr.epistudio.epysia.scripting.compile.ScriptCompileResult;
import fr.epistudio.epysia.scripting.compile.ScriptLanguage;
import fr.epistudio.epysia.scripting.editor.SyntaxDescriptor;
import fr.epistudio.epysia.scripting.editor.SyntaxDescriptorFile;
import fr.epistudio.epysia.scripting.foreign.ForeignScriptRuntime;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

public final class PythonScriptLanguage implements ScriptLanguage {

    private static final int ORDER = 30;
    private static final String STUB_FILENAME = "epysia.pyi";
    private static final String TEMPLATE_RESOURCE = "templates/Behaviour.py";
    private static final String SYNTAX_RESOURCE = "syntax/python.epysyntax";

    private final PythonRuntime runtime = new PythonRuntime();

    @Override
    public String displayName() {
        return "Python";
    }

    @Override
    public Set<String> sourceExtensions() {
        return Set.of(PythonRuntime.SOURCE_EXTENSION);
    }

    @Override
    public String sourceExtension() {
        return PythonRuntime.SOURCE_EXTENSION;
    }

    @Override
    public String behaviourTemplate(String className) {
        return BehaviourTemplate.loadedFrom(PythonScriptLanguage.class, TEMPLATE_RESOURCE)
                .rendered(className);
    }

    @Override
    public int order() {
        return ORDER;
    }

    @Override
    public Optional<SyntaxDescriptor> syntax() {
        return Optional.of(SyntaxDescriptorFile.read(PythonScriptLanguage.class, SYNTAX_RESOURCE));
    }

    @Override
    public Map<String, String> projectStubs() {
        return Map.of(STUB_FILENAME, PythonStubs.generate());
    }

    @Override
    public Optional<ForeignScriptRuntime> foreignRuntime() {
        return Optional.of(runtime);
    }

    @Override
    public ScriptCompileResult compile(List<Path> sources, Path outputDirectory, String classpath) {
        return ScriptCompileResult.succeeded();
    }
}
