package fr.epistudio.epysia.graph;

import fr.epistudio.epysia.pool.ObjectPools;
import fr.epistudio.epysia.tween.Tweens;
import fr.epistudio.epysia.EngineServices;
import fr.epistudio.epysia.SystemRegistry;
import fr.epistudio.epysia.assets.AssetRegistry;
import fr.epistudio.epysia.concurrent.BackgroundTasks;
import fr.epistudio.epysia.gameobjects.GameObject;
import fr.epistudio.epysia.input.InputState;
import fr.epistudio.epysia.input.KeyCode;
import fr.epistudio.epysia.input.MutableInputState;
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
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class InputValueNodeTest {

    private static final EngineServices SERVICES = new HeadlessServices();

    @Test
    void keyDownOutputsTrueWhileTheKeyIsHeld() {
        Fixture fixture = Fixture.of(BuiltinNodes.INPUT_KEY_DOWN, BuiltinNodes.CONDITION_PIN, "flow.branch");
        MutableInputState input = new MutableInputState();
        input.onKey(KeyCode.SPACE, true);
        assertTrue(fixture.booleanValue(input));
        input.onKey(KeyCode.SPACE, false);
        assertFalse(fixture.booleanValue(input));
    }

    @Test
    void axisReportsDirectionFromTheHeldKeys() {
        Fixture fixture = Fixture.of(BuiltinNodes.INPUT_AXIS, "A", "math.add");
        MutableInputState input = new MutableInputState();
        assertEquals(0.0f, fixture.floatValue(input));
        input.onKey(KeyCode.D, true);
        assertEquals(1.0f, fixture.floatValue(input));
        input.onKey(KeyCode.A, true);
        assertEquals(0.0f, fixture.floatValue(input));
        input.onKey(KeyCode.D, false);
        assertEquals(-1.0f, fixture.floatValue(input));
    }

    private record Fixture(GraphInterpreter interpreter, GraphInstance instance,
                           GraphNode consumer, String consumerPin) {

        static Fixture of(String sourceTypeKey, String consumerPin, String consumerTypeKey) {
            GraphAsset asset = new GraphAsset();
            GraphNode source = asset.addNode(sourceTypeKey, 0.0f, 0.0f);
            GraphNode consumer = asset.addNode(consumerTypeKey, 0.0f, 0.0f);
            asset.edges().add(new GraphEdge(source.id(), BuiltinNodes.VALUE_PIN,
                    consumer.id(), consumerPin));
            GraphInstance instance = new GraphInstance(asset, "input-value-node-test",
                    new GameObject("Actor"), Map.of());
            return new Fixture(new GraphInterpreter(GraphNodeRegistry.withBuiltins()),
                    instance, consumer, consumerPin);
        }

        boolean booleanValue(InputState input) {
            return GraphValues.asBoolean(pull(input, PinType.BOOLEAN));
        }

        float floatValue(InputState input) {
            return GraphValues.asFloat(pull(input, PinType.FLOAT));
        }

        private Object pull(InputState input, PinType type) {
            instance.setInputState(input);
            instance.beginPass();
            return interpreter.pullValue(instance, consumer, consumerPin, type, SERVICES);
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

        @Override
        public Window window() { throw new UnsupportedOperationException(); }

        @Override
        public RenderBackend renderBackend() { throw new UnsupportedOperationException(); }

        @Override
        public FontRegistry fonts() { throw new UnsupportedOperationException(); }

        @Override
        public Scene scene() { throw new UnsupportedOperationException(); }

        @Override
        public SystemRegistry systems() { throw new UnsupportedOperationException(); }

        @Override
        public AssetRegistry assets() { throw new UnsupportedOperationException(); }

        @Override
        public Logger logger() { throw new UnsupportedOperationException(); }

        @Override
        public Scheduler scheduler() { throw new UnsupportedOperationException(); }

        @Override
        public BackgroundTasks backgroundTasks() {
            throw new UnsupportedOperationException();
        }

        @Override
        public fr.epistudio.epysia.input.action.InputActions inputActions() {
            return fr.epistudio.epysia.input.action.InputActions.defaults();
        }

        @Override
        public Hud hud() { throw new UnsupportedOperationException(); }

        @Override
        public PostEffects postEffects() { throw new UnsupportedOperationException(); }

        @Override
        public void addPreRenderPass(PreRenderPass pass) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void removePreRenderPass(PreRenderPass pass) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void addRenderSystem(RenderSystem renderSystem) { throw new UnsupportedOperationException(); }

        @Override
        public void removeRenderSystem(RenderSystem renderSystem) { throw new UnsupportedOperationException(); }

        @Override
        public <T extends RenderSystem> T renderSystem(Class<T> type) { throw new UnsupportedOperationException(); }
    }
}
