package fr.epistudio.epysia;

import fr.epistudio.epysia.assets.AssetRegistry;
import fr.epistudio.epysia.logging.Logger;
import fr.epistudio.epysia.render.RenderSystem;
import fr.epistudio.epysia.render.backend.RenderBackend;
import fr.epistudio.epysia.render.postfx.PostEffects;
import fr.epistudio.epysia.render.text.FontRegistry;
import fr.epistudio.epysia.input.action.InputActions;
import fr.epistudio.epysia.scene.Scene;
import fr.epistudio.epysia.scripting.Hud;
import fr.epistudio.epysia.scripting.Scheduler;
import fr.epistudio.epysia.window.Window;

public interface EngineServices {

    Window window();

    RenderBackend renderBackend();

    FontRegistry fonts();

    Scene scene();

    SystemRegistry systems();

    AssetRegistry assets();

    Logger logger();

    Scheduler scheduler();

    InputActions inputActions();

    Hud hud();

    PostEffects postEffects();

    void addRenderSystem(RenderSystem renderSystem);

    void removeRenderSystem(RenderSystem renderSystem);

    <T extends RenderSystem> T renderSystem(Class<T> type);
}
