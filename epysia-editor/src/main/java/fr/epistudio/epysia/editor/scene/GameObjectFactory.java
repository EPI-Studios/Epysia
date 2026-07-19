package fr.epistudio.epysia.editor.scene;

import fr.epistudio.epysia.EngineServices;
import fr.epistudio.epysia.components.Camera3D;
import fr.epistudio.epysia.components.DirectionalLight;
import fr.epistudio.epysia.components.IComponent;
import fr.epistudio.epysia.components.MeshRenderer;
import fr.epistudio.epysia.components.PointLight;
import fr.epistudio.epysia.components.SpotLight;
import fr.epistudio.epysia.components.transforms.Transform3D;
import fr.epistudio.epysia.editor.command.builtin.AddGameObjectCommand;
import fr.epistudio.epysia.gameobjects.GameObject;
import fr.epistudio.epysia.physics.components.BoxCollider;
import fr.epistudio.epysia.physics.components.CapsuleCollider;
import fr.epistudio.epysia.scene.Scene;
import org.joml.Vector3f;

import java.util.function.Supplier;

public final class GameObjectFactory {

    public enum Primitive { CUBE, PLANE, CAPSULE }

    private final Supplier<SceneDocument> activeDocument;
    private final EngineServices services;

    public GameObjectFactory(Supplier<SceneDocument> activeDocument, EngineServices services) {
        this.activeDocument = activeDocument;
        this.services = services;
    }

    public GameObject createPrimitive(Primitive primitive, Vector3f position) {
        GameObject gameObject = new GameObject(uniqueName(displayName(primitive)));
        gameObject.addComponent(new Transform3D().setPosition(position.x, position.y, position.z));
        MeshRenderer renderer = new MeshRenderer().setMeshPath(presetPath(primitive));
        gameObject.addComponent(renderer);
        renderer.onLoad(services);
        gameObject.addComponent(colliderFor(primitive));
        commit(gameObject);
        return gameObject;
    }

    public GameObject createMesh(String meshPath, String baseName, Vector3f position) {
        GameObject gameObject = new GameObject(uniqueName(baseName));
        gameObject.addComponent(new Transform3D().setPosition(position.x, position.y, position.z));
        MeshRenderer renderer = new MeshRenderer().setMeshPath(meshPath);
        gameObject.addComponent(renderer);
        renderer.onLoad(services);
        commit(gameObject);
        return gameObject;
    }

    public GameObject createPointLight(Vector3f position) {
        return createWithComponent("Point Light", new PointLight(), position);
    }

    public GameObject createSpotLight(Vector3f position) {
        return createWithComponent("Spot Light", new SpotLight(), position);
    }

    public GameObject createDirectionalLight(Vector3f position) {
        return createWithComponent("Directional Light", new DirectionalLight(), position);
    }

    public GameObject createCamera(Vector3f position) {
        return createWithComponent("Camera", new Camera3D(), position);
    }

    public GameObject createEmpty(Vector3f position) {
        GameObject gameObject = new GameObject(uniqueName("GameObject"));
        gameObject.addComponent(new Transform3D().setPosition(position.x, position.y, position.z));
        commit(gameObject);
        return gameObject;
    }

    private GameObject createWithComponent(String baseName, IComponent component, Vector3f position) {
        GameObject gameObject = new GameObject(uniqueName(baseName));
        gameObject.addComponent(new Transform3D().setPosition(position.x, position.y, position.z));
        gameObject.addComponent(component);
        commit(gameObject);
        return gameObject;
    }

    private void commit(GameObject gameObject) {
        activeDocument.get().history().execute(new AddGameObjectCommand(gameObject, true));
    }

    private String uniqueName(String baseName) {
        Scene scene = activeDocument.get().scene();
        if (scene.findByName(baseName).isEmpty()) {
            return baseName;
        }
        int suffix = 2;
        while (scene.findByName(baseName + " " + suffix).isPresent()) {
            suffix++;
        }
        return baseName + " " + suffix;
    }

    private static String displayName(Primitive primitive) {
        return switch (primitive) {
            case CUBE -> "Cube";
            case PLANE -> "Plane";
            case CAPSULE -> "Capsule";
        };
    }

    private static String presetPath(Primitive primitive) {
        return switch (primitive) {
            case CUBE -> "preset:cube";
            case PLANE -> "preset:plane";
            case CAPSULE -> "preset:capsule";
        };
    }

    private static IComponent colliderFor(Primitive primitive) {
        return switch (primitive) {
            case CUBE -> new BoxCollider();
            case PLANE -> flatBoxCollider();
            case CAPSULE -> new CapsuleCollider();
        };
    }

    private static BoxCollider flatBoxCollider() {
        BoxCollider collider = new BoxCollider();
        collider.halfExtents().set(0.5f, 0.02f, 0.5f);
        return collider;
    }
}
