package fr.epistudio.epysia.editor.ui;

import fr.epistudio.epysia.components.transforms.Transform2D;
import fr.epistudio.epysia.editor.command.EditorCommand;
import fr.epistudio.epysia.editor.command.builtin.SetPivot2DCommand;
import imgui.ImDrawList;
import imgui.ImGui;
import org.joml.Matrix3x2f;
import org.joml.Vector2f;

import java.util.Optional;

public final class PivotHandle {

    public interface WorldToScreen {
        Vector2f screenOf(float worldX, float worldY);
    }

    private static final float GRAB_RADIUS_PIXELS = 11.0f;
    private static final float HANDLE_RADIUS_PIXELS = 6.0f;
    private static final float CROSS_REACH_PIXELS = 13.0f;
    private static final float HANDLE_THICKNESS = 1.6f;
    private static final float BOUNDS_THICKNESS = 1.0f;
    private static final float SNAP_TOLERANCE_FRACTION = 0.15f;
    private static final float MINIMUM_SCALE = 1.0e-5f;
    private static final int COLOR_HANDLE = 0xFF3FC8FF;
    private static final int COLOR_HANDLE_ACTIVE = 0xFF4FE0FF;
    private static final int COLOR_BOUNDS = 0x60FFFFFF;

    private Optional<Transform2D> dragged = Optional.empty();
    private final Vector2f dragStartPivot = new Vector2f();
    private final Vector2f dragStartPosition = new Vector2f();

    public boolean busy() {
        return dragged.isPresent();
    }

    public Optional<EditorCommand> render(Transform2D transform, Vector2f halfExtents,
                                          WorldToScreen projection, Vector2f mouseWorld,
                                          boolean viewportHovered) {
        Vector2f handleScreen = projection.screenOf(transform.position().x, transform.position().y);
        drawBounds(transform, halfExtents, projection);
        boolean active = dragged.isPresent();
        drawHandle(ImGui.getWindowDrawList(), handleScreen, active || withinGrab(handleScreen));
        if (!active) {
            beginDragIfGrabbed(transform, handleScreen, viewportHovered);
            return Optional.empty();
        }
        if (ImGui.isMouseDown(0)) {
            moveTo(transform, halfExtents, mouseWorld);
            return Optional.empty();
        }
        return finishDrag(transform);
    }

    private void beginDragIfGrabbed(Transform2D transform, Vector2f handleScreen, boolean viewportHovered) {
        if (!viewportHovered || !withinGrab(handleScreen) || !ImGui.isMouseClicked(0)) {
            return;
        }
        dragged = Optional.of(transform);
        dragStartPivot.set(transform.pivot());
        dragStartPosition.set(transform.position());
    }

    private static boolean withinGrab(Vector2f handleScreen) {
        float deltaX = ImGui.getMousePosX() - handleScreen.x;
        float deltaY = ImGui.getMousePosY() - handleScreen.y;
        return deltaX * deltaX + deltaY * deltaY <= GRAB_RADIUS_PIXELS * GRAB_RADIUS_PIXELS;
    }

    private void moveTo(Transform2D transform, Vector2f halfExtents, Vector2f mouseWorld) {
        Optional<Matrix3x2f> basis = invertedBasis(transform);
        if (basis.isEmpty()) {
            return;
        }
        Vector2f delta = basis.get().transformDirection(
                new Vector2f(mouseWorld.x - transform.position().x, mouseWorld.y - transform.position().y));
        Vector2f pivot = new Vector2f(transform.pivot()).add(delta);
        snap(pivot, halfExtents);
        Vector2f offset = new Vector2f(pivot).sub(transform.pivot());
        Vector2f worldOffset = basisOf(transform).transformDirection(offset);
        transform.setPivot(pivot.x, pivot.y);
        transform.setPosition(transform.position().x + worldOffset.x, transform.position().y + worldOffset.y);
        transform.markDirty();
    }

    private static void snap(Vector2f pivot, Vector2f halfExtents) {
        if (!ImGui.getIO().getKeyShift()) {
            return;
        }
        pivot.set(snapAxis(pivot.x, halfExtents.x), snapAxis(pivot.y, halfExtents.y));
    }

    private static float snapAxis(float value, float halfExtent) {
        if (halfExtent <= 0.0f) {
            return value;
        }
        float tolerance = halfExtent * SNAP_TOLERANCE_FRACTION;
        for (float candidate : new float[]{-halfExtent, 0.0f, halfExtent}) {
            if (Math.abs(value - candidate) <= tolerance) {
                return candidate;
            }
        }
        return value;
    }

    private static Matrix3x2f basisOf(Transform2D transform) {
        return new Matrix3x2f()
                .rotate(transform.rotationRadians())
                .scale(transform.scale().x, transform.scale().y);
    }

    private static Optional<Matrix3x2f> invertedBasis(Transform2D transform) {
        if (Math.abs(transform.scale().x) < MINIMUM_SCALE || Math.abs(transform.scale().y) < MINIMUM_SCALE) {
            return Optional.empty();
        }
        return Optional.of(basisOf(transform).invert());
    }

    private Optional<EditorCommand> finishDrag(Transform2D transform) {
        dragged = Optional.empty();
        Vector2f afterPivot = new Vector2f(transform.pivot());
        Vector2f afterPosition = new Vector2f(transform.position());
        if (afterPivot.equals(dragStartPivot) && afterPosition.equals(dragStartPosition)) {
            return Optional.empty();
        }
        transform.setPivot(dragStartPivot.x, dragStartPivot.y);
        transform.setPosition(dragStartPosition.x, dragStartPosition.y);
        transform.markDirty();
        return Optional.of(new SetPivot2DCommand(transform,
                dragStartPivot, dragStartPosition, afterPivot, afterPosition));
    }

    private static void drawHandle(ImDrawList drawList, Vector2f screen, boolean highlighted) {
        int color = highlighted ? COLOR_HANDLE_ACTIVE : COLOR_HANDLE;
        drawList.addCircle(screen.x, screen.y, HANDLE_RADIUS_PIXELS, color, 0, HANDLE_THICKNESS);
        drawList.addCircleFilled(screen.x, screen.y, HANDLE_THICKNESS, color);
        drawList.addLine(screen.x - CROSS_REACH_PIXELS, screen.y,
                screen.x - HANDLE_RADIUS_PIXELS, screen.y, color, HANDLE_THICKNESS);
        drawList.addLine(screen.x + HANDLE_RADIUS_PIXELS, screen.y,
                screen.x + CROSS_REACH_PIXELS, screen.y, color, HANDLE_THICKNESS);
        drawList.addLine(screen.x, screen.y - CROSS_REACH_PIXELS,
                screen.x, screen.y - HANDLE_RADIUS_PIXELS, color, HANDLE_THICKNESS);
        drawList.addLine(screen.x, screen.y + HANDLE_RADIUS_PIXELS,
                screen.x, screen.y + CROSS_REACH_PIXELS, color, HANDLE_THICKNESS);
    }

    private static void drawBounds(Transform2D transform, Vector2f halfExtents, WorldToScreen projection) {
        if (halfExtents.x <= 0.0f || halfExtents.y <= 0.0f) {
            return;
        }
        Matrix3x2f matrix = transform.localMatrix();
        Vector2f cornerA = cornerScreen(matrix, projection, -halfExtents.x, -halfExtents.y);
        Vector2f cornerB = cornerScreen(matrix, projection, halfExtents.x, -halfExtents.y);
        Vector2f cornerC = cornerScreen(matrix, projection, halfExtents.x, halfExtents.y);
        Vector2f cornerD = cornerScreen(matrix, projection, -halfExtents.x, halfExtents.y);
        ImGui.getWindowDrawList().addQuad(cornerA.x, cornerA.y, cornerB.x, cornerB.y,
                cornerC.x, cornerC.y, cornerD.x, cornerD.y, COLOR_BOUNDS, BOUNDS_THICKNESS);
    }

    private static Vector2f cornerScreen(Matrix3x2f matrix, WorldToScreen projection,
                                         float localX, float localY) {
        Vector2f world = matrix.transformPosition(new Vector2f(localX, localY));
        return projection.screenOf(world.x, world.y);
    }
}
