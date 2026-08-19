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

final class JavaCompilation {

    private JavaCompilation() {
    }

    static ScriptCompileResult run(List<Path> sources, Path outputDirectory, String classpath) {
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
