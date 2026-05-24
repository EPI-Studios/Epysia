package fr.epistudio.epysia.render.backend;

public record RenderTargetHandle(long id) {

    public static final RenderTargetHandle SCREEN = new RenderTargetHandle(0L);
}
