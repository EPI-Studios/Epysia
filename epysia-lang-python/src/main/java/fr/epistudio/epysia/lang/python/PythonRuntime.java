package fr.epistudio.epysia.lang.python;

import fr.epistudio.epysia.scripting.foreign.ForeignComponentType;
import fr.epistudio.epysia.scripting.foreign.ForeignScriptRuntime;
import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.Source;
import org.graalvm.polyglot.Value;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;
import java.util.stream.Stream;

public final class PythonRuntime implements ForeignScriptRuntime {

    static final String SOURCE_EXTENSION = ".py";
    private static final String MODULE_RESOURCE = "epysia.py";
    private static final String MODULE_NAME = "epysia";
    private static final String SOURCE_BINDING = "__epysia_module_source";
    private static final String INSTALL_MODULE = """
            import sys, types
            _module = types.ModuleType("%s")
            exec(%s, _module.__dict__)
            sys.modules["%s"] = _module
            """.formatted(MODULE_NAME, SOURCE_BINDING, MODULE_NAME);
    private static final String REGISTERED = """
            import sys
            list(sys.modules["%s"]._registered)
            """.formatted(MODULE_NAME);
    private static final String RESET_REGISTRY = """
            import sys
            sys.modules["%s"]._registered.clear()
            """.formatted(MODULE_NAME);

    private Context context;

    @Override
    public String displayName() {
        return "Python";
    }

    @Override
    public Set<String> sourceExtensions() {
        return Set.of(SOURCE_EXTENSION);
    }

    @Override
    public List<ForeignComponentType> load(Path scriptsDirectory, Consumer<String> messages) {
        List<Path> sources = sourcesIn(scriptsDirectory);
        if (sources.isEmpty()) {
            return List.of();
        }
        shutdown();
        try {
            context = openContext(scriptsDirectory);
        } catch (RuntimeException failure) {
            messages.accept("Python runtime could not start: " + failure.getMessage());
            return List.of();
        }
        sources.forEach(source -> evaluate(source, messages));
        return typesOf(messages);
    }

    private List<ForeignComponentType> typesOf(Consumer<String> messages) {
        List<ForeignComponentType> types = new ArrayList<>();
        Value registered = context.eval("python", REGISTERED);
        for (long index = 0; index < registered.getArraySize(); index++) {
            try {
                types.add(PythonComponentType.of(registered.getArrayElement(index), messages));
            } catch (RuntimeException failure) {
                messages.accept("Python component rejected: " + failure.getMessage());
            }
        }
        return types;
    }

    private void evaluate(Path source, Consumer<String> messages) {
        try {
            context.eval(Source.newBuilder("python", source.toFile()).build());
        } catch (IOException | RuntimeException failure) {
            messages.accept(source.getFileName() + ": " + failure.getMessage());
        }
    }

    private Context openContext(Path scriptsDirectory) {
        Context opened = Context.newBuilder("python")
                .allowAllAccess(true)
                .option("engine.WarnInterpreterOnly", "false")
                .option("python.PythonPath", scriptsDirectory.toAbsolutePath().toString())
                .build();
        opened.getBindings("python").putMember(SOURCE_BINDING, moduleSource());
        opened.eval("python", INSTALL_MODULE);
        opened.eval("python", RESET_REGISTRY);
        return opened;
    }

    private static String moduleSource() {
        try (InputStream stream = PythonRuntime.class.getResourceAsStream(MODULE_RESOURCE)) {
            return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException unreadable) {
            throw new IllegalStateException("The epysia python module is missing from the pack.", unreadable);
        }
    }

    private static List<Path> sourcesIn(Path scriptsDirectory) {
        if (scriptsDirectory == null || !Files.isDirectory(scriptsDirectory)) {
            return List.of();
        }
        try (Stream<Path> walk = Files.walk(scriptsDirectory)) {
            return walk.filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString().endsWith(SOURCE_EXTENSION))
                    .sorted()
                    .toList();
        } catch (IOException unreadable) {
            return List.of();
        }
    }

    @Override
    public void shutdown() {
        if (context != null) {
            context.close(true);
            context = null;
        }
    }
}
