package fr.epistudio.epysia.net;

import fr.epistudio.epysia.EngineServices;
import fr.epistudio.epysia.components.IComponent;
import fr.epistudio.epysia.components.transforms.Transform3D;
import fr.epistudio.epysia.gameobjects.GameObject;
import fr.epistudio.epysia.input.InputState;
import fr.epistudio.epysia.logging.Logger;
import fr.epistudio.epysia.net.diagnostics.DiagnosticsServer;
import fr.epistudio.epysia.net.diagnostics.MetricsSnapshot;
import fr.epistudio.epysia.net.diagnostics.NetworkStats;
import fr.epistudio.epysia.net.diagnostics.PeerLatency;
import fr.epistudio.epysia.net.prediction.InputRing;
import fr.epistudio.epysia.net.prediction.InputSample;
import fr.epistudio.epysia.net.prediction.InputSampler;
import fr.epistudio.epysia.net.prediction.PhysicsRollback;
import fr.epistudio.epysia.net.prediction.PredictedMovement;
import fr.epistudio.epysia.net.prediction.CharacterRollback;
import fr.epistudio.epysia.net.prediction.PredictedPhysics;
import fr.epistudio.epysia.net.prediction.PredictedTransform;
import fr.epistudio.epysia.net.prediction.PredictionBuffer;
import fr.epistudio.epysia.net.prediction.ReconciliationRequest;
import fr.epistudio.epysia.net.prediction.Reconciler;
import fr.epistudio.epysia.physics.PhysicsSystem;
import fr.epistudio.epysia.physics.components.CharacterControllerComponent;
import fr.epistudio.epysia.physics.components.RigidBodyComponent;
import fr.epistudio.epysia.net.protocol.MessageType;
import fr.epistudio.epysia.net.protocol.NetReader;
import fr.epistudio.epysia.net.replication.SnapshotReader;
import fr.epistudio.epysia.net.replication.NetworkCharacterController;
import fr.epistudio.epysia.net.replication.NetworkObject;
import fr.epistudio.epysia.net.replication.ReplicationRuntime;
import fr.epistudio.epysia.net.replication.NetworkInterestGrid;
import fr.epistudio.epysia.net.replication.SnapshotInterest;
import fr.epistudio.epysia.net.replication.SnapshotPriority;
import fr.epistudio.epysia.net.replication.SnapshotRequest;
import fr.epistudio.epysia.net.replication.WorldState;
import fr.epistudio.epysia.net.rpc.RpcMethod;
import fr.epistudio.epysia.net.rpc.RpcTarget;
import fr.epistudio.epysia.net.session.DisconnectReason;
import fr.epistudio.epysia.net.session.NetworkConfig;
import fr.epistudio.epysia.net.session.NetworkEvents;
import fr.epistudio.epysia.net.session.NetworkPeer;
import fr.epistudio.epysia.net.session.NetworkRole;
import fr.epistudio.epysia.net.session.NetworkSession;
import fr.epistudio.epysia.net.session.PeerIds;
import fr.epistudio.epysia.net.session.SessionIdentity;
import fr.epistudio.epysia.net.transport.NetChannel;
import fr.epistudio.epysia.net.voice.VoiceChatComponent;
import fr.epistudio.epysia.net.voice.VoiceFrame;
import fr.epistudio.epysia.net.voice.VoiceRouter;
import fr.epistudio.epysia.net.voice.VoiceRoutingContext;
import fr.epistudio.epysia.net.voice.VoicePlayback;
import fr.epistudio.epysia.net.voice.VoiceRuntime;
import fr.epistudio.epysia.net.voice.VoiceScope;
import fr.epistudio.epysia.audio.AudioBus;
import fr.epistudio.epysia.audio.AudioSystem;
import fr.epistudio.epysia.scene.Scene;
import fr.epistudio.epysia.scripting.Behaviour;
import org.joml.Quaternionf;
import org.joml.Quaternionfc;
import org.joml.Vector3f;
import org.joml.Vector3fc;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import fr.epistudio.epysia.input.action.InputActions;
import fr.epistudio.epysia.net.protocol.NetWriter;
import fr.epistudio.epysia.net.replication.OwnershipPolicy;

public final class NetworkRuntime implements NetworkEvents {
    private static final Set<Class<?>> PREDICTED_EXCLUSIONS = Set.of(Transform3D.class);
    private static final int HEARTBEAT_INTERVAL_TICKS = 10;
    private static final int MINIMUM_CLIENT_LEAD_TICKS = 2;
    private static final int MAXIMUM_CLIENT_LEAD_TICKS = 30;
    private static final int INPUT_WINDOW_MARGIN_TICKS = 15;
    private static final float INTEREST_CELL_SIZE_METERS = 16.0f;
    private static final String TRANSFORM_POSITION = "position";
    private static final String TRANSFORM_ROTATION = "rotation";

    private final NetworkStats stats = new NetworkStats();
    private final VoiceRouter voiceRouter = new VoiceRouter();
    private final InputRing localInputs = new InputRing();
    private final PredictionBuffer predictionBuffer = new PredictionBuffer();
    private final Reconciler reconciler = new Reconciler();
    private final Map<Integer, Vector3f> positionByNetworkId = new LinkedHashMap<>();
    private final Map<Integer, Vector3f> voicePositions = new LinkedHashMap<>();
    private final Map<Integer, Integer> voiceChannels = new LinkedHashMap<>();
    private final List<Integer> voiceListeners = new ArrayList<>();
    private final Set<Integer> serverMutedPeers = new LinkedHashSet<>();
    private final Logger logger;
    private final ReplicationRuntime replication;
    private final PrefabSpawner spawner;
    private final NetworkSession session;
    private VoiceRuntime voice;
    private EngineServices services;
    private NetworkConfig config = new NetworkConfig();
    private InputSampler sampler = InputSampler.forActions(new InputActions());
    private InputState currentInput;
    private int simulatedTick;
    private float localYaw;
    private float localPitch;
    private Scene activeScene = new Scene("network-placeholder");
    private float fixedTimestepSeconds = 1.0f / 60.0f;
    private boolean started;
    private VoiceRoutingContext cachedRoutingContext = VoiceRoutingContext.empty();
    private boolean duckingActive;
    private int ticksSinceSnapshot;
    private long reconnectToken;
    private Optional<DiagnosticsServer> diagnostics = Optional.empty();
    private float uptimeSeconds;
    private final NetworkInterestGrid interestGrid = new NetworkInterestGrid(INTEREST_CELL_SIZE_METERS);
    private int routingContextTick = -1;

    public NetworkRuntime(Logger logger) {
        this.logger = logger;
        this.replication = new ReplicationRuntime(logger);
        this.spawner = new PrefabSpawner(logger);
        this.session = new NetworkSession(stats, logger);
        this.voice = new VoiceRuntime(config.voice(), stats, logger);
        this.session.setEvents(this);
    }

    public void initialize(EngineServices engineServices) {
        this.services = engineServices;
    }

    public NetworkStats stats() {
        return stats;
    }

    public NetworkRole role() {
        return session.role();
    }

    public int localPeer() {
        return session.localPeer();
    }

    public List<NetworkPeer> peers() {
        return session.peers();
    }

    public NetworkConfig config() {
        return config;
    }

    public VoiceRuntime voice() {
        return voice;
    }

    public NetworkSession session() {
        return session;
    }

    public ReplicationRuntime replication() {
        return replication;
    }

    public void setFixedTimestepSeconds(float seconds) {
        this.fixedTimestepSeconds = Math.max(0.001f, seconds);
    }

    public void startServer(NetworkConfig configuration, boolean listenServer) {
        session.stop();
        prepare(configuration, listenServer);
        session.startServer(configuration, identity(), listenServer);
    }

    public void connect(NetworkConfig configuration, String host, int port) {
        session.stop();
        prepare(configuration, true);
        session.connect(configuration, identity(), host, port);
    }

    private void prepare(NetworkConfig configuration, boolean wantsAudioDevices) {
        this.config = configuration;
        this.activeScene = services == null ? activeScene : services.scene();
        this.voice = new VoiceRuntime(configuration.voice(), stats, logger);
        replication.reset();
        replication.build(activeScene);
        localInputs.clear();
        predictionBuffer.clear();
        voice.start(wantsAudioDevices);
        this.sampler = InputSampler.forActions(services == null
                ? new InputActions()
                : services.inputActions());
        replication.objects().assignSceneIds(activeScene);
        started = true;
    }

    private SessionIdentity identity() {
        return SessionIdentity.of(replication.table().hash(), replication.remoteProcedures().hash(),
                voice.codecIdentity(), config.displayName(), config.joinSecret())
                .withReconnectToken(reconnectToken);
    }

    public java.util.Map<Integer, String> roster() {
        return session.roster();
    }

    public void setLocalLook(float yaw, float pitch) {
        localYaw = yaw;
        localPitch = pitch;
    }

    public long reconnectToken() {
        return reconnectToken;
    }

    public void disconnect() {
        if (!started) {
            return;
        }
        session.stop();
    }

    public void receiveTick(Scene scene, float deltaTimeSeconds) {
        this.activeScene = scene;
        if (!started) {
            return;
        }
        session.poll(deltaTimeSeconds);
        if (session.role().isClient()) {
            replication.applyToScene(session.localPeer(), interpolatedTick(), PREDICTED_EXCLUSIONS);
            reconcileOwnedObjects();
        }
        placeVoiceSources(scene);
        voice.playbackTick(deltaTimeSeconds);
    }

    private float interpolatedTick() {
        int delay = Math.max(config.interpolationDelayTicks(), config.snapshotIntervalTicks() + 1);
        return replication.lastAppliedSnapshotTick() - delay;
    }

    public void sendTick(Scene scene, InputState input, float deltaTimeSeconds) {
        this.activeScene = scene;
        if (!started) {
            return;
        }
        if (session.role().isServer() && dueForSnapshot()) {
            broadcastSnapshots(scene);
        }
        publishDiagnostics(deltaTimeSeconds);
        sendLocalVoice(input, deltaTimeSeconds);
        if (session.tick() % HEARTBEAT_INTERVAL_TICKS == 0) {
            session.sendHeartbeats();
        }
    }

    public void setCurrentInput(InputState input) {
        this.currentInput = input;
    }

    public void fixedStep(Scene scene, float fixedStepSeconds) {
        this.activeScene = scene;
        if (!started) {
            return;
        }
        applyLocalSimulationOwnership();
        if (session.role().isServer()) {
            resolvePeerInputs();
            applyPeerInputsToObjects(fixedStepSeconds);
        }
        if (session.role().isClient() && currentInput != null) {
            driveLocalPrediction(fixedStepSeconds);
        }
        simulatedTick = session.tick();
        session.advanceTick();
        if (session.role().isClient()) {
            requestClockCatchUp();
        }
    }

    private void requestClockCatchUp() {
        int pending = session.pendingTickAdjustment();
        if (pending == 0 || services == null) {
            return;
        }
        int step = pending > 0 ? 1 : -1;
        services.requestCatchUpSteps(step);
        session.consumeTickAdjustment(step);
    }

    @Override
    public void onTickResynchronised() {
        predictionBuffer.clear();
        localInputs.clear();
    }

    private void driveLocalPrediction(float fixedStepSeconds) {
        InputSample sample = sampler.sample(session.tick(), services.inputActions(), currentInput,
                localYaw, localPitch);
        localInputs.push(sample);
        ownedPredictedObject().ifPresent(gameObject -> {
            for (PredictedMovement mover : predictedMoversOf(gameObject)) {
                mover.simulatePredictedStep(sample, fixedStepSeconds);
            }
        });
        recordPredictionState();
        sendInputBatch();
    }

    private void applyLocalSimulationOwnership() {
        boolean authoritative = session.role().isServer();
        for (int networkId : replication.objects().networkIds()) {
            replication.objects().find(networkId)
                    .ifPresent(gameObject -> applySimulationFlag(gameObject, authoritative));
        }
    }

    private void applySimulationFlag(GameObject gameObject, boolean authoritative) {
        CharacterControllerComponent controller =
                gameObject.getComponentOrNull(CharacterControllerComponent.class);
        NetworkObject networkObject = gameObject.getComponentOrNull(NetworkObject.class);
        if (controller == null || networkObject == null) {
            return;
        }
        controller.setSimulated(authoritative || networkObject.isOwnedBy(session.localPeer()));
    }

    private void applyPeerInputsToObjects(float fixedStepSeconds) {
        for (int networkId : replication.objects().networkIds()) {
            replication.objects().find(networkId).ifPresent(gameObject ->
                    applyOwnerInput(gameObject, fixedStepSeconds));
        }
    }

    private void applyOwnerInput(GameObject gameObject, float fixedStepSeconds) {
        NetworkObject networkObject = gameObject.getComponentOrNull(NetworkObject.class);
        if (networkObject == null || networkObject.ownerPeer() == session.localPeer()) {
            return;
        }
        session.peer(networkObject.ownerPeer()).ifPresent(peer -> {
            for (PredictedMovement mover : predictedMoversOf(gameObject)) {
                mover.simulatePredictedStep(peer.currentInput(), fixedStepSeconds);
            }
        });
    }

    private void forgetObjectEverywhere(int networkId) {
        replication.forget(networkId);
        for (NetworkPeer peer : session.peers()) {
            peer.forgetObject(networkId);
        }
    }

    private void resolvePeerInputs() {
        for (NetworkPeer peer : session.peers()) {
            if (peer.handshakeComplete() && !peer.resolveInputFor(session.tick())) {
                stats.recordMissingInput();
            }
        }
    }

    private void publishDiagnostics(float deltaTimeSeconds) {
        if (diagnostics.isEmpty()) {
            return;
        }
        uptimeSeconds += deltaTimeSeconds;
        diagnostics.get().publish(new MetricsSnapshot(session.role().isActive(), session.role().name(),
                session.peers().size(), session.tick(), (long) uptimeSeconds,
                stats.counters(), peerMetrics()));
    }

    private List<MetricsSnapshot.PeerMetrics> peerMetrics() {
        List<MetricsSnapshot.PeerMetrics> metrics = new ArrayList<>();
        for (NetworkPeer peer : session.peers()) {
            PeerLatency latency = stats.latencyOf(peer.id());
            metrics.add(new MetricsSnapshot.PeerMetrics(peer.id(), peer.displayName(),
                    latency.roundTripSeconds(), latency.jitterSeconds()));
        }
        return metrics;
    }

    public boolean startDiagnostics(String host, int port) {
        DiagnosticsServer server = new DiagnosticsServer(logger);
        if (!server.start(host, port)) {
            return false;
        }
        diagnostics = Optional.of(server);
        return true;
    }

    private void sendInputBatch() {
        List<InputSample> redundant = localInputs.lastSamples(config.redundantInputSamples());
        session.sendToServer(NetChannel.UNRELIABLE, writer -> {
            writer.writeMessageType(MessageType.INPUT_BATCH);
            writer.writeInt(replication.lastAppliedSnapshotTick());
            writer.writeVarInt(redundant.size());
            redundant.forEach(entry -> entry.write(writer));
        });
    }

    private void recordPredictionState() {
        ownedPredictedObject().flatMap(this::transformOf).ifPresent(transform ->
                predictionBuffer.record(session.tick(), PredictedTransform.capturedFrom(transform)));
    }

    private Optional<Transform3D> transformOf(GameObject gameObject) {
        return Optional.ofNullable(gameObject.getComponentOrNull(Transform3D.class));
    }

    private Optional<GameObject> ownedPredictedObject() {
        for (int networkId : replication.objects().networkIds()) {
            Optional<GameObject> candidate = replication.objects().find(networkId).filter(this::isPredictedByLocal);
            if (candidate.isPresent()) {
                return candidate;
            }
        }
        return Optional.empty();
    }

    private boolean isPredictedByLocal(GameObject gameObject) {
        NetworkObject networkObject = gameObject.getComponentOrNull(NetworkObject.class);
        return networkObject != null
                && networkObject.predictOwnedMovement()
                && networkObject.isOwnedBy(session.localPeer());
    }

    private void reconcileOwnedObjects() {
        for (int networkId : replication.objects().networkIds()) {
            replication.objects().find(networkId)
                    .filter(this::isPredictedByLocal)
                    .ifPresent(gameObject -> reconcileOne(gameObject, networkId));
        }
    }

    private void reconcileOne(GameObject gameObject, int networkId) {
        Optional<Transform3D> transform = transformOf(gameObject);
        Optional<PredictedTransform> serverState = serverTransformOf(networkId);
        if (transform.isEmpty() || serverState.isEmpty()) {
            return;
        }
        int replayed = reconciler.reconcile(new ReconciliationRequest(transform.get(), predictionBuffer,
                localInputs, serverState.get(), PredictedTransform.capturedFrom(transform.get()),
                predictedMoversOf(gameObject), physicsRollbackFor(gameObject, networkId),
                replication.lastAppliedSnapshotTick(), fixedTimestepSeconds));
        if (replayed > 0) {
            stats.recordReconciliation(replayed);
        }
    }

    private void restoreServerCharacterState(GameObject gameObject, int networkId) {
        CharacterControllerComponent controller =
                gameObject.getComponentOrNull(CharacterControllerComponent.class);
        if (controller == null) {
            return;
        }
        replication.replicatedValue(networkId, NetworkCharacterController.class, "verticalVelocity")
                .ifPresent(value -> controller.setVerticalVelocity((Float) value));
        replication.replicatedValue(networkId, NetworkCharacterController.class, "grounded")
                .ifPresent(value -> controller.setGrounded((Boolean) value));
    }

    private PredictedPhysics physicsRollbackFor(GameObject gameObject, int networkId) {
        if (services == null) {
            return PredictedPhysics.NONE;
        }
        Optional<PhysicsSystem> physics = services.systems().find(PhysicsSystem.class);
        if (physics.isEmpty()) {
            return PredictedPhysics.NONE;
        }
        CharacterRollback character = new CharacterRollback(physics.get(), gameObject,
                () -> restoreServerCharacterState(gameObject, networkId));
        if (character.hasCharacter()) {
            return character;
        }
        if (gameObject.getComponentOrNull(RigidBodyComponent.class) == null) {
            return PredictedPhysics.NONE;
        }
        PhysicsRollback rollback = new PhysicsRollback(services.systems().get(PhysicsSystem.class), activeScene)
                .predicting(gameObject);
        return rollback.hasPredictedBody() ? rollback : PredictedPhysics.NONE;
    }

    private Optional<PredictedTransform> serverTransformOf(int networkId) {
        Optional<Object> position = replication.replicatedValue(networkId, Transform3D.class, TRANSFORM_POSITION);
        Optional<Object> rotation = replication.replicatedValue(networkId, Transform3D.class, TRANSFORM_ROTATION);
        if (position.isEmpty() || rotation.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(new PredictedTransform(new Vector3f((Vector3fc) position.get()),
                new Quaternionf((Quaternionfc) rotation.get())));
    }

    private static List<PredictedMovement> predictedMoversOf(GameObject gameObject) {
        List<PredictedMovement> movers = new ArrayList<>();
        for (IComponent component : gameObject.components()) {
            if (component instanceof PredictedMovement mover) {
                movers.add(mover);
            }
        }
        return movers;
    }

    private boolean dueForSnapshot() {
        ticksSinceSnapshot++;
        if (ticksSinceSnapshot < config.snapshotIntervalTicks()) {
            return false;
        }
        ticksSinceSnapshot = 0;
        return true;
    }

    private void broadcastSnapshots(Scene scene) {
        replication.objects().assignServerIdsForScene(scene);
        replication.captureServerState();
        Map<Integer, Integer> owners = replication.objects().ownersByNetworkId();
        refreshObjectPositions();
        interestGrid.rebuild(positionByNetworkId);
        for (NetworkPeer peer : session.peers()) {
            if (peer.handshakeComplete()) {
                sendSnapshotTo(peer, owners);
            }
        }
    }

    private void refreshObjectPositions() {
        positionByNetworkId.clear();
        for (int networkId : replication.objects().networkIdView()) {
            replication.objects().find(networkId)
                    .map(GameObject::transform3DOrNull)
                    .ifPresent(transform -> positionByNetworkId.put(networkId, transform.position()));
        }
    }

    private void sendSnapshotTo(NetworkPeer peer, Map<Integer, Integer> owners) {
        Optional<WorldState> baseline = peer.baselineForAcknowledgedTick();
        SnapshotRequest request = new SnapshotRequest(replication.serverState(),
                baseline.orElseGet(WorldState::new), owners, peer.id(), simulatedTick,
                baseline.map(ignored -> peer.acknowledgedSnapshotTick()).orElse(SnapshotRequest.NO_BASELINE),
                config.snapshotByteCeiling(), priorityFor(peer, owners), peer.sendGate(),
                config.networkTickRate(), interestFor(peer, owners));
        session.sendSnapshotToPeer(peer.id(), writer -> {
            writer.writeMessageType(MessageType.SNAPSHOT);
            WorldState delivered = replication.snapshotWriter().write(writer, request);
            peer.recordSentState(simulatedTick, delivered);
            recordSnapshotOutcome(peer, writer.position());
        });
    }

    private void recordSnapshotOutcome(NetworkPeer peer, int byteCount) {
        stats.recordSnapshot(byteCount);
        stats.recordCulledObjects(replication.snapshotWriter().culledObjects());
        for (int networkId : replication.snapshotWriter().writtenNetworkIds()) {
            peer.markIncluded(networkId, simulatedTick);
        }
        int dropped = replication.snapshotWriter().droppedObjects();
        if (dropped > 0) {
            stats.recordSnapshotTruncation(dropped);
        }
    }

    private SnapshotInterest interestFor(NetworkPeer peer, Map<Integer, Integer> owners) {
        float radius = config.interestRadiusMeters();
        if (radius <= 0.0f) {
            return SnapshotInterest.EVERYTHING;
        }
        Optional<Vector3f> viewer = viewerPositionOf(peer.id(), owners);
        if (viewer.isEmpty()) {
            return SnapshotInterest.EVERYTHING;
        }
        Vector3f origin = new Vector3f(viewer.get());
        Set<Integer> alwaysRelevant = alwaysRelevantTo(peer.id(), owners);
        return current -> interestGrid.within(origin, radius, alwaysRelevant);
    }

    private Set<Integer> alwaysRelevantTo(int peerId, Map<Integer, Integer> owners) {
        Set<Integer> relevant = new LinkedHashSet<>();
        for (Map.Entry<Integer, Integer> entry : owners.entrySet()) {
            if (entry.getValue() == peerId) {
                relevant.add(entry.getKey());
            }
        }
        for (int networkId : replication.objects().networkIdView()) {
            if (!interestGrid.positionedNetworkIds().contains(networkId)) {
                relevant.add(networkId);
            }
        }
        return relevant;
    }

    private SnapshotPriority priorityFor(NetworkPeer peer, Map<Integer, Integer> owners) {
        Optional<Vector3f> viewer = viewerPositionOf(peer.id(), owners);
        if (viewer.isEmpty()) {
            return SnapshotPriority.NONE;
        }
        Vector3f origin = new Vector3f(viewer.get());
        int currentTick = session.tick();
        return networkId -> scoreOf(origin, networkId, peer, currentTick);
    }

    private float scoreOf(Vector3f viewer, int networkId, NetworkPeer peer, int currentTick) {
        Vector3f position = positionByNetworkId.get(networkId);
        if (position == null) {
            return 0.0f;
        }
        int staleness = Math.max(0, peer.ticksSinceIncluded(networkId, currentTick));
        return viewer.distanceSquared(position) / (1.0f + staleness);
    }

    private Optional<Vector3f> viewerPositionOf(int peerId, Map<Integer, Integer> owners) {
        for (Map.Entry<Integer, Integer> entry : owners.entrySet()) {
            if (entry.getValue() == peerId && positionByNetworkId.containsKey(entry.getKey())) {
                return Optional.of(positionByNetworkId.get(entry.getKey()));
            }
        }
        return Optional.empty();
    }

    @Override
    public void onSnapshotReceived(NetReader reader) {
        Optional<SnapshotReader.ReadResult> applied = replication.readSnapshot(reader);
        if (applied.isEmpty()) {
            return;
        }
        int acknowledgedTick = applied.get().serverTick();
        synchroniseClientTick(acknowledgedTick);
        session.sendToServer(NetChannel.UNRELIABLE, writer -> {
            writer.writeMessageType(MessageType.ACK);
            writer.writeInt(acknowledgedTick);
        });
    }

    private void synchroniseClientTick(int latestServerTick) {
        session.synchroniseTick(latestServerTick + oneWayTicks() + clientLeadTicks());
    }

    private int clientLeadTicks() {
        PeerLatency latency = stats.latencyOf(PeerIds.SERVER);
        if (!latency.sampled()) {
            return config.interpolationDelayTicks() + MINIMUM_CLIENT_LEAD_TICKS;
        }
        int jitterTicks = Math.round(latency.jitterSeconds() * config.networkTickRate());
        return Math.clamp(oneWayTicks() + jitterTicks + MINIMUM_CLIENT_LEAD_TICKS,
                MINIMUM_CLIENT_LEAD_TICKS, MAXIMUM_CLIENT_LEAD_TICKS);
    }

    private int oneWayTicks() {
        PeerLatency latency = stats.latencyOf(PeerIds.SERVER);
        if (!latency.sampled()) {
            return 0;
        }
        return Math.round(latency.stableRoundTripSeconds() * 0.5f * config.networkTickRate());
    }

    public int clientLead() {
        return clientLeadTicks();
    }

    @Override
    public void onSpawnReceived(NetReader reader) {
        SpawnRecord record = SpawnRecord.read(reader);
        spawner.instantiate(services, activeScene, record).ifPresent(gameObject -> {
            NetworkObject networkObject = gameObject.getComponentOrNull(NetworkObject.class);
            replication.objects().register(gameObject, networkObject, record.networkId());
        });
    }

    @Override
    public void onDespawnReceived(NetReader reader) {
        int networkId = reader.readVarInt();
        replication.objects().find(networkId).ifPresent(activeScene::removeGameObject);
        forgetObjectEverywhere(networkId);
    }

    public Optional<GameObject> spawn(String prefabGuid, Vector3f position, Quaternionf rotation, int ownerPeer) {
        if (!session.role().isServer()) {
            logger.warn("[net] spawn was called on a peer that is not the server and was ignored");
            return Optional.empty();
        }
        SpawnRecord record = new SpawnRecord(replication.objects().allocateNetworkId(), prefabGuid,
                ownerPeer, position, rotation);
        Optional<GameObject> spawned = spawner.instantiate(services, activeScene, record);
        spawned.ifPresent(gameObject -> registerAndAnnounce(gameObject, record));
        return spawned;
    }

    private void registerAndAnnounce(GameObject gameObject, SpawnRecord record) {
        replication.objects().register(gameObject, gameObject.getComponentOrNull(NetworkObject.class),
                record.networkId());
        session.broadcastToPeers(NetChannel.RELIABLE, writer -> {
            writer.writeMessageType(MessageType.SPAWN);
            record.write(writer);
        });
    }

    public void muteOnServer(int peer, boolean muted) {
        if (!session.role().isServer()) {
            logger.warn("[net] server side mute was called on a peer that is not the server");
            return;
        }
        if (muted) {
            serverMutedPeers.add(peer);
            return;
        }
        serverMutedPeers.remove(peer);
    }

    public boolean isMutedOnServer(int peer) {
        return serverMutedPeers.contains(peer);
    }

    public void kick(int peer, DisconnectReason reason) {
        session.kick(peer, reason);
    }

    public void ban(int peer) {
        session.ban(peer);
    }

    public void despawn(GameObject gameObject) {
        NetworkObject networkObject = gameObject.getComponentOrNull(NetworkObject.class);
        if (networkObject == null || !session.role().isServer()) {
            return;
        }
        int networkId = networkObject.networkId();
        session.broadcastToPeers(NetChannel.RELIABLE, writer -> {
            writer.writeMessageType(MessageType.DESPAWN);
            writer.writeVarInt(networkId);
        });
        activeScene.removeGameObject(gameObject);
        forgetObjectEverywhere(networkId);
    }

    @Override
    public void onInputBatchReceived(NetworkPeer peer, NetReader reader) {
        peer.acknowledgeSnapshot(reader.readInt());
        int count = reader.requireCount(reader.readVarInt(), InputSample.MINIMUM_ENCODED_BYTES);
        for (int index = 0; index < count; index++) {
            acceptInputSample(peer, InputSample.read(reader));
        }
    }

    private void acceptInputSample(NetworkPeer peer, InputSample sample) {
        if (!peer.offerInput(sample, session.tick(), acceptableInputLeadTicks())) {
            stats.recordRejectedInput();
        }
    }

    private int acceptableInputLeadTicks() {
        return MAXIMUM_CLIENT_LEAD_TICKS + config.redundantInputSamples() + INPUT_WINDOW_MARGIN_TICKS;
    }

    public Optional<InputSample> inputOf(int peerId) {
        return session.peer(peerId).map(NetworkPeer::currentInput);
    }

    public void call(IComponent component, String methodName, Object... arguments) {
        Optional<Integer> index = replication.remoteProcedures().indexOf(component, methodName);
        if (index.isEmpty()) {
            logger.warn("[net.rpc] no @Rpc method named " + methodName + " on "
                    + component.getClass().getName());
            return;
        }
        RpcMethod method = replication.remoteProcedures().at(index.get()).orElseThrow();
        dispatchCall(component, method, index.get(), arguments);
    }

    private void dispatchCall(IComponent component, RpcMethod method, int index, Object[] arguments) {
        if (!started || !shouldSendRemotely(method)) {
            method.invokeOn(component, arguments);
            return;
        }
        int networkId = networkIdOf(component).orElse(NetworkObject.UNASSIGNED_ID);
        NetChannel channel = method.reliable() ? NetChannel.RELIABLE : NetChannel.UNRELIABLE;
        if (method.target() == RpcTarget.SERVER) {
            session.sendToServer(channel, writer -> writeCall(writer, index, networkId, method, arguments));
            return;
        }
        session.broadcastToPeers(channel, writer -> writeCall(writer, index, networkId, method, arguments));
        method.invokeOn(component, arguments);
    }

    private boolean shouldSendRemotely(RpcMethod method) {
        if (method.target() == RpcTarget.SERVER) {
            return !session.role().isServer();
        }
        return session.role().isServer();
    }

    private static void writeCall(NetWriter writer, int index,
                                  int networkId, RpcMethod method, Object[] arguments) {
        writer.writeMessageType(MessageType.RPC);
        writer.writeVarInt(index);
        writer.writeVarInt(networkId);
        method.writeArguments(writer, arguments);
    }

    private Optional<Integer> networkIdOf(IComponent component) {
        GameObject owner = component.ownerOrNull();
        if (owner == null) {
            return Optional.empty();
        }
        NetworkObject networkObject = owner.getComponentOrNull(NetworkObject.class);
        return networkObject == null ? Optional.empty() : Optional.of(networkObject.networkId());
    }

    @Override
    public void onRemoteProcedureCallReceived(int fromPeer, NetReader reader) {
        int index = reader.readVarInt();
        int networkId = reader.readVarInt();
        Optional<RpcMethod> method = replication.remoteProcedures().at(index);
        if (method.isEmpty()) {
            stats.recordUnknownMessage();
            return;
        }
        Object[] arguments = method.get().readArguments(reader);
        replication.objects().find(networkId)
                .ifPresent(gameObject -> invokeReceived(gameObject, method.get(), fromPeer, arguments));
    }

    private void invokeReceived(GameObject gameObject, RpcMethod method, int fromPeer, Object[] arguments) {
        if (!isCallerAllowed(gameObject, method, fromPeer)) {
            stats.recordRejectedRemoteProcedureCall();
            return;
        }
        for (IComponent component : gameObject.components()) {
            if (method.declaringType().isInstance(component)) {
                safeInvoke(method, component, arguments);
            }
        }
    }

    private void safeInvoke(RpcMethod method, IComponent component, Object[] arguments) {
        try {
            method.invokeOn(component, arguments);
        } catch (RuntimeException failure) {
            logger.error("[net.rpc] " + method.identity() + " threw on the receiving side", failure);
        }
    }

    private boolean isCallerAllowed(GameObject gameObject, RpcMethod method, int fromPeer) {
        if (method.target() != RpcTarget.SERVER) {
            return true;
        }
        NetworkObject networkObject = gameObject.getComponentOrNull(NetworkObject.class);
        return networkObject != null && networkObject.isOwnedBy(fromPeer);
    }

    private void sendLocalVoice(InputState input, float deltaTimeSeconds) {
        boolean pushToTalkDown = services.inputActions()
                .isDown(config.voice().pushToTalkAction(), input);
        for (VoiceFrame frame : voice.captureOutgoing(session.localPeer(), pushToTalkDown, deltaTimeSeconds)) {
            emitVoiceFrame(frame);
        }
    }

    private void emitVoiceFrame(VoiceFrame frame) {
        if (session.role().isServer()) {
            routeVoiceFrame(frame);
            return;
        }
        session.sendToServer(NetChannel.VOICE, writer -> {
            writer.writeMessageType(MessageType.VOICE);
            frame.write(writer);
        });
    }

    @Override
    public void onVoiceFrameReceived(int fromPeer, NetReader reader) {
        VoiceFrame frame = VoiceFrame.read(reader);
        if (!session.role().isServer()) {
            voice.receive(frame);
            return;
        }
        routeVoiceFrame(frame.withSpeaker(fromPeer));
    }

    private void routeVoiceFrame(VoiceFrame frame) {
        if (serverMutedPeers.contains(frame.speakerPeer())) {
            return;
        }
        VoiceRoutingContext context = routingContext();
        for (int listener : voiceRouter.listenersFor(frame, context)) {
            if (listener == session.localPeer()) {
                voice.receive(frame);
                continue;
            }
            session.sendToPeer(listener, NetChannel.VOICE, writer -> {
                writer.writeMessageType(MessageType.VOICE);
                frame.write(writer);
            });
        }
    }

    private VoiceRoutingContext routingContext() {
        if (routingContextTick == session.tick()) {
            return cachedRoutingContext;
        }
        routingContextTick = session.tick();
        cachedRoutingContext = buildRoutingContext();
        return cachedRoutingContext;
    }

    private VoiceRoutingContext buildRoutingContext() {
        voicePositions.clear();
        voiceChannels.clear();
        voiceListeners.clear();
        for (VoiceChatComponent voiceChat : activeScene.componentsOf(VoiceChatComponent.class)) {
            transformOfComponent(voiceChat).ifPresent(transform ->
                    voicePositions.put(voiceChat.effectiveSpeakerPeer(), transform.position()));
        }
        for (NetworkPeer peer : session.peers()) {
            voiceChannels.put(peer.id(), peer.voiceChannel());
            voiceListeners.add(peer.id());
        }
        addLocalListener(voiceChannels, voiceListeners);
        return new VoiceRoutingContext(config.voice().scope(), config.voice().hearingRadiusMeters(),
                voicePositions, voiceChannels, voiceListeners);
    }

    private void addLocalListener(Map<Integer, Integer> channels, List<Integer> listeners) {
        if (session.role() != NetworkRole.LISTEN_SERVER) {
            return;
        }
        channels.put(session.localPeer(), voice.localChannelId());
        listeners.add(session.localPeer());
    }

    private static Optional<Transform3D> transformOfComponent(IComponent component) {
        GameObject owner = component.ownerOrNull();
        return owner == null ? Optional.empty() : Optional.ofNullable(owner.getComponentOrNull(Transform3D.class));
    }

    private void placeVoiceSources(Scene scene) {
        applyVoiceDucking();
        if (config.voice().scope() != VoiceScope.PROXIMITY) {
            applyNonSpatialVolume();
            return;
        }
        for (VoiceChatComponent voiceChat : scene.componentsOf(VoiceChatComponent.class)) {
            int speaker = voiceChat.effectiveSpeakerPeer();
            voice.playbackOf(speaker).ifPresent(playback ->
                    transformOfComponent(voiceChat).ifPresent(transform ->
                            placeOne(playback, transform.position(), speaker, voiceChat.gain())));
        }
    }

    private void placeOne(VoicePlayback playback, Vector3f position, int speaker, float componentGain) {
        playback.placeAt(position.x, position.y, position.z, config.voice());
        playback.setGain(config.voice().playbackGain() * componentGain * voice.volumeOf(speaker));
        routeReverb(playback);
    }

    private void applyNonSpatialVolume() {
        for (NetworkPeer peer : session.peers()) {
            voice.playbackOf(peer.id()).ifPresent(playback -> {
                playback.setGain(config.voice().playbackGain() * voice.volumeOf(peer.id()));
                routeReverb(playback);
            });
        }
    }

    private void routeReverb(VoicePlayback playback) {
        if (!config.voice().reverbSendEnabled() || services == null) {
            return;
        }
        reverbSlot().ifPresent(playback::routeToReverb);
    }

    private Optional<Integer> reverbSlot() {
        return services.systems().find(AudioSystem.class).flatMap(AudioSystem::reverbSlotId);
    }

    private void applyVoiceDucking() {
        if (config.voice().duckedBusGain() >= 1.0f || services == null) {
            return;
        }
        boolean speaking = voice.anyoneSpeaking();
        if (speaking == duckingActive) {
            return;
        }
        duckingActive = speaking;
        services.systems().find(AudioSystem.class).ifPresent(audio -> {
            if (speaking) {
                audio.mixer().duck(AudioBus.MUSIC, config.voice().duckedBusGain(),
                        config.voice().duckFadeSeconds());
                return;
            }
            audio.mixer().restore(AudioBus.MUSIC, config.voice().duckFadeSeconds());
        });
    }

    @Override
    public void onSessionStarted(NetworkRole role) {
        for (Behaviour behaviour : activeScene.componentsOf(Behaviour.class)) {
            behaviour.onNetworkStart(role);
        }
    }

    @Override
    public void onSessionStopped() {
        for (Behaviour behaviour : activeScene.componentsOf(Behaviour.class)) {
            behaviour.onNetworkStop();
        }
        voice.stop();
        replication.reset();
        started = false;
    }

    @Override
    public void onPeerJoined(NetworkPeer peer) {
        announceExistingSpawnsTo(peer);
    }

    private void announceExistingSpawnsTo(NetworkPeer peer) {
        for (int networkId : replication.objects().networkIds()) {
            replication.objects().find(networkId)
                    .filter(gameObject -> isRuntimeSpawned(gameObject))
                    .ifPresent(gameObject -> sendSpawnTo(peer, gameObject, networkId));
        }
    }

    private static boolean isRuntimeSpawned(GameObject gameObject) {
        NetworkObject networkObject = gameObject.getComponentOrNull(NetworkObject.class);
        return networkObject != null && networkObject.spawnedAtRuntime();
    }

    private void sendSpawnTo(NetworkPeer peer, GameObject gameObject, int networkId) {
        NetworkObject networkObject = gameObject.getComponentOrNull(NetworkObject.class);
        Transform3D transform = gameObject.getComponentOrNull(Transform3D.class);
        Vector3f position = transform == null ? new Vector3f() : new Vector3f(transform.position());
        Quaternionf rotation = transform == null ? new Quaternionf() : new Quaternionf(transform.rotation());
        SpawnRecord record = new SpawnRecord(networkId, networkObject.prefabGuid(),
                networkObject.ownerPeer(), position, rotation);
        session.sendToPeer(peer.id(), NetChannel.RELIABLE, writer -> {
            writer.writeMessageType(MessageType.SPAWN);
            record.write(writer);
        });
    }

    @Override
    public void onPeerLeft(NetworkPeer peer, DisconnectReason reason) {
        voice.forgetPeer(peer.id());
        serverMutedPeers.remove(peer.id());
        if (config.reconnectGraceSeconds() > 0.0f && reason != DisconnectReason.KICKED
                && reason != DisconnectReason.BANNED) {
            return;
        }
        releaseOwnedObjects(peer.id());
    }

    @Override
    public List<Integer> ownedObjectsOf(int peerId) {
        List<Integer> owned = new ArrayList<>();
        for (Map.Entry<Integer, Integer> entry : replication.objects().ownersByNetworkId().entrySet()) {
            if (entry.getValue() == peerId) {
                owned.add(entry.getKey());
            }
        }
        return owned;
    }

    @Override
    public void onPeerReturned(NetworkPeer peer, List<Integer> ownedObjects) {
        for (int networkId : ownedObjects) {
            replication.objects().find(networkId).ifPresent(gameObject -> reclaim(gameObject, peer.id()));
        }
        announceExistingSpawnsTo(peer);
    }

    private void reclaim(GameObject gameObject, int peerId) {
        NetworkObject networkObject = gameObject.getComponentOrNull(NetworkObject.class);
        if (networkObject == null) {
            return;
        }
        networkObject.assignOwner(peerId);
        notifyOwnershipChanged(gameObject, peerId);
    }

    @Override
    public void onReconnectWindowClosed(int peerId, List<Integer> ownedObjects) {
        logger.info("[net] peer " + peerId + " did not come back, releasing what it owned");
        releaseOwnedObjects(peerId);
    }

    private void releaseOwnedObjects(int peerId) {
        for (int networkId : replication.objects().networkIds()) {
            replication.objects().find(networkId).ifPresent(gameObject -> releaseOwnership(gameObject, peerId));
        }
    }

    private void releaseOwnership(GameObject gameObject, int leavingPeer) {
        NetworkObject networkObject = gameObject.getComponentOrNull(NetworkObject.class);
        if (networkObject == null || !networkObject.isOwnedBy(leavingPeer)) {
            return;
        }
        if (networkObject.onOwnerDisconnect() == OwnershipPolicy.DESTROY) {
            despawn(gameObject);
            return;
        }
        networkObject.assignOwner(PeerIds.SERVER);
        notifyOwnershipChanged(gameObject, PeerIds.SERVER);
    }

    private static void notifyOwnershipChanged(GameObject gameObject, int ownerPeer) {
        for (IComponent component : gameObject.components()) {
            if (component instanceof Behaviour behaviour) {
                behaviour.onOwnershipChanged(ownerPeer);
            }
        }
    }

    @Override
    public void onLocalPeerAssigned(int peerId, int serverTick) {
        reconnectToken = session.localReconnectToken();
        for (Behaviour behaviour : activeScene.componentsOf(Behaviour.class)) {
            behaviour.onOwnershipChanged(peerId);
        }
    }

    public void shutdown() {
        disconnect();
        voice.stop();
        diagnostics.ifPresent(DiagnosticsServer::stop);
        diagnostics = Optional.empty();
    }
}
