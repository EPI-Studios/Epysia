package fr.epistudio.epysia.scripting.compile;

import javax.tools.Diagnostic;
import javax.tools.DiagnosticCollector;
import javax.tools.JavaCompiler;
import javax.tools.JavaFileObject;
import javax.tools.StandardJavaFileManager;
import javax.tools.StandardLocation;
import javax.tools.ToolProvider;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.stream.Stream;

public final class ScriptCompiler {

    public record Result(boolean ok, List<String> messages) {
    }

    public Result compile(Path scriptsDirectory, Path outputDirectory) {
        List<String> messages = new ArrayList<>();
        cleanOutput(outputDirectory);
        List<Path> sources = collectSources(scriptsDirectory);
        if (sources.isEmpty()) {
            return new Result(true, messages);
        }
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        if (compiler == null) {
            messages.add("No system Java compiler available (run on a JDK, not a JRE).");
            return new Result(false, messages);
        }
        try {
            Files.createDirectories(outputDirectory);
        } catch (IOException exception) {
            messages.add("Could not create script output dir: " + exception.getMessage());
            return new Result(false, messages);
        }
        DiagnosticCollector<JavaFileObject> diagnostics = new DiagnosticCollector<>();
        try (StandardJavaFileManager fileManager = compiler.getStandardFileManager(diagnostics, Locale.ROOT, null)) {
            fileManager.setLocationFromPaths(StandardLocation.CLASS_OUTPUT, List.of(outputDirectory));
            Iterable<? extends JavaFileObject> units = fileManager.getJavaFileObjectsFromPaths(sources);
            List<String> options = List.of("-classpath", System.getProperty("java.class.path"));
            boolean ok = compiler.getTask(null, fileManager, diagnostics, options, null, units).call();
            for (Diagnostic<? extends JavaFileObject> diagnostic : diagnostics.getDiagnostics()) {
                messages.add(formatDiagnostic(diagnostic));
            }
            return new Result(ok, messages);
        } catch (IOException exception) {
            messages.add("Script compile failed: " + exception.getMessage());
            return new Result(false, messages);
        }
    }

    private static String formatDiagnostic(Diagnostic<? extends JavaFileObject> diagnostic) {
        String source = diagnostic.getSource() == null ? "scripts" : diagnostic.getSource().getName();
        return diagnostic.getKind() + " " + source + ":" + diagnostic.getLineNumber()
                + ": " + diagnostic.getMessage(Locale.ROOT);
    }

    private static void cleanOutput(Path outputDirectory) {
        if (!Files.isDirectory(outputDirectory)) {
            return;
        }
        try (Stream<Path> walk = Files.walk(outputDirectory)) {
            walk.sorted(java.util.Comparator.reverseOrder()).forEach(path -> {
                try {
                    Files.deleteIfExists(path);
                } catch (IOException ignored) {
                }
            });
        } catch (IOException ignored) {
        }
    }

    private static List<Path> collectSources(Path scriptsDirectory) {
        if (scriptsDirectory == null || !Files.isDirectory(scriptsDirectory)) {
            return List.of();
        }
        try (Stream<Path> walk = Files.walk(scriptsDirectory)) {
            List<Path> sources = new ArrayList<>();
            walk.filter(path -> path.toString().endsWith(".java")).forEach(sources::add);
            return sources;
        } catch (IOException exception) {
            return List.of();
        }
    }
}
