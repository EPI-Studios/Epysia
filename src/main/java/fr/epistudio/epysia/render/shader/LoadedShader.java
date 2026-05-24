package fr.epistudio.epysia.render.shader;

import java.util.List;

public record LoadedShader(String source, List<String> dependencyPaths) {

    public LoadedShader {
        dependencyPaths = List.copyOf(dependencyPaths);
    }
}
