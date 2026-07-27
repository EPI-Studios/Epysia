package fr.epistudio.epysia.render.environment;

import fr.epistudio.epysia.exceptions.EpysiaException;
import fr.epistudio.epysia.render.shader.LoadedShader;
import fr.epistudio.epysia.render.shader.ShaderLoader;

import java.util.ArrayList;
import java.util.List;

final class SkyShaderComposer {

    private static final String BODY_MARKER = "// SKY_BODY";

    private SkyShaderComposer() {
    }

    static LoadedShader compose(ShaderLoader loader, String wrapperPath, SkySource source) {
        LoadedShader wrapper = loader.load(wrapperPath);
        LoadedShader body = loadBody(loader, source);
        String composed = replaceMarker(wrapper.source(), body.source());
        List<String> dependencies = new ArrayList<>(wrapper.dependencyPaths());
        dependencies.addAll(body.dependencyPaths());
        return new LoadedShader(composed, List.copyOf(dependencies));
    }

    private static LoadedShader loadBody(ShaderLoader loader, SkySource source) {
        try {
            return loader.load(source.bodyPath());
        } catch (EpysiaException error) {
            return loader.load(SkySource.PROCEDURAL.bodyPath());
        }
    }

    private static String replaceMarker(String wrapper, String body) {
        List<String> lines = new ArrayList<>();
        for (String line : wrapper.split("\n", -1)) {
            lines.add(line.trim().equals(BODY_MARKER) ? body : line);
        }
        return String.join("\n", lines);
    }
}
