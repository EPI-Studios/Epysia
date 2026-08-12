package fr.epistudio.epysia.net;

import fr.epistudio.epysia.EpysiaEngine;
import fr.epistudio.epysia.components.transforms.Transform3D;
import fr.epistudio.epysia.gameobjects.GameObject;
import fr.epistudio.epysia.input.InputState;
import fr.epistudio.epysia.input.KeyCode;
import fr.epistudio.epysia.input.MouseButton;
import fr.epistudio.epysia.input.action.InputAction;
import fr.epistudio.epysia.input.action.InputBinding;
import fr.epistudio.epysia.net.prediction.CharacterInputMapper;
import fr.epistudio.epysia.net.prediction.InputSample;
import fr.epistudio.epysia.net.replication.NetworkCharacterController;
import fr.epistudio.epysia.net.replication.NetworkObject;
import fr.epistudio.epysia.net.session.NetworkConfig;
import fr.epistudio.epysia.net.session.TransportKind;
import fr.epistudio.epysia.physics.PhysicsSystem;
import fr.epistudio.epysia.physics.components.BoxCollider;
import fr.epistudio.epysia.physics.components.CharacterControllerComponent;
import fr.epistudio.epysia.render.backend.NullRenderBackend;
import fr.epistudio.epysia.scene.Scene;
import fr.epistudio.epysia.window.Window;
import org.joml.Vector3f;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class PredictedCharacterNetworkTest {
    private static final float STEP = 1.0f / 60.0f;
    private static final int FORWARD_ACTION = 0;
    private static final int JUMP_ACTION = 1;
    private static final float SPAWN_HEIGHT = 1.5f;
    private static final int HANDSHAKE_TICKS = 60;
    private static final int SETTLE_TICKS = 90;
    private static final int WALK_TICKS = 120;
    private static final float AGREEMENT_METERS = 0.05f;
    private static final float BACKWARD_TOLERANCE_METERS = 0.001f;
    private static final UUID GROUND_SCENE_ID = UUID.fromString("aaaaaaaa-0000-0000-0000-000000000001");
    private static final UUID PLAYER_SCENE_ID = UUID.fromString("aaaaaaaa-0000-0000-0000-000000000002");

    private static List<Condition> conditions() {
        return List.of(
                new Condition("lan", 45_740, 0.0f, 0.0f, 0.0f),
                new Condition("reordering", 45_742, 0.0f, 0.020f, 0.0f),
                new Condition("lossy", 45_743, 0.0f, 0.020f, 0.05f));
    }

    private record Condition(String name, int port, float oneWayLatencySeconds,
                             float jitterSeconds, float lossProbability) {
        @Override
        public String toString() {
            return name;
        }
    }

    private Instance server;
    private Instance client;
    private Condition condition;
    private final List<Frame> trace = new ArrayList<>();

    @AfterEach
    void shutdownInstances() {
        if (client != null) {
            client.shutdown();
        }
        if (server != null) {
            server.shutdown();
        }
    }

    @ParameterizedTest
    @MethodSource("conditions")
    void aWalkingClientNeverSlidesBackwards(Condition network) {
        startSession(network);
        walk(WALK_TICKS);
        float worstBackwardStep = worstBackwardStep();
        assertTrue(worstBackwardStep <= BACKWARD_TOLERANCE_METERS,
                "the predicted client slid backwards by " + worstBackwardStep + " m\n" + diagnostics());
    }

    @ParameterizedTest
    @MethodSource("conditions")
    void aWalkingClientEndsWhereTheServerPutIt(Condition network) {
        startSession(network);
        walk(WALK_TICKS);
        pump(SETTLE_TICKS, ScriptedInput.IDLE);
        Frame last = trace.getLast();
        assertEquals(last.serverPosition.z, last.clientPosition.z, AGREEMENT_METERS,
                "client and server disagree after the walk\n" + diagnostics());
    }

    @ParameterizedTest
    @MethodSource("conditions")
    void aJumpReachesTheSameHeightOnBothSides(Condition network) {
        startSession(network);
        jump();
        pump(SETTLE_TICKS, ScriptedInput.IDLE);
        assertEquals(apexOf(false), apexOf(true), AGREEMENT_METERS,
                "the jump apexes disagree\n" + diagnostics());
    }

    @ParameterizedTest
    @MethodSource("conditions")
    void theServerSeesEveryJumpTheClientPressed(Condition network) {
        startSession(network);
        jump();
        pump(SETTLE_TICKS, ScriptedInput.IDLE);
        assertEquals(client.motion.jumpEdgeTicks, server.motion.jumpEdgeTicks,
                "the server did not replay the same jump edges as the client\n" + diagnostics());
    }

    private void startSession(Condition network) {
        condition = network;
        server = new Instance("server", network);
        client = new Instance("client", network);
        server.startServer();
        client.connect();
        pump(HANDSHAKE_TICKS, ScriptedInput.IDLE);
        assertEquals(1, server.service().peers().size(), "the client never joined");
        int peer = server.service().peers().getFirst().id();
        assertEquals(peer, client.service().localPeer(), "the client does not know its own peer id");
        server.networkObject().assignOwner(peer);
        client.networkObject().assignOwner(peer);
        pump(SETTLE_TICKS, ScriptedInput.IDLE);
        trace.clear();
        server.motion.jumpEdgeTicks.clear();
        client.motion.jumpEdgeTicks.clear();
    }

    private void walk(int ticks) {
        pump(ticks, new ScriptedInput(EnumSet.of(KeyCode.W), EnumSet.noneOf(KeyCode.class)));
    }

    private void jump() {
        pump(1, new ScriptedInput(EnumSet.of(KeyCode.SPACE), EnumSet.of(KeyCode.SPACE)));
        pump(4, new ScriptedInput(EnumSet.of(KeyCode.SPACE), EnumSet.noneOf(KeyCode.class)));
    }

    private void pump(int ticks, InputState input) {
        for (int tick = 0; tick < ticks; tick++) {
            server.tick(InputState.inactive());
            client.tick(input);
            trace.add(new Frame(trace.size(),
                    new Vector3f(client.transform().position()), client.controller().verticalVelocity(),
                    new Vector3f(server.transform().position()), server.controller().verticalVelocity(),
                    client.service().stats().reconciliationReplays()));
        }
    }

    private float worstBackwardStep() {
        float worst = 0.0f;
        for (int index = 1; index < trace.size(); index++) {
            float delta = trace.get(index).clientPosition.z - trace.get(index - 1).clientPosition.z;
            worst = Math.max(worst, delta);
        }
        return worst;
    }

    private float apexOf(boolean onClient) {
        float apex = Float.NEGATIVE_INFINITY;
        for (Frame frame : trace) {
            apex = Math.max(apex, onClient ? frame.clientPosition.y : frame.serverPosition.y);
        }
        return apex;
    }

    private String diagnostics() {
        return "network " + condition
                + "\nclient jump edges " + client.motion.jumpEdgeTicks
                + " server jump edges " + server.motion.jumpEdgeTicks
                + "\nserver missing inputs " + server.service().stats().missingInputs()
                + " pending inputs " + server.service().peers().getFirst().pendingInputCount()
                + "\nserver tick " + server.service().tick()
                + " client tick " + client.service().tick()
                + " client lead " + client.receive.runtime().clientLead()
                + "\n" + traceTable();
    }

    private String traceTable() {
        StringBuilder text = new StringBuilder(
                "tick clientY clientZ clientVy serverY serverZ serverVy replays\n");
        for (Frame frame : trace) {
            text.append(String.format("%4d %8.4f %8.4f %8.4f %8.4f %8.4f %8.4f %7d%n", frame.tick,
                    frame.clientPosition.y, frame.clientPosition.z, frame.clientVerticalVelocity,
                    frame.serverPosition.y, frame.serverPosition.z, frame.serverVerticalVelocity,
                    frame.replays));
        }
        return text.toString();
    }

    private record Frame(int tick, Vector3f clientPosition, float clientVerticalVelocity,
                         Vector3f serverPosition, float serverVerticalVelocity, long replays) {
    }

    private static final class RecordingMotion implements CharacterInputMapper {
        private final List<Integer> jumpEdgeTicks = new ArrayList<>();

        @Override
        public void applyTo(CharacterControllerComponent controller, InputSample input, float delta) {
            float speed = input.isDown(FORWARD_ACTION) ? controller.moveSpeed() : 0.0f;
            controller.setDesiredHorizontalMove(new Vector3f(0.0f, 0.0f, -speed));
            if (input.wasPressed(JUMP_ACTION)) {
                jumpEdgeTicks.add(input.tick());
                controller.requestJump();
            }
        }
    }

    private static final class ScriptedInput implements InputState {
        private static final ScriptedInput IDLE =
                new ScriptedInput(EnumSet.noneOf(KeyCode.class), EnumSet.noneOf(KeyCode.class));

        private final Set<KeyCode> down;
        private final Set<KeyCode> pressed;

        private ScriptedInput(Set<KeyCode> down, Set<KeyCode> pressed) {
            this.down = down;
            this.pressed = pressed;
        }

        @Override
        public boolean isKeyDown(KeyCode key) {
            return down.contains(key);
        }

        @Override
        public boolean wasKeyPressed(KeyCode key) {
            return pressed.contains(key);
        }

        @Override
        public boolean isMouseButtonDown(MouseButton button) {
            return false;
        }

        @Override
        public float cursorX() {
            return 0.0f;
        }

        @Override
        public float cursorY() {
            return 0.0f;
        }

        @Override
        public float scrollDeltaY() {
            return 0.0f;
        }
    }

    private static final class Instance {
        private final RecordingMotion motion = new RecordingMotion();
        private final EpysiaEngine engine;
        private final NullRenderBackend backend = new NullRenderBackend();
        private final Scene scene;
        private final GameObject player;
        private final NetworkReceiveSystem receive = new NetworkReceiveSystem();
        private final NetworkSendSystem send = new NetworkSendSystem();
        private final NetworkConfig config;

        private Instance(String name, Condition network) {
            config = configFor(network);
            Window window = Window.headless(name, 1, 1);
            engine = new EpysiaEngine(window, backend);
            scene = new Scene(name);
            engine.addScene(scene);
            engine.setActiveScene(scene);
            scene.addGameObject(ground());
            player = buildPlayer();
            scene.addGameObject(player);
            scene.advanceTick();
            engine.inputActions().replaceAll(List.of(
                    InputAction.button("MoveForward", InputBinding.key(KeyCode.W)),
                    InputAction.button("Jump", InputBinding.key(KeyCode.SPACE))));
            engine.addSystem(new PhysicsSystem());
            engine.addSystem(receive);
            engine.addSystem(send);
            backend.initialize(window);
            engine.initialize();
        }

        private GameObject buildPlayer() {
            GameObject built = new GameObject("Player", PLAYER_SCENE_ID);
            built.addComponent(new Transform3D().setPosition(0.0f, SPAWN_HEIGHT, 0.0f));
            built.addComponent(new NetworkObject().setPredictOwnedMovement(true));
            built.addComponent(new CharacterControllerComponent());
            built.addComponent(new NetworkCharacterController().setInputMapper(motion));
            return built;
        }

        private static GameObject ground() {
            GameObject floor = new GameObject("Ground", GROUND_SCENE_ID);
            floor.addComponent(new Transform3D().setPosition(0.0f, 0.0f, 0.0f));
            BoxCollider shape = new BoxCollider();
            shape.halfExtents().set(80.0f, 0.5f, 80.0f);
            floor.addComponent(shape);
            return floor;
        }

        private static NetworkConfig configFor(Condition network) {
            NetworkConfig built = new NetworkConfig()
                    .setPort(network.port())
                    .setTransport(TransportKind.LOOPBACK);
            built.simulateNetwork(network.oneWayLatencySeconds(), network.jitterSeconds(),
                    network.lossProbability());
            built.voice().setEnabled(false);
            return built;
        }

        private NetworkService service() {
            return receive.service();
        }

        private Transform3D transform() {
            return player.getComponentOrNull(Transform3D.class);
        }

        private CharacterControllerComponent controller() {
            return player.getComponentOrNull(CharacterControllerComponent.class);
        }

        private NetworkObject networkObject() {
            return player.getComponentOrNull(NetworkObject.class);
        }

        private void startServer() {
            service().startServer(config);
        }

        private void connect() {
            service().connect(config, "localhost", config.port());
        }

        private void tick(InputState input) {
            int steps = Math.max(0, 1 + engine.consumeCatchUpSteps());
            for (int step = 0; step < steps; step++) {
                engine.tick(input, STEP);
            }
        }

        private void shutdown() {
            service().disconnect();
            engine.shutdown();
            backend.shutdown();
        }
    }
}
