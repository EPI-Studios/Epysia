package fr.epistudio.epysia.lang.python;

import fr.epistudio.epysia.scripting.compile.ScriptCompileResult;
import fr.epistudio.epysia.scripting.compile.ScriptLanguage;
import fr.epistudio.epysia.scripting.editor.SyntaxDescriptor;
import fr.epistudio.epysia.scripting.foreign.ForeignScriptRuntime;

import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.Set;

public final class PythonScriptLanguage implements ScriptLanguage {

    private static final int ORDER = 30;
    private static final String TEMPLATE = """
            from epysia import Behaviour, component, export


            @component(name="%s", category="Scripts")
            class %s(Behaviour):

                speed = export(1.0, label="Speed")

                def on_start(self):
                    pass

                def on_update(self, input, delta_seconds):
                    pass
            """;

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
        return TEMPLATE.formatted(className, className);
    }

    @Override
    public int order() {
        return ORDER;
    }

    @Override
    public Optional<SyntaxDescriptor> syntax() {
        return Optional.of(SyntaxDescriptor.indented("Python", PythonSyntax.KEYWORDS,
                PythonSyntax.DECLARATIONS, PythonSyntax.IMPLICIT_PACKAGES));
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
