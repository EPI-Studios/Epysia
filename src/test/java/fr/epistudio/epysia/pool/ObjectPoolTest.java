package fr.epistudio.epysia.pool;

import fr.epistudio.epysia.EpysiaEngine;
import fr.epistudio.epysia.components.EpysiaComponent;
import fr.epistudio.epysia.gameobjects.GameObject;
import fr.epistudio.epysia.render.backend.NullRenderBackend;
import fr.epistudio.epysia.scene.Scene;
import fr.epistudio.epysia.scripting.Behaviour;
import fr.epistudio.epysia.window.Window;
import org.joml.Vector3f;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ObjectPoolTest {

    @EpysiaComponent(name = "Pool Probe", category = "Testing")
    public static final class PoolProbe extends Behaviour {

        static final AtomicInteger SPAWNS = new AtomicInteger();
        static final AtomicInteger DESPAWNS = new AtomicInteger();

        @Override
        public void onSpawn() {
            SPAWNS.incrementAndGet();
        }

        @Override
        public void onDespawn() {
            DESPAWNS.incrementAndGet();
        }
    }

    private static final String PREFAB = """
            {"name":"bullet","gameObjects":[{"name":"bullet","active":true,"parentIndex":-1,
            "components":[
            {"type":"fr.epistudio.epysia.components.transforms.Transform3D",
             "displayName":"Transform 3D","fields":{}},
            {"type":"fr.epistudio.epysia.pool.ObjectPoolTest$PoolProbe",
             "displayName":"Pool Probe","fields":{}}]}]}
            """;

    @Test
    void despawningAndSpawningReusesTheSameObject(@TempDir Path directory) throws IOException {
        EpysiaEngine engine = newEngine();
        ObjectPool pool = engine.pools().forPrefab(writePrefab(directory));

        GameObject first = pool.spawn();
        pool.despawn(first);
        GameObject second = pool.spawn();

        assertSame(first, second, "a despawned object must be handed back out rather than recreated");
        assertEquals(1, pool.createdCount(), "reuse means the prefab is instantiated once");
    }

    @Test
    void spawningBeyondTheIdleSetCreatesMore(@TempDir Path directory) throws IOException {
        EpysiaEngine engine = newEngine();
        ObjectPool pool = engine.pools().forPrefab(writePrefab(directory));

        GameObject first = pool.spawn();
        GameObject second = pool.spawn();

        assertNotNull(second);
        assertEquals(2, pool.createdCount(), "a pool grows rather than handing out a live object twice");
        assertEquals(2, pool.liveCount(), "both spawned objects count as live");
        assertFalse(first == second, "a live object is never handed out again");
    }

    @Test
    void aDespawnedObjectIsInactiveAndASpawnedOneIsActive(@TempDir Path directory) throws IOException {
        EpysiaEngine engine = newEngine();
        ObjectPool pool = engine.pools().forPrefab(writePrefab(directory));

        GameObject spawned = pool.spawn();
        assertTrue(spawned.active(), "a spawned object must be active");

        pool.despawn(spawned);
        assertFalse(spawned.active(), "a despawned object must stop rendering and updating");
        assertTrue(spawned.isAlive(), "pooling must not destroy the object, that is the whole point");
    }

    @Test
    void theSpawnAndDespawnHooksFire(@TempDir Path directory) throws IOException {
        PoolProbe.SPAWNS.set(0);
        PoolProbe.DESPAWNS.set(0);
        EpysiaEngine engine = newEngine();
        ObjectPool pool = engine.pools().forPrefab(writePrefab(directory));

        GameObject spawned = pool.spawn();
        pool.despawn(spawned);
        pool.spawn();

        assertEquals(2, PoolProbe.SPAWNS.get(), "onSpawn fires on every hand out, reuse included");
        assertEquals(1, PoolProbe.DESPAWNS.get(), "onDespawn fires once per return");
    }

    @Test
    void spawningPlacesTheObject(@TempDir Path directory) throws IOException {
        EpysiaEngine engine = newEngine();
        ObjectPool pool = engine.pools().forPrefab(writePrefab(directory));

        GameObject spawned = pool.spawn(new Vector3f(3.0f, 4.0f, 5.0f));

        assertEquals(4.0f, spawned.transform3DOrNull().position().y(), 1.0e-4f,
                "a spawn with a position must place the object there");
    }

    @Test
    void despawningSomethingThePoolDoesNotOwnIsRefused(@TempDir Path directory) throws IOException {
        EpysiaEngine engine = newEngine();
        ObjectPool pool = engine.pools().forPrefab(writePrefab(directory));

        assertFalse(pool.despawn(new GameObject("stranger")),
                "a pool must not claim an object it never handed out");
    }

    @Test
    void objectsDestroyedByASceneChangeAreNotHandedOutAgain(@TempDir Path directory) throws IOException {
        EpysiaEngine engine = newEngine();
        Scene scene = engine.scene();
        ObjectPool pool = engine.pools().forPrefab(writePrefab(directory));
        GameObject spawned = pool.spawn();
        pool.despawn(spawned);

        scene.removeGameObject(spawned);
        scene.advanceTick();
        GameObject afterReload = pool.spawn();

        assertFalse(spawned == afterReload,
                "an object the scene destroyed must never come back out of the pool");
        assertTrue(afterReload.isAlive(), "the replacement must be a live object");
    }

    private static String writePrefab(Path directory) throws IOException {
        Path file = directory.resolve("bullet.epyprefab");
        Files.writeString(file, PREFAB);
        return file.toString();
    }

    private static EpysiaEngine newEngine() {
        EpysiaEngine engine = new EpysiaEngine(new Window("test", 1, 1), new NullRenderBackend());
        engine.addScene(new Scene("pooling"));
        return engine;
    }
}
