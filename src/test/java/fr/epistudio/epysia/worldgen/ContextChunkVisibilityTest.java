package fr.epistudio.epysia.worldgen;

import fr.epistudio.epysia.EngineServices;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ContextChunkVisibilityTest {
    private static final float CHUNK_SIZE = 100.0f;
    private static final float CONTEXT_PADDING = 10.0f;
    private static final float REQUIRED_RADIUS = 40.0f;

    private static final class GroundLayer extends GenerationLayer<Integer> {
        private final Set<ChunkCoordinate> generated = new HashSet<>();
        private final Set<ChunkCoordinate> attached = new HashSet<>();

        @Override
        public float chunkSize() {
            return CHUNK_SIZE;
        }

        @Override
        public Integer generate(ChunkCoordinate coordinate, LayerContext context) {
            generated.add(coordinate);
            return coordinate.x() * 31 + coordinate.z();
        }

        @Override
        public void attach(ChunkCoordinate coordinate, Integer data, EngineServices services) {
            attached.add(coordinate);
        }

        @Override
        public void detach(ChunkCoordinate coordinate, Integer data, EngineServices services) {
            attached.remove(coordinate);
        }
    }

    private static final class PropsLayer extends GenerationLayer<Integer> {
        private final GroundLayer ground;

        private PropsLayer(GroundLayer ground) {
            this.ground = ground;
        }

        @Override
        public float chunkSize() {
            return CHUNK_SIZE;
        }

        @Override
        public List<LayerDependency> dependencies() {
            return List.of(LayerDependency.of(ground, CONTEXT_PADDING));
        }

        @Override
        public Integer generate(ChunkCoordinate coordinate, LayerContext context) {
            return context.chunk(ground, coordinate);
        }
    }

    @Test
    void contextOnlyChunksAreGeneratedButNeverAttached() {
        GroundLayer ground = new GroundLayer();
        PropsLayer props = new PropsLayer(ground);
        HeadlessEngineServices services = new HeadlessEngineServices();
        LayerWorld world = new LayerWorld();
        world.require(ground, REQUIRED_RADIUS);
        world.require(props, REQUIRED_RADIUS);

        services.settle(world, 50.0f, 50.0f);

        ChunkCoordinate context = new ChunkCoordinate(-1, -1);
        assertTrue(ground.generated.contains(context),
                "the ring chunk must still be generated, dependents read it as context");
        assertFalse(ground.attached.contains(context),
                "the ring chunk is context only and must not be attached");
        assertTrue(ground.attached.contains(new ChunkCoordinate(0, 0)),
                "the requested chunk must be attached");
        services.tasks().shutdown();
    }

    @Test
    void contextChunkAttachesOnceTheFocusMakesItVisible() {
        GroundLayer ground = new GroundLayer();
        PropsLayer props = new PropsLayer(ground);
        HeadlessEngineServices services = new HeadlessEngineServices();
        LayerWorld world = new LayerWorld();
        world.require(ground, REQUIRED_RADIUS);
        world.require(props, REQUIRED_RADIUS);

        services.settle(world, 50.0f, 50.0f);
        ChunkCoordinate moved = new ChunkCoordinate(-1, -1);
        assertFalse(ground.attached.contains(moved), "still context only before the focus moves");

        services.settle(world, -50.0f, -50.0f);
        assertTrue(ground.attached.contains(moved),
                "a context chunk must attach once the focus makes it visible");
        assertFalse(ground.attached.contains(new ChunkCoordinate(1, 1)),
                "the chunk left behind must have been detached");
        services.tasks().shutdown();
    }
}
