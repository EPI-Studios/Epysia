package fr.epistudio.epysia;

import fr.epistudio.epysia.input.InputState;
import fr.epistudio.epysia.input.MutableInputState;
import fr.epistudio.epysia.profiling.FrameProfiler;
import fr.epistudio.epysia.profiling.ProfileNode;
import fr.epistudio.epysia.render.backend.NullRenderBackend;
import fr.epistudio.epysia.scene.Scene;
import fr.epistudio.epysia.window.Window;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EngineLifecycleTest {

    private record RecordingSystem(String name, List<String> shutdownOrder) implements GameSystem {

        @Override
        public void update(Scene scene, InputState input, float deltaTimeSeconds) {
        }

        @Override
        public void shutdown() {
            shutdownOrder.add(name);
        }
    }

    @Test
    void systemsShutDownInTheReverseOfTheOrderTheyStartedIn() {
        List<String> order = new ArrayList<>();
        Window window = Window.headless("shutdown order", 1, 1);
        NullRenderBackend backend = new NullRenderBackend();
        EpysiaEngine engine = new EpysiaEngine(window, backend);
        engine.addSystem(new RecordingSystem("first", order));
        engine.addSystem(new RecordingSystem("second", order));
        engine.addSystem(new RecordingSystem("third", order));
        backend.initialize(window);
        engine.initialize();

        engine.shutdown();

        assertEquals(List.of("third", "second", "first"), order,
                "a system that started later may depend on an earlier one, so it must stop first");
    }

    @Test
    void everySystemKeepsItsOwnProfilerSection() {
        Window window = Window.headless("profiler sections", 1, 1);
        NullRenderBackend backend = new NullRenderBackend();
        EpysiaEngine engine = new EpysiaEngine(window, backend);
        Scene scene = new Scene("sections");
        engine.addScene(scene);
        engine.setActiveScene(scene);
        engine.addSystem(new RecordingSystem("first", new ArrayList<>()));
        backend.initialize(window);
        engine.initialize();

        engine.tick(new MutableInputState(), 1.0f / 60.0f);
        engine.profiler().publishFrame();

        assertTrue(engine.profiler().sections().containsKey("system/RecordingSystem"),
                "the precomputed section names must stay aligned with the systems they time");
        ProfileNode tick = engine.profiler().frame().roots().stream()
                .filter(node -> node.name().equals(FrameProfiler.TICK_SECTION))
                .findFirst()
                .orElseThrow();
        assertTrue(tick.children().stream().anyMatch(child -> child.name().equals("system/RecordingSystem")),
                "a game system belongs under the tick that ran it, not beside it");
        engine.shutdown();
    }
}
