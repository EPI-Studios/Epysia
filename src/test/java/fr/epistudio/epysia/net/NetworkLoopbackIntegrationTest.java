package fr.epistudio.epysia.net;

import fr.epistudio.epysia.EpysiaEngine;
import fr.epistudio.epysia.components.transforms.Transform3D;
import fr.epistudio.epysia.gameobjects.GameObject;
import fr.epistudio.epysia.input.InputState;
import fr.epistudio.epysia.net.replication.NetworkObject;
import fr.epistudio.epysia.net.session.NetworkConfig;
import fr.epistudio.epysia.net.session.NetworkRole;
import fr.epistudio.epysia.net.session.TransportKind;
import fr.epistudio.epysia.render.backend.NullRenderBackend;
import fr.epistudio.epysia.scene.Scene;
import fr.epistudio.epysia.window.Window;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class NetworkLoopbackIntegrationTest {
    private static final int PORT = 45_701;
    private static final float STEP = 1.0f / 60.0f;
    private static final UUID SHARED_SCENE_ID = UUID.fromString("11111111-2222-3333-4444-555555555555");
    private static final float TOLERANCE = 0.001f;

    private Instance server;
    private Instance client;

    @BeforeEach
    void buildInstances() {
        server = new Instance("server");
        client = new Instance("client");
    }

    @AfterEach
    void shutdownInstances() {
        client.shutdown();
        server.shutdown();
    }

    @Test
    void aClientJoinsAndReceivesTheServerTransform() {
        server.startServer();
        client.connect();
        pump(20);
        assertEquals(NetworkRole.SERVER, server.role());
        assertEquals(NetworkRole.CLIENT, client.role());
        assertEquals(1, server.service().peers().size());
        server.transform().setPosition(4.0f, 1.0f, -2.0f);
        pump(30);
        assertEquals(4.0f, client.transform().position().x, TOLERANCE);
        assertEquals(-2.0f, client.transform().position().z, TOLERANCE);
    }

    @Test
    void ongoingMovementKeepsReachingTheClient() {
        server.startServer();
        client.connect();
        pump(20);
        for (int step = 0; step < 40; step++) {
            server.transform().translate(0.1f, 0.0f, 0.0f);
            pump(1);
        }
        pump(10);
        assertTrue(client.transform().position().x > 3.0f,
                "the client should have followed the server but sits at " + client.transform().position().x);
    }

    @Test
    void replicatedMovementReachesTheWorldMatrixAndNotOnlyTheField() {
        server.startServer();
        client.connect();
        pump(20);
        client.transform().worldMatrix();
        server.transform().setPosition(7.0f, 0.0f, 0.0f);
        pump(30);
        assertEquals(7.0f, client.transform().worldMatrix().m30(), TOLERANCE,
                "applying replicated fields must invalidate the cached matrices");
    }

    @Test
    void bothSidesAgreeOnTheNetworkIdOfASceneObject() {
        server.startServer();
        client.connect();
        pump(20);
        assertEquals(server.networkObject().networkId(), client.networkObject().networkId());
    }

    @Test
    void aClientWithTheWrongJoinSecretNeverBecomesAPeer() {
        server.config().setJoinSecret("correct-horse");
        server.startServer();
        client.config().setJoinSecret("wrong-horse");
        client.connect();
        pump(40);
        assertEquals(0, server.service().peers().size(),
                "a peer that cannot prove the join secret must never be admitted");
        assertEquals(NetworkRole.OFFLINE, client.role());
    }

    @Test
    void matchingJoinSecretsStillConnectAndReplicate() {
        server.config().setJoinSecret("correct-horse");
        server.startServer();
        client.config().setJoinSecret("correct-horse");
        client.connect();
        pump(20);
        assertEquals(1, server.service().peers().size());
        server.transform().setPosition(2.0f, 0.0f, 0.0f);
        pump(30);
        assertEquals(2.0f, client.transform().position().x, TOLERANCE);
    }

    @Test
    void aReturningClientReclaimsItsPeerIdAndItsObject() {
        server.startServer();
        client.connect();
        pump(20);
        int originalPeer = server.service().peers().getFirst().id();
        server.networkObject().assignOwner(originalPeer);
        pump(10);

        client.service().disconnect();
        pump(10);
        assertEquals(0, server.service().peers().size());
        assertEquals(originalPeer, server.networkObject().ownerPeer(),
                "the object must be held for the peer during the grace period, not released");

        client.connect();
        pump(30);
        assertEquals(originalPeer, server.service().peers().getFirst().id(),
                "a returning client should get its old peer id back");
        assertEquals(originalPeer, server.networkObject().ownerPeer(),
                "and its object back with it");
    }

    @Test
    void anAbsentClientLosesItsObjectOnceTheGraceExpires() {
        server.config().setReconnectGraceSeconds(0.2f);
        server.startServer();
        client.connect();
        pump(20);
        int originalPeer = server.service().peers().getFirst().id();
        server.networkObject().assignOwner(originalPeer);
        client.service().disconnect();
        pump(40);
        assertEquals(NetworkObject.SERVER_PEER, server.networkObject().ownerPeer(),
                "once nobody comes back the disconnect policy must finally run");
    }

    @Test
    void aClientLeavingDropsThePeerOnTheServer() {
        server.startServer();
        client.connect();
        pump(20);
        assertEquals(1, server.service().peers().size());
        client.service().disconnect();
        pump(10);
        assertEquals(0, server.service().peers().size());
        assertEquals(NetworkRole.OFFLINE, client.role());
    }

    @Test
    void theServerCountsTheBytesItSends() {
        server.startServer();
        client.connect();
        pump(20);
        server.transform().setPosition(1.0f, 2.0f, 3.0f);
        pump(10);
        assertTrue(server.service().stats().snapshotsSent() > 0, "the server should have sent snapshots");
        assertTrue(client.service().stats().bytesReceived(fr.epistudio.epysia.net.transport.NetChannel.UNRELIABLE)
                + client.service().stats().bytesReceived(fr.epistudio.epysia.net.transport.NetChannel.RELIABLE) > 0,
                "the client should have received bytes");
    }

    private void pump(int ticks) {
        for (int tick = 0; tick < ticks; tick++) {
            server.tick();
            client.tick();
        }
    }

    private static final class Instance {
        private final EpysiaEngine engine;
        private final NullRenderBackend backend = new NullRenderBackend();
        private final Scene scene;
        private final GameObject networked;
        private final NetworkReceiveSystem receive = new NetworkReceiveSystem();
        private final NetworkSendSystem send = new NetworkSendSystem();

        private Instance(String name) {
            Window window = Window.headless(name, 1, 1);
            engine = new EpysiaEngine(window, backend);
            scene = new Scene(name);
            engine.addScene(scene);
            engine.setActiveScene(scene);
            networked = new GameObject("Player", SHARED_SCENE_ID);
            networked.addComponent(new Transform3D());
            networked.addComponent(new NetworkObject());
            scene.addGameObject(networked);
            scene.advanceTick();
            engine.addSystem(receive);
            engine.addSystem(send);
            backend.initialize(window);
            engine.initialize();
        }

        private NetworkConfig config() {
            return config;
        }

        private NetworkService service() {
            return receive.service();
        }

        private NetworkRuntime runtime() {
            return receive.runtime();
        }

        private NetworkRole role() {
            return service().role();
        }

        private Transform3D transform() {
            return networked.getComponentOrNull(Transform3D.class);
        }

        private NetworkObject networkObject() {
            return networked.getComponentOrNull(NetworkObject.class);
        }

        private final NetworkConfig config = loopbackConfig();

        private void startServer() {
            service().startServer(config);
        }

        private void connect() {
            service().connect(config, "localhost", PORT);
        }

        private static NetworkConfig loopbackConfig() {
            NetworkConfig config = new NetworkConfig()
                    .setPort(PORT)
                    .setTransport(TransportKind.LOOPBACK);
            config.voice().setEnabled(false);
            return config;
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
