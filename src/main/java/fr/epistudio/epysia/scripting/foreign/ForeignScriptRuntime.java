package fr.epistudio.epysia.scripting.foreign;

import fr.epistudio.epysia.logging.Logger;

import java.nio.file.Path;
import java.util.List;
import java.util.Set;

public interface ForeignScriptRuntime {

    String displayName();

    Set<String> sourceExtensions();

    String sourceExtension();

    String behaviourTemplate(String className);

    List<ForeignComponentType> load(Path scriptsDirectory, Logger logger);

    default void shutdown() {
    }
}
