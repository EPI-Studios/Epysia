package fr.epistudio.epysia.scripting.compile;

import fr.epistudio.epysia.reflection.DiscoveredComponent;

import java.util.List;

public record ScriptLoadResult(boolean ok, List<DiscoveredComponent> components,
                               List<String> messages, ScriptClassLoader loader) {
}
