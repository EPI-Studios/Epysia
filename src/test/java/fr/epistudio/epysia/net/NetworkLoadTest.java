package fr.epistudio.epysia.net;

import fr.epistudio.epysia.EpysiaEngine;
import fr.epistudio.epysia.components.transforms.Transform3D;
import fr.epistudio.epysia.gameobjects.GameObject;
import fr.epistudio.epysia.input.InputState;
import fr.epistudio.epysia.net.replication.NetworkObject;
import fr.epistudio.epysia.net.session.NetworkConfig;
import fr.epistudio.epysia.net.session.NetworkPeer;
import fr.epistudio.epysia.net.session.TransportKind;
import fr.epistudio.epysia.net.transport.NetChannel;
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

final class NetworkLoadTest {
    private static final int PORT = 45_811;
    private static final int CLIENT_COUNT = 8;
    private static final int REPLICATED_OBJECTS = 60;
    private static final float STEP = 1.0f / 60.0f;
    private static final int WARMUP_TICKS = 60;
    private static final int MEASURED_TICKS = 120;
    private static final float TOLERANCE = 0.05f;

    private final List<Instance> instances = new ArrayList<>();
    private Instance server;

    @AfterEach
    void shutdownAll() {
        for (int index = instances.size() - 1; index >= 0; index--) {
            instances.get(index).shutdown();
        }
        instances.clear();
    }

    @Test
    void eightClientsStayConvergedAndTheCostIsBounded() {
        server = newInstance("server");
        server.service().startServer(configuration());
        List<Instance> clients = connectClients();
        pump(WARMUP_TICKS);
        assertEquals(CLIENT_COUNT, server.service().peers().size(), "every client should have joined");

        long bytesBefore = receivedBytes(clients);
        moveEverything();
        pump(MEASURED_TICKS);
        long perClientPerSecond = measuredRate(clients, bytesBefore);

        assertConverged(clients);
        assertTrue(perClientPerSecond < 400_000L,
                "downstream ran to " + perClientPerSecond + " bytes per second per client");
        System.out.println("[load] " + CLIENT_COUNT + " clients, " + REPLICATED_OBJECTS
                + " objects: " + perClientPerSecond + " bytes/s per client, "
                + server.service().stats().snapshotsSent() + " snapshots sent");
    }

    @Test
    void anInterestRadiusCullsWhatItShould() {
        server = newInstance("server");
        server.service().startServer(configuration().setInterestRadiusMeters(5.0f));
        connectClients();
        pump(WARMUP_TICKS);
        moveEverything();
        giveEachClientAnObject();
        pump(MEASURED_TICKS);
        assertTrue(server.service().stats().culledObjects() > 0,
                "a tight interest radius should have culled distant objects before diffing them");
    }

    private void giveEachClientAnObject() {
        List<NetworkPeer> peers = server.service().peers();
        for (int index = 0; index < peers.size(); index++) {
            server.networkObject(index).assignOwner(peers.get(index).id());
        }
    }

    private List<Instance> connectClients() {
        List<Instance> clients = new ArrayList<>();
        for (int index = 0; index < CLIENT_COUNT; index++) {
            Instance client = newInstance("client" + index);
            client.service().connect(configuration(), "localhost", PORT);
            clients.add(client);
        }
        return clients;
    }

    private void moveEverything() {
        for (int index = 0; index < REPLICATED_OBJECTS; index++) {
            server.replicated(index).setPosition(index * 2.0f, 1.0f, index * 0.5f);
        }
    }

    private void assertConverged(List<Instance> clients) {
        for (Instance client : clients) {
            Transform3D mirrored = client.replicated(REPLICATED_OBJECTS - 1);
            assertEquals(server.replicated(REPLICATED_OBJECTS - 1).position().x,
                    mirrored.position().x, TOLERANCE,
                    "client " + client.name + " never caught up");
        }
    }

    private static long receivedBytes(List<Instance> clients) {
        long total = 0L;
        for (Instance client : clients) {
            total += client.service().stats().bytesReceived(NetChannel.UNRELIABLE)
                    + client.service().stats().bytesReceived(NetChannel.RELIABLE);
        }
        return total;
    }

    private static long measuredRate(List<Instance> clients, long bytesBefore) {
        long delta = receivedBytes(clients) - bytesBefore;
        float seconds = MEASURED_TICKS * STEP;
        return Math.round(delta / seconds / clients.size());
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
        NetworkConfig config = new NetworkConfig()
                .setPort(PORT)
                .setTransport(TransportKind.LOOPBACK)
                .setMaximumPeers(CLIENT_COUNT + 1);
        config.voice().setEnabled(false);
        return config;
    }

    private static final class Instance {
        private final String name;
        private final EpysiaEngine engine;
        private final NullRenderBackend backend = new NullRenderBackend();
        private final List<GameObject> replicated = new ArrayList<>();
        private final NetworkReceiveSystem receive = new NetworkReceiveSystem();
        private final NetworkSendSystem send = new NetworkSendSystem();

        private Instance(String name) {
            this.name = name;
            Window window = Window.headless(name, 1, 1);
            engine = new EpysiaEngine(window, backend);
            Scene scene = new Scene(name);
            engine.addScene(scene);
            engine.setActiveScene(scene);
            populate(scene);
            engine.addSystem(receive);
            engine.addSystem(send);
            backend.initialize(window);
            engine.initialize();
        }

        private void populate(Scene scene) {
            for (int index = 0; index < REPLICATED_OBJECTS; index++) {
                GameObject object = new GameObject("replicated" + index, stableId(index));
                object.addComponent(new Transform3D());
                object.addComponent(new NetworkObject());
                scene.addGameObject(object);
                replicated.add(object);
            }
            scene.advanceTick();
        }

        private static UUID stableId(int index) {
            return new UUID(0xA110CL, index);
        }

        private NetworkService service() {
            return receive.service();
        }

        private NetworkObject networkObject(int index) {
            return replicated.get(index).getComponentOrNull(NetworkObject.class);
        }

        private Transform3D replicated(int index) {
            return replicated.get(index).getComponentOrNull(Transform3D.class);
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
