package fr.epistudio.epysia.scripting.foreign;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.ServiceLoader;
import java.util.Set;

public final class ForeignScriptRuntimes {

    private final List<ForeignScriptRuntime> runtimes;

    private ForeignScriptRuntimes(List<ForeignScriptRuntime> runtimes) {
        this.runtimes = runtimes;
    }

    public static ForeignScriptRuntimes discover() {
        List<ForeignScriptRuntime> discovered = new ArrayList<>();
        ServiceLoader.load(ForeignScriptRuntime.class).forEach(discovered::add);
        return new ForeignScriptRuntimes(List.copyOf(discovered));
    }

    public List<ForeignScriptRuntime> runtimes() {
        return runtimes;
    }

    public boolean isEmpty() {
        return runtimes.isEmpty();
    }

    public Set<String> sourceExtensions() {
        Set<String> extensions = new java.util.HashSet<>();
        runtimes.forEach(runtime -> extensions.addAll(runtime.sourceExtensions()));
        return Set.copyOf(extensions);
    }

    public boolean isSource(Path file) {
        String name = file.getFileName().toString();
        return sourceExtensions().stream().anyMatch(name::endsWith);
    }

    public Optional<ForeignScriptRuntime> forExtension(String extension) {
        for (ForeignScriptRuntime runtime : runtimes) {
            if (runtime.sourceExtensions().contains(extension)) {
                return Optional.of(runtime);
            }
        }
        return Optional.empty();
    }

    public void shutdown() {
        runtimes.forEach(ForeignScriptRuntime::shutdown);
    }
}
