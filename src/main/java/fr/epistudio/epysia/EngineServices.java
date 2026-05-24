package fr.epistudio.epysia;

import fr.epistudio.epysia.render.backend.RenderBackend;
import fr.epistudio.epysia.render.text.FontRegistry;
import fr.epistudio.epysia.scene.Scene;
import fr.epistudio.epysia.window.Window;

public interface EngineServices {

    Window window();

    RenderBackend renderBackend();

    FontRegistry fonts();

    Scene scene();

    SystemRegistry systems();
}
