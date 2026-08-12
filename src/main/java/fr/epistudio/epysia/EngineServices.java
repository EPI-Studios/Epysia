package fr.epistudio.epysia;

import fr.epistudio.epysia.assets.AssetRegistry;
import fr.epistudio.epysia.concurrent.BackgroundTasks;
import fr.epistudio.epysia.debug.DebugDraw;
import fr.epistudio.epysia.logging.Logger;
import fr.epistudio.epysia.net.NetworkService;
import fr.epistudio.epysia.steam.SteamService;
import fr.epistudio.epysia.render.PreRenderPass;
import fr.epistudio.epysia.render.RenderSystem;
import fr.epistudio.epysia.render.backend.RenderBackend;
import fr.epistudio.epysia.save.SaveGames;

import java.nio.file.Path;
import fr.epistudio.epysia.web.WebService;
import fr.epistudio.epysia.render.postfx.PostEffects;
import fr.epistudio.epysia.render.text.FontRegistry;
import fr.epistudio.epysia.audio.AudioSystem;

import java.util.Optional;
import fr.epistudio.epysia.input.action.InputActions;
import fr.epistudio.epysia.navigation.NavigationService;
import fr.epistudio.epysia.scene.Scene;
import fr.epistudio.epysia.scene.SceneLoader;
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

    BackgroundTasks backgroundTasks();

    InputActions inputActions();

    Hud hud();

    PostEffects postEffects();

    default DebugDraw debug() {
        return DebugDraw.detached();
    }

    default Optional<SceneLoader> scenes() {
        return Optional.empty();
    }

    default SaveGames saves() {
        return SaveGames.beside(Path.of(System.getProperty("user.dir", ".")));
    }

    default WebService web() {
        return new WebService(backgroundTasks());
    }

    default NetworkService network() {
        return NetworkService.detached();
    }

    default SteamService steam() {
        return SteamService.detached();
    }

    default Optional<AudioSystem> audio() {
        return Optional.empty();
    }

    default NavigationService navigation() {
        return NavigationService.detached();
    }

    default void requestCatchUpSteps(int steps) {
    }

    void addPreRenderPass(PreRenderPass pass);

    void removePreRenderPass(PreRenderPass pass);

    void addRenderSystem(RenderSystem renderSystem);

    void removeRenderSystem(RenderSystem renderSystem);

    <T extends RenderSystem> T renderSystem(Class<T> type);
}
