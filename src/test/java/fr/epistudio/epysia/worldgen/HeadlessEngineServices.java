package fr.epistudio.epysia.worldgen;

import fr.epistudio.epysia.pool.ObjectPools;
import fr.epistudio.epysia.EngineServices;
import fr.epistudio.epysia.SystemRegistry;
import fr.epistudio.epysia.assets.AssetRegistry;
import fr.epistudio.epysia.concurrent.BackgroundTasks;
import fr.epistudio.epysia.input.action.InputActions;
import fr.epistudio.epysia.logging.Logger;
import fr.epistudio.epysia.render.PreRenderPass;
import fr.epistudio.epysia.render.RenderSystem;
import fr.epistudio.epysia.render.backend.RenderBackend;
import fr.epistudio.epysia.render.postfx.PostEffects;
import fr.epistudio.epysia.render.text.FontRegistry;
import fr.epistudio.epysia.scene.Scene;
import fr.epistudio.epysia.scripting.Hud;
import fr.epistudio.epysia.scripting.Scheduler;
import fr.epistudio.epysia.window.Window;

final class HeadlessEngineServices implements EngineServices {

    private final ObjectPools pools = new ObjectPools(this);

    @Override
    public ObjectPools pools() {
        return pools;
    }
    private static final class SilentLogger implements Logger {
        @Override
        public void info(String message) {
        }

        @Override
        public void warn(String message) {
        }

        @Override
        public void error(String message) {
        }

        @Override
        public void error(String message, Throwable cause) {
        }
    }

    private final Logger logger = new SilentLogger();
    private final BackgroundTasks tasks = new BackgroundTasks(() -> logger);

    BackgroundTasks tasks() {
        return tasks;
    }

    void settle(LayerWorld world, float focusX, float focusZ) {
        for (int attempt = 0; attempt < 100; attempt++) {
            world.update(this, focusX, focusZ);
            pause();
            tasks.deliverCompleted();
        }
    }

    private static void pause() {
        try {
            Thread.sleep(2L);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
        }
    }

    @Override
    public BackgroundTasks backgroundTasks() {
        return tasks;
    }

    @Override
    public Logger logger() {
        return logger;
    }

    @Override
    public Window window() {
        throw new UnsupportedOperationException();
    }

    @Override
    public RenderBackend renderBackend() {
        throw new UnsupportedOperationException();
    }

    @Override
    public FontRegistry fonts() {
        throw new UnsupportedOperationException();
    }

    @Override
    public Scene scene() {
        throw new UnsupportedOperationException();
    }

    @Override
    public SystemRegistry systems() {
        throw new UnsupportedOperationException();
    }

    @Override
    public AssetRegistry assets() {
        throw new UnsupportedOperationException();
    }

    @Override
    public Scheduler scheduler() {
        throw new UnsupportedOperationException();
    }

    @Override
    public InputActions inputActions() {
        return InputActions.defaults();
    }

    @Override
    public Hud hud() {
        throw new UnsupportedOperationException();
    }

    @Override
    public PostEffects postEffects() {
        throw new UnsupportedOperationException();
    }

    @Override
    public void addPreRenderPass(PreRenderPass pass) {
    }

    @Override
    public void removePreRenderPass(PreRenderPass pass) {
    }

    @Override
    public void addRenderSystem(RenderSystem renderSystem) {
    }

    @Override
    public void removeRenderSystem(RenderSystem renderSystem) {
    }

    @Override
    public <T extends RenderSystem> T renderSystem(Class<T> type) {
        throw new UnsupportedOperationException();
    }
}
