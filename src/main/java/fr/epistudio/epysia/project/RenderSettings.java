package fr.epistudio.epysia.project;

import fr.epistudio.epysia.render.GraphicsApi;

public record RenderSettings(GraphicsApi api) {

    public static RenderSettings defaults() {
        return new RenderSettings(GraphicsApi.OPENGL);
    }

    public RenderSettings clamped() {
        return new RenderSettings(api == null ? GraphicsApi.OPENGL : api);
    }
}
