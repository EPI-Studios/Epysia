package fr.epistudio.epysia.net;

import fr.epistudio.epysia.EpysiaEngine;
import fr.epistudio.epysia.components.transforms.Transform3D;
import fr.epistudio.epysia.gameobjects.GameObject;
import fr.epistudio.epysia.input.InputState;
import fr.epistudio.epysia.net.replication.NetworkObject;
import fr.epistudio.epysia.net.session.NetworkConfig;
import fr.epistudio.epysia.net.session.NetworkPeer;
import fr.epistudio.epysia.net.session.TransportKind;
import fr.epistudio.epysia.render.backend.NullRenderBackend;
import fr.epistudio.epysia.scene.Scene;
import fr.epistudio.epysia.window.Window;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class NetworkSoakTest {
    private static final int PORT = 45_911;
    private static final int SOAK_TICKS = 10_000;
    private static final int CHURN_INTERVAL_TICKS = 20;
    private static final int LIVE_OBJECTS = 12;
    private static final float STEP = 1.0f / 60.0f;

    private final List<Instance> instances = new ArrayList<>();
    private Instance server;
    private Instance client;
    private int nextSpawnIndex;

    @AfterEach
    void shutdownAll() {
        for (int index = instances.size() - 1; index >= 0; index--) {
            instances.get(index).shutdown();
        }
        instances.clear();
    }

    @Test
    void aLongSessionDoesNotAccumulateStatePerObjectThatEverExisted() {
        startSession();
        pump(60);
        for (int tick = 0; tick < SOAK_TICKS; tick++) {
            if (tick % CHURN_INTERVAL_TICKS == 0) {
                churnOneObject();
            }
            pump(1);
        }
        NetworkPeer peer = server.service().peers().getFirst();
        int churnedObjects = SOAK_TICKS / CHURN_INTERVAL_TICKS;
        assertTrue(peer.rememberedObjectCount() <= LIVE_OBJECTS * 2,
                "the peer remembers " + peer.rememberedObjectCount() + " objects after churning "
                        + churnedObjects + ", which means the table grows with history not with the world");
    }

    @Test
    void aLongSessionKeepsReplicatingAndStaysConnected() {
        startSession();
        pump(SOAK_TICKS);
        assertEquals(1, server.service().peers().size(), "the peer should still be connected");
        server.moveFirst(42.0f);
        pump(120);
        assertEquals(42.0f, client.firstPosition(), 0.05f,
                "replication should still be working after a long session");
        assertEquals(0L, server.service().stats().malformedPackets());
        assertEquals(0L, server.service().stats().rejectedPackets());
    }

    private void startSession() {
        server = newInstance("server");
        client = newInstance("client");
        server.service().startServer(configuration());
        client.service().connect(configuration(), "localhost", PORT);
    }

    private void churnOneObject() {
        if (server.replicated.size() >= LIVE_OBJECTS) {
            GameObject retired = server.replicated.removeFirst();
            server.service().despawn(retired);
        }
        server.spawnLocally(nextSpawnIndex++);
    }

    private Instance newInstance(String name) {
        Instance instance = new Instance(name);
        instances.add(instance);
        return instance;
    }

    private void pump(int ticks) {
        for (int tick = 0; tick < ticks; tick++) {
            for (Instance instance : instances) {
                instance.tick();
            }
        }
    }

    private static NetworkConfig configuration() {
        NetworkConfig config = new NetworkConfig().setPort(PORT).setTransport(TransportKind.LOOPBACK);
        config.voice().setEnabled(false);
        return config;
    }

    private static final class Instance {
        private final EpysiaEngine engine;
        private final NullRenderBackend backend = new NullRenderBackend();
        private final Scene scene;
        private final List<GameObject> replicated = new ArrayList<>();
        private final NetworkReceiveSystem receive = new NetworkReceiveSystem();
        private final NetworkSendSystem send = new NetworkSendSystem();

        private Instance(String name) {
            Window window = Window.headless(name, 1, 1);
            engine = new EpysiaEngine(window, backend);
            scene = new Scene(name);
            engine.addScene(scene);
            engine.setActiveScene(scene);
            addObject(new UUID(0xB0A7L, 0));
            engine.addSystem(receive);
            engine.addSystem(send);
            backend.initialize(window);
            engine.initialize();
        }

        private void addObject(UUID id) {
            GameObject object = new GameObject("replicated", id);
            object.addComponent(new Transform3D());
            object.addComponent(new NetworkObject());
            scene.addGameObject(object);
            scene.advanceTick();
            replicated.add(object);
        }

        private void spawnLocally(int index) {
            addObject(new UUID(0xB0A7L, index + 1L));
            NetworkObject networkObject = replicated.getLast().getComponentOrNull(NetworkObject.class);
            networkObject.assignNetworkId(receive.runtime().replication().objects().allocateNetworkId())
                    .markSpawnedAtRuntime();
            receive.runtime().replication().objects()
                    .register(replicated.getLast(), networkObject, networkObject.networkId());
        }

        private void moveFirst(float x) {
            replicated.getFirst().getComponentOrNull(Transform3D.class).setPosition(x, 0.0f, 0.0f);
        }

        private float firstPosition() {
            return replicated.getFirst().getComponentOrNull(Transform3D.class).position().x;
        }

        private NetworkService service() {
            return receive.service();
        }

        private void tick() {
            engine.tick(InputState.inactive(), STEP);
        }

        private void shutdown() {
            service().disconnect();
            engine.shutdown();
            backend.shutdown();
        }
    }
}
