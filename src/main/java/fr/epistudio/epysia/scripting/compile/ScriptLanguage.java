package fr.epistudio.epysia.scripting.compile;

import java.nio.file.Path;
import java.util.List;
import java.util.Set;

public interface ScriptLanguage {

    String displayName();

    Set<String> sourceExtensions();

    String sourceExtension();

    String behaviourTemplate(String className);

    int order();

    ScriptCompileResult compile(List<Path> sources, Path outputDirectory, String classpath);
}
