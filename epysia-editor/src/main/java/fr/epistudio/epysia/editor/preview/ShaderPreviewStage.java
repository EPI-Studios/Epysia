package fr.epistudio.epysia.editor.preview;

import fr.epistudio.epysia.EngineServices;
import fr.epistudio.epysia.components.Camera3D;
import fr.epistudio.epysia.components.DirectionalLight;
import fr.epistudio.epysia.components.MeshRenderer;
import fr.epistudio.epysia.components.transforms.Transform3D;
import fr.epistudio.epysia.gameobjects.GameObject;
import fr.epistudio.epysia.render.material.LitMaterial;
import fr.epistudio.epysia.scene.Scene;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class ShaderPreviewStage {

    private static final float MINIMUM_PITCH = -1.45f;
    private static final float MAXIMUM_PITCH = 1.45f;
    private static final float MINIMUM_DISTANCE = 0.8f;
    private static final float MAXIMUM_DISTANCE = 8.0f;
    private static final float FLAT_DISTANCE = 1.21f;
    private static final float FIELD_OF_VIEW_DEGREES = 40.0f;
    private static final float ZOOM_STEP = 0.2f;

    private final Scene scene;
    private final MeshRenderer renderer = new MeshRenderer();
    private final Map<String, LitMaterial> materialsByShaderPath = new HashMap<>();
    private LitMaterial material = materialFor("");
    private final Transform3D cameraTransform = new Transform3D();
    private final Camera3D camera = new Camera3D()
            .setFieldOfViewDegrees(FIELD_OF_VIEW_DEGREES).setNearFar(0.05f, 50.0f);
    private final boolean orbitEnabled;
    private String meshPath;
    private float yawRadians = 0.7f;
    private float pitchRadians = 0.5f;
    private float distance = 2.4f;

    public ShaderPreviewStage(String name, String meshPath, boolean orbitEnabled) {
        this.scene = new Scene(name);
        this.meshPath = meshPath;
        this.orbitEnabled = orbitEnabled;
        renderer.setMeshPath(meshPath);
        renderer.setMaterial(material);
        scene.addGameObject(buildTargetObject());
        scene.addGameObject(buildSunObject());
        scene.addGameObject(buildCameraObject());
        scene.advanceTick();
        applyCamera();
    }

    private GameObject buildTargetObject() {
        GameObject targetObject = new GameObject("PreviewTarget");
        targetObject.addComponent(new Transform3D());
        targetObject.addComponent(renderer);
        return targetObject;
    }

    private static GameObject buildSunObject() {
        GameObject sunObject = new GameObject("PreviewSun");
        sunObject.addComponent(new Transform3D().lookAt(-0.5f, -1.0f, -0.4f, 0.0f, 1.0f, 0.0f));
        sunObject.addComponent(new DirectionalLight()
                .setColor(1.0f, 0.97f, 0.92f)
                .setAmbient(0.28f, 0.30f, 0.34f));
        return sunObject;
    }

    private GameObject buildCameraObject() {
        GameObject cameraObject = new GameObject("PreviewCamera");
        cameraObject.addComponent(cameraTransform);
        cameraObject.addComponent(camera);
        return cameraObject;
    }

    public void setSurfaceShaderPath(String shaderPath) {
        LitMaterial next = materialFor(shaderPath);
        if (next == material) {
            return;
        }
        material = next;
        renderer.setMaterial(next);
    }

    private LitMaterial materialFor(String shaderPath) {
        return materialsByShaderPath.computeIfAbsent(shaderPath,
                path -> new LitMaterial().setSurfaceShaderPath(path).setReceiveShadows(false));
    }

    public void setMeshPath(String path, EngineServices services) {
        if (meshPath.equals(path)) {
            return;
        }
        meshPath = path;
        renderer.setMeshPath(path);
        renderer.meshRef().clearCache();
        renderer.setMaterials(List.of(material));
        renderer.onLoad(services);
    }

    public void reloadMesh(EngineServices services) {
        renderer.meshRef().clearCache();
        renderer.onLoad(services);
    }

    public void orbit(float deltaYaw, float deltaPitch) {
        if (!orbitEnabled) {
            return;
        }
        yawRadians += deltaYaw;
        pitchRadians = Math.clamp(pitchRadians + deltaPitch, MINIMUM_PITCH, MAXIMUM_PITCH);
        applyCamera();
    }

    public void zoom(float wheelDelta) {
        if (!orbitEnabled) {
            return;
        }
        distance = Math.clamp(distance - wheelDelta * ZOOM_STEP, MINIMUM_DISTANCE, MAXIMUM_DISTANCE);
        applyCamera();
    }

    private void applyCamera() {
        if (!orbitEnabled) {
            cameraTransform.setPosition(0.0f, FLAT_DISTANCE, 0.0f);
            cameraTransform.lookAt(0.0f, 0.0f, 0.0f, 0.0f, 0.0f, -1.0f);
            return;
        }
        float horizontal = (float) Math.cos(pitchRadians) * distance;
        cameraTransform.setPosition(horizontal * (float) Math.sin(yawRadians),
                (float) Math.sin(pitchRadians) * distance,
                horizontal * (float) Math.cos(yawRadians));
        cameraTransform.lookAt(0.0f, 0.0f, 0.0f, 0.0f, 1.0f, 0.0f);
    }

    public Scene scene() {
        return scene;
    }

    public Camera3D camera() {
        return camera;
    }
}
