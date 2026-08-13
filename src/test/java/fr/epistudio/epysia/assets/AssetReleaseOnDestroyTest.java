package fr.epistudio.epysia.assets;

import fr.epistudio.epysia.EngineServices;
import fr.epistudio.epysia.EpysiaEngine;
import fr.epistudio.epysia.components.Component;
import fr.epistudio.epysia.components.EpysiaComponent;
import fr.epistudio.epysia.components.transforms.Transform3D;
import fr.epistudio.epysia.gameobjects.GameObject;
import fr.epistudio.epysia.render.backend.NullRenderBackend;
import fr.epistudio.epysia.scene.Scene;
import fr.epistudio.epysia.window.Window;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;

class AssetReleaseOnDestroyTest {

    @EpysiaComponent(name = "Destruction Probe", category = "Testing")
    public static final class DestructionProbe extends Component {

        static final AtomicInteger DESTROYED = new AtomicInteger();

        @Override
        public void onDestroy(EngineServices services) {
            super.onDestroy(services);
            DESTROYED.incrementAndGet();
        }
    }

    @Test
    void aSceneAddedTheWayTheRuntimeAddsItStillDispatchesDestruction() {
        DestructionProbe.DESTROYED.set(0);
        EpysiaEngine engine = new EpysiaEngine(new Window("test", 1, 1), new NullRenderBackend());
        Scene scene = new Scene("runtime");
        engine.addScene(scene);
        GameObject object = new GameObject("doomed");
        object.addComponent(new Transform3D());
        object.addComponent(new DestructionProbe());
        scene.addGameObject(object);
        scene.advanceTick();

        scene.removeGameObject(object);
        scene.advanceTick();

        assertEquals(1, DestructionProbe.DESTROYED.get(),
                "addScene must install the removal listener, otherwise nothing an object owns is"
                        + " ever released in the standalone runtime");
    }

    @Test
    void everyObjectOfARemovedSubtreeIsDestroyed() {
        DestructionProbe.DESTROYED.set(0);
        EpysiaEngine engine = new EpysiaEngine(new Window("test", 1, 1), new NullRenderBackend());
        Scene scene = new Scene("runtime");
        engine.addScene(scene);
        GameObject parent = probeObject("parent");
        GameObject child = probeObject("child");
        scene.addGameObject(parent);
        scene.addGameObject(child);
        scene.advanceTick();
        child.setParent(parent);

        scene.removeGameObject(parent);
        scene.advanceTick();

        assertEquals(2, DestructionProbe.DESTROYED.get(),
                "removing a parent must destroy the whole subtree, not only the root");
    }

    private static GameObject probeObject(String name) {
        GameObject object = new GameObject(name);
        object.addComponent(new Transform3D());
        object.addComponent(new DestructionProbe());
        return object;
    }
}
