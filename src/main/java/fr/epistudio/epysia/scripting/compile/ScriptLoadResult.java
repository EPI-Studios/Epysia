package fr.epistudio.epysia.scripting.compile;

import fr.epistudio.epysia.reflection.DiscoveredComponent;
import fr.epistudio.epysia.scripting.ProjectRenderSetup;

import java.util.List;

public record ScriptLoadResult(boolean ok, List<DiscoveredComponent> components,
                               List<Class<? extends ProjectRenderSetup>> renderSetups,
                               List<String> messages, ScriptClassLoader loader) {
}
