package fr.epistudio.epysia.scripting.foreign;

import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;

public interface ForeignScriptRuntime {

    String displayName();

    Set<String> sourceExtensions();

    List<ForeignComponentType> load(Path scriptsDirectory, Consumer<String> messages);

    default void shutdown() {
    }
}
