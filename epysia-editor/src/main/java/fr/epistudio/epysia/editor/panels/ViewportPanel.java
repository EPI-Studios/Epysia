package fr.epistudio.epysia.editor.panels;

import com.miry.graphics.Texture;
import com.miry.platform.InputConstants;
import com.miry.platform.MiryContext;
import com.miry.platform.MiryHost;
import com.miry.ui.PanelContext;
import com.miry.ui.gizmo.GizmoOverlay3D;
import com.miry.ui.gizmo.GizmoSpace;
import com.miry.ui.input.UiInput;
import com.miry.ui.panels.Panel;
import com.miry.ui.render.UiRenderer;
import com.miry.ui.theme.Icon;
import fr.epistudio.epysia.components.Camera3D;
import fr.epistudio.epysia.components.transforms.Transform3D;
import fr.epistudio.epysia.editor.EditorSceneHost;
import fr.epistudio.epysia.editor.EditorStyle;
import fr.epistudio.epysia.editor.EditorWorld;
import fr.epistudio.epysia.editor.picking.EditorPicker;
import fr.epistudio.epysia.gameobjects.GameObject;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.Optional;

public final class ViewportPanel extends Panel {

    private static final String TITLE = "Viewport";
    private static final int VIEWPORT_INSET = 6;
    private static final int CORNER_RADIUS = 4;
    private static final int OVERLAY_FONT_BASELINE_OFFSET = 16;
    private static final int AXIS_GIZMO_SIZE = 28;
    private static final int AXIS_GIZMO_MARGIN = 14;
    private static final int AXIS_LINE_THICKNESS = 2;
    private static final int TOOLBAR_BUTTON_SIZE = 26;
    private static final int TOOLBAR_BUTTON_GAP = 2;
    private static final int TOOLBAR_GROUP_PADDING = 4;
    private static final int TOOLBAR_GROUP_RADIUS = 6;
    private static final int TOOLBAR_GROUP_GAP = 8;
    private static final int TOOLBAR_ICON_SIZE = 16;
    private static final int TOOLBAR_MARGIN = 12;
    private static final int TOOLBAR_TOOL_COUNT = 4;

    private final EditorSceneHost sceneHost;
    private final EditorWorld world;
    private final Vector3f axisScratch = new Vector3f();
    private final Matrix4f viewMatrixScratch = new Matrix4f();
    private final Vector3f gizmoPosition = new Vector3f();
    private final Vector3f gizmoEulerDegrees = new Vector3f();
    private final Vector3f gizmoScale = new Vector3f(1.0f);
    private final Quaternionf gizmoRotation = new Quaternionf();
    private final Vector3f cameraPositionScratch = new Vector3f();
    private final EditorPicker picker = new EditorPicker();
    private GizmoOverlay3D gizmoOverlay;
    private GameObject lastGizmoTarget;
    private boolean previousKeyQDown;
    private boolean previousKeyWDown;
    private boolean previousKeyEDown;
    private boolean previousKeyRDown;
    private boolean previousKeyTDown;
    private int lastRequestedWidth;
    private int lastRequestedHeight;
    private int viewportRectX;
    private int viewportRectY;

    public ViewportPanel(EditorSceneHost sceneHost, EditorWorld world) {
        super(TITLE);
        this.sceneHost = sceneHost;
        this.world = world;
    }

    public int lastRequestedWidth() {
        return lastRequestedWidth;
    }

    public int lastRequestedHeight() {
        return lastRequestedHeight;
    }

    public int viewportRectX() {
        return viewportRectX;
    }

    public int viewportRectY() {
        return viewportRectY;
    }

    public void tickKeyboard(MiryHost host) {
        if (gizmoOverlay == null) {
            return;
        }
        boolean qDown = host.isKeyDown(InputConstants.KEY_Q);
        boolean wDown = host.isKeyDown(InputConstants.KEY_W);
        boolean eDown = host.isKeyDown(InputConstants.KEY_E);
        boolean rDown = host.isKeyDown(InputConstants.KEY_R);
        boolean tDown = host.isKeyDown(InputConstants.KEY_T);
        if (qDown && !previousKeyQDown) gizmoOverlay.setMode(GizmoOverlay3D.Mode.NONE);
        if (wDown && !previousKeyWDown) gizmoOverlay.setMode(GizmoOverlay3D.Mode.TRANSLATE);
        if (eDown && !previousKeyEDown) gizmoOverlay.setMode(GizmoOverlay3D.Mode.ROTATE);
        if (rDown && !previousKeyRDown) gizmoOverlay.setMode(GizmoOverlay3D.Mode.SCALE);
        if (tDown && !previousKeyTDown) toggleGizmoSpace();
        previousKeyQDown = qDown;
        previousKeyWDown = wDown;
        previousKeyEDown = eDown;
        previousKeyRDown = rDown;
        previousKeyTDown = tDown;
    }

    private void toggleGizmoSpace() {
        GizmoSpace next = gizmoOverlay.gizmoSpace() == GizmoSpace.WORLD ? GizmoSpace.LOCAL : GizmoSpace.WORLD;
        gizmoOverlay.setGizmoSpace(next);
    }

    @Override
    public void render(PanelContext context) {
        UiRenderer renderer = context.renderer();
        viewportRectX = context.x() + VIEWPORT_INSET;
        viewportRectY = context.y() + VIEWPORT_INSET;
        int viewportWidth = context.width() - VIEWPORT_INSET * 2;
        int viewportHeight = context.height() - VIEWPORT_INSET * 2;
        lastRequestedWidth = viewportWidth;
        lastRequestedHeight = viewportHeight;
        renderer.drawRoundedRect(viewportRectX, viewportRectY, viewportWidth, viewportHeight, CORNER_RADIUS, EditorStyle.COLOR_VIEWPORT_BG);
        renderSceneTexture(renderer, viewportWidth, viewportHeight);
        updateAndRenderGizmo(context, viewportWidth, viewportHeight);
        handleViewportPicking(context, viewportWidth, viewportHeight);
        renderHelpOverlay(renderer, viewportRectX + 14, viewportRectY + 22);
        renderAxisGizmo(renderer, viewportRectX + viewportWidth - AXIS_GIZMO_SIZE - AXIS_GIZMO_MARGIN,
                viewportRectY + AXIS_GIZMO_MARGIN);
        renderGizmoToolbar(context, viewportRectX + TOOLBAR_MARGIN,
                viewportRectY + TOOLBAR_MARGIN);
    }

    private void renderSceneTexture(UiRenderer renderer, int viewportWidth, int viewportHeight) {
        Texture sceneTexture = sceneHost.colorTextureForMiry();
        if (sceneTexture == null) {
            return;
        }
        renderer.drawTexturedRect(sceneTexture, viewportRectX, viewportRectY, viewportWidth, viewportHeight,
                0.0f, 1.0f, 1.0f, 0.0f, 0xFFFFFFFF);
    }

    private void updateAndRenderGizmo(PanelContext context, int viewportWidth, int viewportHeight) {
        Optional<GameObject> selected = world.selected();
        if (selected.isEmpty()) {
            return;
        }
        Transform3D transform = selected.get().getComponent(Transform3D.class).orElse(null);
        if (transform == null) {
            return;
        }
        ensureGizmoOverlay();
        syncGizmoBuffersFromTransform(selected.get(), transform);
        Matrix4f viewProjection = computeViewProjection(viewportWidth, viewportHeight);
        Vector3f cameraPosition = sceneHost.cameraTransform().position();
        cameraPositionScratch.set(cameraPosition);
        MiryHost host = MiryContext.host();
        float scaleX = host.getFramebufferScaleX();
        float scaleY = host.getFramebufferScaleY();
        gizmoOverlay.updateInput(context.ui().input(), viewProjection, cameraPositionScratch,
                viewportRectX, viewportRectY, viewportWidth, viewportHeight,
                scaleX, scaleY, gizmoPosition, gizmoEulerDegrees, gizmoScale);
        applyGizmoBuffersToTransform(transform);
        renderGizmoToTexture(context.renderer(), viewProjection, viewportWidth, viewportHeight, scaleX, scaleY);
    }

    private void ensureGizmoOverlay() {
        if (gizmoOverlay == null) {
            gizmoOverlay = new GizmoOverlay3D();
            gizmoOverlay.setMode(GizmoOverlay3D.Mode.TRANSLATE);
        }
    }

    private void syncGizmoBuffersFromTransform(GameObject target, Transform3D transform) {
        if (target != lastGizmoTarget || gizmoOverlay == null || !gizmoOverlay.dragging()) {
            gizmoPosition.set(transform.position());
            transform.rotation().getEulerAnglesYXZ(gizmoEulerDegrees);
            float pitchDegrees = (float) Math.toDegrees(gizmoEulerDegrees.x);
            float yawDegrees = (float) Math.toDegrees(gizmoEulerDegrees.y);
            float rollDegrees = (float) Math.toDegrees(gizmoEulerDegrees.z);
            gizmoEulerDegrees.set(pitchDegrees, yawDegrees, rollDegrees);
            gizmoScale.set(transform.scale());
            lastGizmoTarget = target;
        }
    }

    private void applyGizmoBuffersToTransform(Transform3D transform) {
        transform.setPosition(gizmoPosition.x, gizmoPosition.y, gizmoPosition.z);
        float pitchRadians = (float) Math.toRadians(gizmoEulerDegrees.x);
        float yawRadians = (float) Math.toRadians(gizmoEulerDegrees.y);
        float rollRadians = (float) Math.toRadians(gizmoEulerDegrees.z);
        gizmoRotation.identity().rotateY(yawRadians).rotateX(pitchRadians).rotateZ(rollRadians);
        transform.setRotation(gizmoRotation);
        transform.setUniformScale(gizmoScale.x);
    }

    private Matrix4f computeViewProjection(int viewportWidth, int viewportHeight) {
        Camera3D camera = sceneHost.camera();
        float aspect = viewportHeight <= 0 ? 1.0f : (float) viewportWidth / viewportHeight;
        camera.setAspectRatio(aspect);
        return camera.viewProjection();
    }

    private void renderGizmoToTexture(UiRenderer renderer, Matrix4f viewProjection,
                                      int viewportWidth, int viewportHeight, float scaleX, float scaleY) {
        int pixelWidth = Math.max(1, Math.round(viewportWidth * scaleX));
        int pixelHeight = Math.max(1, Math.round(viewportHeight * scaleY));
        gizmoOverlay.renderToTexture(pixelWidth, pixelHeight, viewProjection, cameraPositionScratch, gizmoPosition);
        Texture gizmoTexture = gizmoOverlay.texture();
        if (gizmoTexture == null) {
            return;
        }
        renderer.drawTexturedRect(gizmoTexture, viewportRectX, viewportRectY, viewportWidth, viewportHeight,
                0.0f, 1.0f, 1.0f, 0.0f, 0xFFFFFFFF);
    }

    private void handleViewportPicking(PanelContext context, int viewportWidth, int viewportHeight) {
        if (gizmoOverlay != null && gizmoOverlay.dragging()) {
            return;
        }
        if (!context.ui().input().mousePressed()) {
            return;
        }
        int cursorX = Math.round(context.ui().input().mousePos().x);
        int cursorY = Math.round(context.ui().input().mousePos().y);
        if (cursorX < viewportRectX || cursorX >= viewportRectX + viewportWidth
                || cursorY < viewportRectY || cursorY >= viewportRectY + viewportHeight) {
            return;
        }
        Matrix4f viewProjection = computeViewProjection(viewportWidth, viewportHeight);
        int hitIndex = picker.pickAt(cursorX, cursorY,
                viewportRectX, viewportRectY, viewportWidth, viewportHeight,
                viewProjection, world.objects());
        applyPickingSelection(context, hitIndex);
    }

    private void applyPickingSelection(PanelContext context, int hitIndex) {
        boolean shift = context.ui().input().shiftDown();
        boolean ctrl = context.ui().input().ctrlDown();
        if (hitIndex < 0) {
            if (!shift && !ctrl) {
                world.clearSelection();
            }
            return;
        }
        if (ctrl) {
            world.toggleSelection(hitIndex);
        } else if (shift) {
            world.addToSelection(hitIndex);
        } else {
            world.selectIndex(hitIndex);
        }
    }

    private void renderGizmoToolbar(PanelContext context, int originX, int originY) {
        if (gizmoOverlay == null) {
            return;
        }
        int toolsGroupWidth = TOOLBAR_GROUP_PADDING * 2
                + TOOLBAR_BUTTON_SIZE * TOOLBAR_TOOL_COUNT
                + TOOLBAR_BUTTON_GAP * (TOOLBAR_TOOL_COUNT - 1);
        renderToolsGroup(context, originX, originY, toolsGroupWidth);
        int spaceGroupX = originX + toolsGroupWidth + TOOLBAR_GROUP_GAP;
        renderSpaceToggle(context.renderer(), context.ui().input(), spaceGroupX, originY);
    }

    private void renderToolsGroup(PanelContext context, int originX, int originY, int groupWidth) {
        UiRenderer renderer = context.renderer();
        int groupHeight = TOOLBAR_GROUP_PADDING * 2 + TOOLBAR_BUTTON_SIZE;
        renderer.drawRoundedRect(originX, originY, groupWidth, groupHeight, TOOLBAR_GROUP_RADIUS,
                withAlpha(EditorStyle.COLOR_PANEL_BG, 0xD0));
        GizmoOverlay3D.Mode mode = gizmoOverlay.mode();
        int buttonsTop = originY + TOOLBAR_GROUP_PADDING;
        int x0 = originX + TOOLBAR_GROUP_PADDING;
        int step = TOOLBAR_BUTTON_SIZE + TOOLBAR_BUTTON_GAP;
        renderToolButton(context, x0, buttonsTop, Icon.SELECT, mode == GizmoOverlay3D.Mode.NONE, GizmoOverlay3D.Mode.NONE);
        renderToolButton(context, x0 + step, buttonsTop, Icon.MOVE, mode == GizmoOverlay3D.Mode.TRANSLATE, GizmoOverlay3D.Mode.TRANSLATE);
        renderToolButton(context, x0 + step * 2, buttonsTop, Icon.ROTATE, mode == GizmoOverlay3D.Mode.ROTATE, GizmoOverlay3D.Mode.ROTATE);
        renderToolButton(context, x0 + step * 3, buttonsTop, Icon.SCALE, mode == GizmoOverlay3D.Mode.SCALE, GizmoOverlay3D.Mode.SCALE);
    }

    private void renderToolButton(PanelContext context, int x, int y, Icon icon, boolean active, GizmoOverlay3D.Mode targetMode) {
        UiRenderer renderer = context.renderer();
        UiInput input = context.ui().input();
        boolean hovered = isHovered(input, x, y, TOOLBAR_BUTTON_SIZE, TOOLBAR_BUTTON_SIZE);
        int background = backgroundForToolState(active, hovered);
        int iconColor = iconColorForToolState(active, hovered);
        if (background != 0) {
            renderer.drawRoundedRect(x, y, TOOLBAR_BUTTON_SIZE, TOOLBAR_BUTTON_SIZE, TOOLBAR_GROUP_RADIUS - 2, background);
        }
        int iconX = x + (TOOLBAR_BUTTON_SIZE - TOOLBAR_ICON_SIZE) / 2;
        int iconY = y + (TOOLBAR_BUTTON_SIZE - TOOLBAR_ICON_SIZE) / 2;
        context.ui().theme().icons.draw(renderer, icon, iconX, iconY, TOOLBAR_ICON_SIZE, iconColor);
        if (hovered && input.mousePressed()) {
            gizmoOverlay.setMode(targetMode);
        }
    }

    private void renderSpaceToggle(UiRenderer renderer, UiInput input, int originX, int originY) {
        int width = 52;
        int height = TOOLBAR_GROUP_PADDING * 2 + TOOLBAR_BUTTON_SIZE;
        renderer.drawRoundedRect(originX, originY, width, height, TOOLBAR_GROUP_RADIUS,
                withAlpha(EditorStyle.COLOR_PANEL_BG, 0xD0));
        boolean hovered = isHovered(input, originX, originY, width, height);
        String label = gizmoOverlay.gizmoSpace() == GizmoSpace.WORLD ? "World" : "Local";
        int textColor = hovered ? EditorStyle.COLOR_TEXT_PRIMARY : EditorStyle.COLOR_TEXT_MUTED;
        int textWidth = Math.round(renderer.measureText(label));
        renderer.drawText(label, originX + (width - textWidth) / 2, originY + height / 2 + 5, textColor);
        if (hovered && input.mousePressed()) {
            toggleGizmoSpace();
        }
    }

    private int backgroundForToolState(boolean active, boolean hovered) {
        if (active) {
            return withAlpha(EditorStyle.COLOR_WIDGET_HOVER, 0xF2);
        }
        if (hovered) {
            return withAlpha(EditorStyle.COLOR_WIDGET_HOVER, 0x99);
        }
        return 0;
    }

    private int iconColorForToolState(boolean active, boolean hovered) {
        if (active) {
            return EditorStyle.LEAF_HEADER_ACCENT;
        }
        if (hovered) {
            return EditorStyle.COLOR_TEXT_PRIMARY;
        }
        return EditorStyle.COLOR_TEXT_MUTED;
    }

    private static boolean isHovered(UiInput input, int x, int y, int width, int height) {
        if (input == null) {
            return false;
        }
        float mouseX = input.mousePos().x;
        float mouseY = input.mousePos().y;
        return mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + height;
    }

    private static int withAlpha(int argb, int alpha) {
        return (argb & 0x00FFFFFF) | ((alpha & 0xFF) << 24);
    }

    private void renderHelpOverlay(UiRenderer renderer, int x, int baselineY) {
        renderer.drawText("RMB look | WASD move | Alt+LMB orbit | F frame | Q/W/E/R gizmo | T space", x, baselineY, 0x80E0E3E8);
        if (world.isPlaying()) {
            renderer.drawText("PLAYING", x, baselineY + OVERLAY_FONT_BASELINE_OFFSET, EditorStyle.COLOR_PLAY);
        }
    }

    private void renderAxisGizmo(UiRenderer renderer, int gizmoX, int gizmoY) {
        int half = AXIS_GIZMO_SIZE / 2;
        int centerX = gizmoX + half;
        int centerY = gizmoY + half;
        renderer.drawCircle(centerX, centerY, half + 4, 0x60101218);
        sceneHost.cameraTransform().localMatrix().invert(viewMatrixScratch);
        drawAxisLine(renderer, centerX, centerY, 1.0f, 0.0f, 0.0f, half - 2, EditorStyle.COLOR_AXIS_X);
        drawAxisLine(renderer, centerX, centerY, 0.0f, 1.0f, 0.0f, half - 2, EditorStyle.COLOR_AXIS_Y);
        drawAxisLine(renderer, centerX, centerY, 0.0f, 0.0f, 1.0f, half - 2, EditorStyle.COLOR_AXIS_Z);
    }

    private void drawAxisLine(UiRenderer renderer, int centerX, int centerY,
                              float worldX, float worldY, float worldZ, int length, int color) {
        axisScratch.set(worldX, worldY, worldZ);
        viewMatrixScratch.transformDirection(axisScratch);
        float screenX = axisScratch.x;
        float screenY = -axisScratch.y;
        float magnitude = (float) Math.sqrt(screenX * screenX + screenY * screenY);
        if (magnitude < 0.0001f) {
            return;
        }
        screenX /= magnitude;
        screenY /= magnitude;
        int endX = centerX + Math.round(screenX * length);
        int endY = centerY + Math.round(screenY * length);
        renderer.drawLine(centerX, centerY, endX, endY, AXIS_LINE_THICKNESS, color);
        renderer.drawCircle(endX, endY, 3, color);
    }
}
