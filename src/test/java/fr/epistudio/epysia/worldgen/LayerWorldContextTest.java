package fr.epistudio.epysia.worldgen;

import fr.epistudio.epysia.pool.ObjectPools;
import fr.epistudio.epysia.tween.Tweens;
import fr.epistudio.epysia.EngineServices;
import fr.epistudio.epysia.SystemRegistry;
import fr.epistudio.epysia.assets.AssetRegistry;
import fr.epistudio.epysia.concurrent.BackgroundTasks;
import fr.epistudio.epysia.render.PreRenderPass;
import fr.epistudio.epysia.render.RenderSystem;
import fr.epistudio.epysia.window.Window;
import fr.epistudio.epysia.input.action.InputActions;
import fr.epistudio.epysia.logging.Logger;
import fr.epistudio.epysia.render.backend.RenderBackend;
import fr.epistudio.epysia.render.postfx.PostEffects;
import fr.epistudio.epysia.render.text.FontRegistry;
import fr.epistudio.epysia.scene.Scene;
import fr.epistudio.epysia.scripting.Hud;
import fr.epistudio.epysia.scripting.Scheduler;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LayerWorldContextTest {

    private static final float CHUNK_SIZE = 100.0f;

    private static final class SourceLayer extends GenerationLayer<Integer> {

        @Override
        public float chunkSize() {
            return CHUNK_SIZE;
        }

        @Override
        public Integer generate(ChunkCoordinate coordinate, LayerContext context) {
            return valueOf(coordinate);
        }

        static int valueOf(ChunkCoordinate coordinate) {
            return coordinate.x() * 31 + coordinate.z() * 17;
        }
    }

    private static final class BlurLayer extends GenerationLayer<Integer> {

        private final SourceLayer source;
        private final Map<ChunkCoordinate, Integer> attached = new HashMap<>();

        private BlurLayer(SourceLayer source) {
            this.source = source;
        }

        @Override
        public float chunkSize() {
            return CHUNK_SIZE;
        }

        @Override
        public List<LayerDependency> dependencies() {
            return List.of(LayerDependency.of(source, CHUNK_SIZE));
        }

        @Override
        public Integer generate(ChunkCoordinate coordinate, LayerContext context) {
            int total = 0;
            for (int z = -1; z <= 1; z++) {
                for (int x = -1; x <= 1; x++) {
                    total += context.chunk(source, new ChunkCoordinate(coordinate.x() + x, coordinate.z() + z));
                }
            }
            return total;
        }

        @Override
        public void attach(ChunkCoordinate coordinate, Integer data, EngineServices services) {
            attached.put(coordinate, data);
        }

        @Override
        public void detach(ChunkCoordinate coordinate, Integer data, EngineServices services) {
            attached.remove(coordinate);
        }
    }

    @Test
    void contextualChunkIsIdenticalWhicheverOrderItWasGeneratedIn() {
        SourceLayer source = new SourceLayer();
        BlurLayer blur = new BlurLayer(source);
        HeadlessServices services = new HeadlessServices();
        LayerWorld world = new LayerWorld();
        world.require(blur, CHUNK_SIZE);
        ChunkCoordinate origin = new ChunkCoordinate(0, 0);

        settle(world, services, 50.0f, 50.0f);
        Optional<Integer> nearOrigin = Optional.ofNullable(blur.attached.get(origin));
        assertTrue(nearOrigin.isPresent(), "the origin chunk should have been generated");
        assertEquals(referenceBlur(origin), nearOrigin.get());

        settle(world, services, 100000.0f, 100000.0f);
        assertTrue(blur.attached.get(origin) == null, "the origin chunk should have been released");

        settle(world, services, 50.0f, 50.0f);
        assertEquals(referenceBlur(origin), blur.attached.get(origin),
                "regenerating the same chunk from a different approach must give the same result");
        services.tasks.shutdown();
    }

    @Test
    void readingOutsideTheDeclaredContextFails() {
        SourceLayer source = new SourceLayer();
        LayerContext context = new LayerContext(new WorldRect(0.0f, 0.0f, 1.0f, 1.0f), Map.of(), Map.of());
        assertThrows(RuntimeException.class, () -> context.chunk(source, new ChunkCoordinate(0, 0)));
    }

    private static int referenceBlur(ChunkCoordinate coordinate) {
        int total = 0;
        for (int z = -1; z <= 1; z++) {
            for (int x = -1; x <= 1; x++) {
                total += SourceLayer.valueOf(new ChunkCoordinate(coordinate.x() + x, coordinate.z() + z));
            }
        }
        return total;
    }

    private static void settle(LayerWorld world, HeadlessServices services, float focusX, float focusZ) {
        for (int attempt = 0; attempt < 100; attempt++) {
            world.update(services, focusX, focusZ);
            sleepBriefly();
            services.tasks.deliverCompleted();
        }
    }

    private static void sleepBriefly() {
        try {
            Thread.sleep(2L);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
        }
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

    private static final class HeadlessServices implements EngineServices {

        private final Tweens tweens = new Tweens();

    @Override
    public Tweens tweens() {
        return tweens;
    }

private final ObjectPools pools = new ObjectPools(this);

    @Override
    public ObjectPools pools() {
        return pools;
    }

        private final Logger logger = new SilentLogger();
        private final BackgroundTasks tasks = new BackgroundTasks(() -> logger);

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
}
