package fr.epistudio.epysia.editor;

import fr.epistudio.epysia.EngineServices;
import fr.epistudio.epysia.SystemRegistry;
import fr.epistudio.epysia.render.backend.RenderBackend;
import fr.epistudio.epysia.render.text.FontRegistry;
import fr.epistudio.epysia.scene.Scene;
import fr.epistudio.epysia.window.Window;

public final class EditorEngineServices implements EngineServices {

    private final Window window;
    private final RenderBackend renderBackend;
    private final FontRegistry fonts;
    private final Scene scene;
    private final SystemRegistry systems;

    public EditorEngineServices(Window window, RenderBackend renderBackend, FontRegistry fonts, Scene scene,
                                SystemRegistry systems) {
        this.window = window;
        this.renderBackend = renderBackend;
        this.fonts = fonts;
        this.scene = scene;
        this.systems = systems;
    }

    @Override
    public Window window() {
        return window;
    }

    @Override
    public RenderBackend renderBackend() {
        return renderBackend;
    }

    @Override
    public FontRegistry fonts() {
        return fonts;
    }

    @Override
    public Scene scene() {
        return scene;
    }

    @Override
    public SystemRegistry systems() {
        return systems;
    }
}
