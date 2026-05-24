package fr.epistudio.epysia.editor.panels;

import com.miry.ui.PanelContext;
import com.miry.ui.panels.Panel;
import com.miry.ui.widgets.StatusBar;
import fr.epistudio.epysia.editor.EditorSceneHost;
import fr.epistudio.epysia.editor.EditorWorld;
import org.joml.Vector3f;

public final class StatusBarPanel extends Panel {

    private static final String TITLE = "StatusBar";

    private final EditorWorld world;
    private final EditorSceneHost sceneHost;
    private final StatusBar statusBar = new StatusBar();
    private float fpsAccumulator;
    private int fpsFrameCount;
    private float displayedFps;

    public StatusBarPanel(EditorWorld world, EditorSceneHost sceneHost) {
        super(TITLE);
        this.world = world;
        this.sceneHost = sceneHost;
    }

    public void tickFps(float deltaTimeSeconds) {
        fpsAccumulator += deltaTimeSeconds;
        fpsFrameCount++;
        if (fpsAccumulator >= 0.5f) {
            displayedFps = fpsFrameCount / fpsAccumulator;
            fpsAccumulator = 0.0f;
            fpsFrameCount = 0;
        }
    }

    @Override
    public void render(PanelContext context) {
        statusBar.clear();
        statusBar.addLeft(String.format("FPS %5.1f", displayedFps));
        statusBar.addLeft(String.format("viewport %dx%d", sceneHost.currentWidth(), sceneHost.currentHeight()));
        Vector3f position = sceneHost.cameraTransform().position();
        statusBar.addCenter(String.format("cam %.2f %.2f %.2f", position.x, position.y, position.z));
        statusBar.addRight(world.objects().size() + " objects");
        statusBar.addRight(world.isPlaying() ? "PLAY" : "EDIT");
        statusBar.render(context.renderer(), context.ui().theme(),
                context.x(), context.y(), context.width(), context.height());
    }
}
