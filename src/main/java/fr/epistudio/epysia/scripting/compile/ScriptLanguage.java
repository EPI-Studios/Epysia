package fr.epistudio.epysia.scripting.compile;

import fr.epistudio.epysia.scripting.editor.SyntaxDescriptor;
import fr.epistudio.epysia.scripting.foreign.ForeignScriptRuntime;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

public interface ScriptLanguage {

    String displayName();

    Set<String> sourceExtensions();

    String sourceExtension();

    String behaviourTemplate(String className);

    int order();

    ScriptCompileResult compile(List<Path> sources, Path outputDirectory, String classpath);

    default Optional<SyntaxDescriptor> syntax() {
        return Optional.empty();
    }

    default Optional<ForeignScriptRuntime> foreignRuntime() {
        return Optional.empty();
    }

    default Map<String, String> projectStubs() {
        return Map.of();
    }

    default List<Path> runtimeArchives() {
        return List.of();
    }

    default List<String> gradlePlugins() {
        return List.of();
    }

    default String sourceDirectoryName() {
        return "";
    }
}
