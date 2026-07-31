package fr.epistudio.epysia.editor.scene;

import fr.epistudio.epysia.EngineServices;
import fr.epistudio.epysia.components.Camera3D;
import fr.epistudio.epysia.components.DirectionalLight;
import fr.epistudio.epysia.components.IComponent;
import fr.epistudio.epysia.components.MeshRenderer;
import fr.epistudio.epysia.components.PointLight;
import fr.epistudio.epysia.components.SpotLight;
import fr.epistudio.epysia.components.SpriteRenderer;
import fr.epistudio.epysia.components.TilemapRenderer;
import fr.epistudio.epysia.components.GlobalLight2D;
import fr.epistudio.epysia.components.IComponent;
import fr.epistudio.epysia.components.PointLight2D;
import fr.epistudio.epysia.components.SpotLight2D;
import fr.epistudio.epysia.components.transforms.Transform2D;
import fr.epistudio.epysia.components.transforms.Transform3D;
import fr.epistudio.epysia.editor.command.builtin.AddGameObjectCommand;
import fr.epistudio.epysia.gameobjects.GameObject;
import fr.epistudio.epysia.physics.components.BoxCollider;
import fr.epistudio.epysia.physics.components.CapsuleCollider;
import fr.epistudio.epysia.physics.components.TilemapCollider2D;
import fr.epistudio.epysia.scene.Scene;
import org.joml.Vector3f;

import java.util.function.BooleanSupplier;
import java.util.function.Supplier;

public final class GameObjectFactory {

    public enum Primitive { CUBE, PLANE, CAPSULE }

    private final Supplier<SceneDocument> activeDocument;
    private final EngineServices services;
    private final BooleanSupplier twoDimensional;

    public GameObjectFactory(Supplier<SceneDocument> activeDocument, EngineServices services,
                             BooleanSupplier twoDimensional) {
        this.activeDocument = activeDocument;
        this.services = services;
        this.twoDimensional = twoDimensional;
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

    public GameObject createSprite(Vector3f position) {
        GameObject gameObject = new GameObject(uniqueName("Sprite"));
        gameObject.addComponent(new Transform2D().setPosition(position.x, position.y));
        gameObject.addComponent(new SpriteRenderer());
        commit(gameObject);
        return gameObject;
    }

    public GameObject createTilemap(Vector3f position) {
        GameObject gameObject = new GameObject(uniqueName("Tilemap"));
        gameObject.addComponent(new Transform2D().setPosition(position.x, position.y));
        gameObject.addComponent(new TilemapRenderer());
        gameObject.addComponent(new TilemapCollider2D());
        commit(gameObject);
        return gameObject;
    }

    public GameObject createPointLight2D(Vector3f position) {
        return createLight2D("Point Light 2D", new PointLight2D(), position);
    }

    public GameObject createSpotLight2D(Vector3f position) {
        return createLight2D("Spot Light 2D", new SpotLight2D(), position);
    }

    public GameObject createGlobalLight2D(Vector3f position) {
        GameObject gameObject = new GameObject(uniqueName("Global Light 2D"));
        gameObject.addComponent(new GlobalLight2D());
        commit(gameObject);
        return gameObject;
    }

    private GameObject createLight2D(String name, IComponent light, Vector3f position) {
        GameObject gameObject = new GameObject(uniqueName(name));
        gameObject.addComponent(new Transform2D().setPosition(position.x, position.y));
        gameObject.addComponent(light);
        commit(gameObject);
        return gameObject;
    }

    public GameObject createEmpty(Vector3f position) {
        GameObject gameObject = new GameObject(uniqueName("GameObject"));
        if (twoDimensional.getAsBoolean()) {
            gameObject.addComponent(new Transform2D().setPosition(position.x, position.y));
        } else {
            gameObject.addComponent(new Transform3D().setPosition(position.x, position.y, position.z));
        }
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
        return UniqueObjectName.in(activeDocument.get().scene(), baseName);
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
