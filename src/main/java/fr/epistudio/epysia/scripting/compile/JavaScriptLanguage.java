package fr.epistudio.epysia.scripting.compile;

import javax.tools.Diagnostic;
import javax.tools.DiagnosticCollector;
import javax.tools.JavaCompiler;
import javax.tools.JavaFileObject;
import javax.tools.StandardJavaFileManager;
import javax.tools.StandardLocation;
import javax.tools.ToolProvider;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public final class JavaScriptLanguage implements ScriptLanguage {

    public static final int ORDER = 50;

    private static final String TEMPLATE = """
            import fr.epistudio.epysia.EngineServices;
            import fr.epistudio.epysia.components.EpysiaComponent;
            import fr.epistudio.epysia.components.Export;
            import fr.epistudio.epysia.input.InputState;
            import fr.epistudio.epysia.scripting.Behaviour;

            @EpysiaComponent(name = "%s", category = "Scripts")
            public final class %s extends Behaviour {

                @Export(label = "Speed")
                private float speed = 1.0f;

                @Override
                public void onStart(EngineServices services) {
                }

                @Override
                public void onUpdate(InputState input, float deltaTimeSeconds) {
                }
            }
            """;

    @Override
    public String displayName() {
        return "Java";
    }

    @Override
    public Set<String> sourceExtensions() {
        return Set.of(".java");
    }

    @Override
    public String sourceExtension() {
        return ".java";
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
    public ScriptCompileResult compile(List<Path> sources, Path outputDirectory, String classpath) {
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        if (compiler == null) {
            return ScriptCompileResult.failed("No system Java compiler available (run on a JDK, not a JRE).");
        }
        DiagnosticCollector<JavaFileObject> diagnostics = new DiagnosticCollector<>();
        try (StandardJavaFileManager fileManager = compiler.getStandardFileManager(diagnostics, Locale.ROOT, null)) {
            fileManager.setLocationFromPaths(StandardLocation.CLASS_OUTPUT, List.of(outputDirectory));
            Iterable<? extends JavaFileObject> units = fileManager.getJavaFileObjectsFromPaths(sources);
            boolean ok = compiler.getTask(null, fileManager, diagnostics,
                    List.of("-classpath", classpath), null, units).call();
            return new ScriptCompileResult(ok, messagesOf(diagnostics));
        } catch (IOException exception) {
            return ScriptCompileResult.failed("Script compile failed: " + exception.getMessage());
        }
    }

    private static List<String> messagesOf(DiagnosticCollector<JavaFileObject> diagnostics) {
        List<String> messages = new ArrayList<>();
        for (Diagnostic<? extends JavaFileObject> diagnostic : diagnostics.getDiagnostics()) {
            messages.add(formatDiagnostic(diagnostic));
        }
        return List.copyOf(messages);
    }

    private static String formatDiagnostic(Diagnostic<? extends JavaFileObject> diagnostic) {
        String source = diagnostic.getSource() == null ? "scripts" : diagnostic.getSource().getName();
        return diagnostic.getKind() + " " + source + ":" + diagnostic.getLineNumber()
                + ": " + diagnostic.getMessage(Locale.ROOT);
    }
}
