package fr.epistudio.epysia.render;

import fr.epistudio.epysia.render.backend.DrawCommand;
import fr.epistudio.epysia.render.backend.PassClear;
import fr.epistudio.epysia.render.backend.RenderBackend;
import fr.epistudio.epysia.render.backend.RenderTargetHandle;
import fr.epistudio.epysia.scene.Scene;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

public final class EpysiaFrameDriver implements StageConfigurer {

    private final RenderBackend renderBackend;
    private final List<RenderSystem> renderSystems = new ArrayList<>();
    private final Frame frame = new Frame();
    private final Map<Stage, StageBinding> stageBindings = new EnumMap<>(Stage.class);
    private final PassClear defaultClear;
    private boolean initialized;

    public EpysiaFrameDriver(RenderBackend renderBackend, PassClear defaultClear) {
        this.renderBackend = renderBackend;
        this.defaultClear = defaultClear;
        applyDefaultStageBindings();
    }

    private void applyDefaultStageBindings() {
        StageBinding screenWithClear = new StageBinding(RenderTargetHandle.SCREEN, defaultClear);
        StageBinding screenNoClear = new StageBinding(RenderTargetHandle.SCREEN, PassClear.none());
        stageBindings.put(Stage.PRE_3D, screenWithClear);
        stageBindings.put(Stage.OPAQUE_3D, screenWithClear);
        stageBindings.put(Stage.TRANSPARENT_3D, screenNoClear);
        stageBindings.put(Stage.WORLD_2D, screenNoClear);
        stageBindings.put(Stage.UI, screenNoClear);
        stageBindings.put(Stage.POST, screenNoClear);
    }

    public void addRenderSystem(RenderSystem renderSystem) {
        renderSystems.add(renderSystem);
    }

    public void initializeRenderSystems() {
        for (RenderSystem system : renderSystems) {
            system.initialize(renderBackend, this);
        }
        initialized = true;
    }

    public void onResize(int width, int height) {
        if (!initialized) {
            return;
        }
        renderBackend.onViewportResize(width, height);
        for (RenderSystem system : renderSystems) {
            system.onResize(renderBackend, this, width, height);
        }
    }

    public void renderFrame(Scene scene, float interpolationAlpha) {
        collectFrame(scene, interpolationAlpha);
        renderBackend.beginFrame();
        drainStages();
        renderBackend.endFrame();
    }

    public void shutdownRenderSystems() {
        for (RenderSystem system : renderSystems) {
            system.shutdown(renderBackend);
        }
        initialized = false;
    }

    @Override
    public void bindStageTarget(Stage stage, RenderTargetHandle target, PassClear clear) {
        stageBindings.put(stage, new StageBinding(target, clear));
    }

    private void collectFrame(Scene scene, float interpolationAlpha) {
        frame.reset();
        for (RenderSystem system : renderSystems) {
            system.collect(scene, frame, interpolationAlpha);
        }
    }

    private void drainStages() {
        boolean screenWasOpened = false;
        for (Stage stage : Stage.values()) {
            List<DrawCommand> commands = frame.commandsFor(stage);
            if (commands.isEmpty()) {
                continue;
            }
            StageBinding binding = stageBindings.get(stage);
            renderBackend.beginProfileSection(stage.name());
            renderBackend.beginPass(binding.target(), binding.clear());
            for (DrawCommand command : commands) {
                renderBackend.execute(command);
            }
            renderBackend.endPass();
            renderBackend.endProfileSection();
            if (binding.target().id() == 0L) {
                screenWasOpened = true;
            }
        }
        if (!screenWasOpened) {
            renderBackend.beginPass(RenderTargetHandle.SCREEN, defaultClear);
            renderBackend.endPass();
        }
    }

    private record StageBinding(RenderTargetHandle target, PassClear clear) {
    }
}
