package fr.epistudio.epysia.lang.rust;

import fr.epistudio.epysia.scripting.foreign.ForeignComponentType;
import fr.epistudio.epysia.scripting.foreign.ForeignScriptRuntime;
import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.Source;
import org.graalvm.polyglot.Value;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;
import java.util.stream.Stream;

public final class RustRuntime implements ForeignScriptRuntime {

    static final String SOURCE_EXTENSION = ".rs";

    private Context context;

    @Override
    public String displayName() {
        return "Rust";
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
        Optional<String> unavailable = CargoToolchain.unavailableReason();
        if (unavailable.isPresent()) {
            messages.accept("Rust runtime unavailable: " + unavailable.get());
            return List.of();
        }
        Path crateDirectory;
        try {
            crateDirectory = RustCrateSync.sync(scriptsDirectory, sources);
        } catch (IOException failure) {
            messages.accept("Rust crate sync failed: " + failure.getMessage());
            return List.of();
        }
        Path wasmFile = build(crateDirectory, messages);
        if (wasmFile == null) {
            return List.of();
        }
        return loadModule(wasmFile, messages);
    }

    private List<ForeignComponentType> loadModule(Path wasmFile, Consumer<String> messages) {
        shutdown();
        try {
            context = Context.newBuilder("wasm").allowAllAccess(true).build();
            // eval() already instantiates an import-free module and returns its exports, newInstance() throws
            Value exports = context.eval(Source.newBuilder("wasm", wasmFile.toFile()).build());
            RustModule bridge = new RustModule(exports);
            return RustComponentType.allFrom(bridge, messages);
        } catch (IOException | RuntimeException failure) {
            messages.accept("Rust wasm module could not load: " + failure.getMessage());
            return List.of();
        }
    }

    private static Path build(Path crateDirectory, Consumer<String> messages) {
        try {
            Path manifestPath = crateDirectory.resolve("Cargo.toml").toAbsolutePath();
            Process process = new ProcessBuilder("cargo", "build", "--release",
                    "--target", "wasm32-unknown-unknown", "--manifest-path", manifestPath.toString())
                    .redirectErrorStream(true).start();
            String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            int exitCode = process.waitFor();
            if (exitCode != 0) {
                output.lines().filter(line -> !line.isBlank()).forEach(line -> messages.accept("Rust build: " + line));
                return null;
            }
            Path artifact = RustCrateSync.wasmArtifact(crateDirectory);
            if (!Files.isRegularFile(artifact)) {
                messages.accept("Rust build succeeded but " + artifact + " is missing");
                return null;
            }
            return artifact;
        } catch (IOException | InterruptedException failure) {
            if (failure instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            messages.accept("Rust build could not start: " + failure.getMessage());
            return null;
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
