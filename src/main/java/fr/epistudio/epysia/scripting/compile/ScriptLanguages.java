package fr.epistudio.epysia.scripting.compile;

import fr.epistudio.epysia.project.ProjectLibraries;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.ServiceLoader;
import java.util.Set;
import java.util.stream.Stream;

public final class ScriptLanguages {

    private final List<ScriptLanguage> languages;
    private final List<ScriptLanguage> discoveryOrder;

    private ScriptLanguages(List<ScriptLanguage> languages, List<ScriptLanguage> discoveryOrder) {
        this.languages = languages;
        this.discoveryOrder = discoveryOrder;
    }

    public static ScriptLanguages discover() {
        List<ScriptLanguage> discovered = new ArrayList<>();
        ServiceLoader.load(ScriptLanguage.class).forEach(discovered::add);
        if (discovered.stream().noneMatch(language -> language instanceof JavaScriptLanguage)) {
            discovered.add(new JavaScriptLanguage());
        }
        List<ScriptLanguage> compileOrder = new ArrayList<>(discovered);
        compileOrder.sort(Comparator.comparingInt(ScriptLanguage::order));
        return new ScriptLanguages(List.copyOf(compileOrder), List.copyOf(discovered));
    }

    public List<ScriptLanguage> languages() {
        return languages;
    }

    public List<ScriptLanguage> authoringOrder() {
        return discoveryOrder;
    }

    public ScriptLanguage defaultLanguage() {
        return discoveryOrder.get(0);
    }

    public Set<String> sourceExtensions() {
        Set<String> extensions = new HashSet<>();
        languages.forEach(language -> extensions.addAll(language.sourceExtensions()));
        return Set.copyOf(extensions);
    }

    public boolean isSource(Path path) {
        String name = path.getFileName().toString();
        return sourceExtensions().stream().anyMatch(name::endsWith);
    }

    public String baseNameOf(Path source) {
        String name = source.getFileName().toString();
        for (String extension : sourceExtensions()) {
            if (name.endsWith(extension)) {
                return name.substring(0, name.length() - extension.length());
            }
        }
        return name;
    }

    public ScriptCompileResult compileAll(Path scriptsDirectory, Path outputDirectory,
                                          ProjectLibraries libraries) {
        cleanOutput(outputDirectory);
        Map<ScriptLanguage, List<Path>> sources = groupSources(scriptsDirectory);
        if (sources.isEmpty()) {
            return ScriptCompileResult.succeeded();
        }
        try {
            Files.createDirectories(outputDirectory);
        } catch (IOException exception) {
            return ScriptCompileResult.failed("Could not create script output dir: " + exception.getMessage());
        }
        return compileEachLanguage(sources, outputDirectory, classpathFor(outputDirectory, libraries));
    }

    private static ScriptCompileResult compileEachLanguage(Map<ScriptLanguage, List<Path>> sources,
                                                           Path outputDirectory, String classpath) {
        ScriptCompileResult combined = ScriptCompileResult.succeeded();
        for (Map.Entry<ScriptLanguage, List<Path>> entry : sources.entrySet()) {
            combined = combined.mergedWith(entry.getKey().compile(entry.getValue(), outputDirectory, classpath));
            if (!combined.ok()) {
                return combined;
            }
        }
        return combined;
    }

    private static String classpathFor(Path outputDirectory, ProjectLibraries libraries) {
        return System.getProperty("java.class.path")
                + java.io.File.pathSeparator + outputDirectory.toAbsolutePath()
                + libraries.classpathSuffix();
    }

    private Map<ScriptLanguage, List<Path>> groupSources(Path scriptsDirectory) {
        Map<ScriptLanguage, List<Path>> grouped = new LinkedHashMap<>();
        for (Path source : collectSources(scriptsDirectory)) {
            languageFor(source).ifPresent(language ->
                    grouped.computeIfAbsent(language, ignored -> new ArrayList<>()).add(source));
        }
        return grouped;
    }

    private java.util.Optional<ScriptLanguage> languageFor(Path source) {
        String name = source.getFileName().toString();
        return languages.stream()
                .filter(language -> language.sourceExtensions().stream().anyMatch(name::endsWith))
                .findFirst();
    }

    private List<Path> collectSources(Path scriptsDirectory) {
        if (scriptsDirectory == null || !Files.isDirectory(scriptsDirectory)) {
            return List.of();
        }
        Set<String> extensions = sourceExtensions();
        try (Stream<Path> walk = Files.walk(scriptsDirectory)) {
            return walk.filter(Files::isRegularFile)
                    .filter(path -> extensions.stream().anyMatch(path.getFileName().toString()::endsWith))
                    .sorted()
                    .toList();
        } catch (IOException exception) {
            return List.of();
        }
    }

    private static void cleanOutput(Path outputDirectory) {
        if (!Files.isDirectory(outputDirectory)) {
            return;
        }
        try (Stream<Path> walk = Files.walk(outputDirectory)) {
            walk.sorted(Comparator.reverseOrder()).forEach(ScriptLanguages::deleteQuietly);
        } catch (IOException ignored) {
        }
    }

    private static void deleteQuietly(Path path) {
        try {
            Files.deleteIfExists(path);
        } catch (IOException ignored) {
        }
    }
}
