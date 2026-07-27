package fr.epistudio.epysia.editor.gizmo;

import fr.epistudio.epysia.gameobjects.GameObject;
import fr.epistudio.epysia.render.backend.TextureHandle;
import fr.epistudio.epysia.render.mesh.SilhouettePass;
import fr.epistudio.epysia.render.opengl.OpenGlRenderBackend;
import fr.epistudio.epysia.render.shader.ShaderLoader;
import org.joml.Matrix4f;
import org.joml.Vector3f;

import java.util.List;
import java.util.Optional;

public final class SelectionSilhouetteOverlay implements AutoCloseable {

    private static final Vector3f ACCENT_COLOR = new Vector3f(0.16f, 0.62f, 1.0f);
    private static final float OUTLINE_RADIUS_PIXELS = 2.5f;
    private static final float INTERIOR_ALPHA = 0.08f;

    private final SilhouettePass pass = new SilhouettePass(ShaderLoader.autoDetect());
    private int textureId;

    public boolean render(List<GameObject> selectedObjects, Matrix4f viewProjection,
                          int pixelWidth, int pixelHeight, float thicknessScale,
                          SilhouettePass.JointPaletteSource palettes, OpenGlRenderBackend backend) {
        List<GameObject> meshObjects = SelectionHierarchy.meshObjectsUnder(selectedObjects);
        if (meshObjects.isEmpty()) {
            textureId = 0;
            return false;
        }
        float radius = Math.clamp(OUTLINE_RADIUS_PIXELS * thicknessScale, 1.0f,
                SilhouettePass.MAXIMUM_OUTLINE_RADIUS);
        Optional<TextureHandle> outline = pass.render(meshObjects,
                viewProjection, pixelWidth, pixelHeight, radius, ACCENT_COLOR, INTERIOR_ALPHA,
                palettes, backend);
        textureId = outline.map(backend::glTextureName).orElse(0);
        return textureId != 0;
    }

    public int textureId() {
        return textureId;
    }

    @Override
    public void close() {
        pass.shutdown();
        textureId = 0;
    }
}
