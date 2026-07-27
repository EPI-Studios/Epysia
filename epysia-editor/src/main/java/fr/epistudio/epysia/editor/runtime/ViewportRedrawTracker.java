package fr.epistudio.epysia.editor.runtime;

import fr.epistudio.epysia.components.Animator;
import fr.epistudio.epysia.components.Camera3D;
import fr.epistudio.epysia.components.MeshRenderer;
import fr.epistudio.epysia.gameobjects.GameObject;
import fr.epistudio.epysia.render.material.LitMaterial;
import fr.epistudio.epysia.render.material.Material;
import fr.epistudio.epysia.scene.Scene;
import org.joml.Matrix4f;

public final class ViewportRedrawTracker {

    private final Matrix4f lastViewProjection = new Matrix4f().zero();
    private long lastSceneModification = -1L;
    private long continuousCheckedModification = -1L;
    private boolean continuousContent;
    private int lastWidth;
    private int lastHeight;
    private static final long MAXIMUM_STALE_NANOS = 100L * 1_000_000L;

    private boolean dirty = true;
    private long lastRedrawNanos;

    public void requestRedraw() {
        dirty = true;
    }

    public boolean shouldRedraw(Camera3D camera, Scene scene, int width, int height, boolean playing) {
        boolean redraw = dirty || playing || staleTooLong() || sizeChanged(width, height)
                || sceneChanged(scene) || cameraMoved(camera) || continuousContent(scene);
        dirty = false;
        if (redraw) {
            lastRedrawNanos = System.nanoTime();
        }
        return redraw;
    }

    private boolean staleTooLong() {
        return System.nanoTime() - lastRedrawNanos >= MAXIMUM_STALE_NANOS;
    }

    private boolean sizeChanged(int width, int height) {
        if (width == lastWidth && height == lastHeight) {
            return false;
        }
        lastWidth = width;
        lastHeight = height;
        return true;
    }

    private boolean sceneChanged(Scene scene) {
        long modification = scene.modificationCount();
        if (modification == lastSceneModification) {
            return false;
        }
        lastSceneModification = modification;
        return true;
    }

    private boolean cameraMoved(Camera3D camera) {
        Matrix4f current = camera.viewProjection();
        if (current.equals(lastViewProjection, 1.0e-7f)) {
            return false;
        }
        lastViewProjection.set(current);
        return true;
    }

    private boolean continuousContent(Scene scene) {
        long modification = scene.modificationCount();
        if (modification != continuousCheckedModification) {
            continuousCheckedModification = modification;
            continuousContent = scanForAnimatedContent(scene);
        }
        return continuousContent;
    }

    private static boolean scanForAnimatedContent(Scene scene) {
        for (GameObject gameObject : scene.gameObjects()) {
            if (gameObject.getComponentOrNull(Animator.class) != null) {
                return true;
            }
            if (hasSurfaceShader(gameObject.getComponentOrNull(MeshRenderer.class))) {
                return true;
            }
        }
        return false;
    }

    private static boolean hasSurfaceShader(MeshRenderer renderer) {
        if (renderer == null) {
            return false;
        }
        for (Material material : renderer.materials()) {
            if (material instanceof LitMaterial lit && !lit.surfaceShaderPath().isEmpty()) {
                return true;
            }
        }
        return false;
    }
}
