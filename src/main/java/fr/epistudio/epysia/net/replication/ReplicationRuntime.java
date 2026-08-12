package fr.epistudio.epysia.net.replication;

import fr.epistudio.epysia.components.IComponent;
import fr.epistudio.epysia.components.transforms.Transform3D;
import fr.epistudio.epysia.gameobjects.GameObject;
import fr.epistudio.epysia.logging.Logger;
import fr.epistudio.epysia.net.protocol.NetReader;
import fr.epistudio.epysia.net.rpc.RpcRegistry;
import fr.epistudio.epysia.reflection.ComponentRegistry;
import fr.epistudio.epysia.net.prediction.PredictedMovement;
import fr.epistudio.epysia.scene.Scene;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.HashSet;
import java.util.Set;
import java.util.TreeMap;

public final class ReplicationRuntime {
    private static final String TRANSFORM_POSITION_FIELD = "position";
    private static final String TRANSFORM_ROTATION_FIELD = "rotation";
    private static final int RETAINED_CLIENT_STATES = 64;
    private static final float TRANSFORM_POSITION_PRECISION = 0.001f;
    private static final float TRANSFORM_ROTATION_PRECISION = 1.0f;

    private final Logger logger;
    private final NetworkObjectRegistry objects = new NetworkObjectRegistry();
    private final WorldState serverState = new WorldState();
    private WorldState clientState = new WorldState();
    private final Map<Integer, InterpolationBuffer> interpolationByNetworkId = new LinkedHashMap<>();
    private final TreeMap<Integer, WorldState> appliedStatesByTick = new TreeMap<>();
    private ReplicationTable table = ReplicationTable.builder().build();
    private RpcRegistry remoteProcedures = RpcRegistry.builder().build();
    private SnapshotWriter snapshotWriter = new SnapshotWriter(table);
    private SnapshotReader snapshotReader = new SnapshotReader(table);
    private StateCapture stateCapture = new StateCapture(table);
    private StateApply stateApply = new StateApply(table);
    private int lastAppliedSnapshotTick = SnapshotRequest.NO_BASELINE;

    public ReplicationRuntime(Logger logger) {
        this.logger = logger;
    }

    public void build(Scene scene) {
        ReplicationTable.Builder tableBuilder = ReplicationTable.builder();
        RpcRegistry.Builder procedureBuilder = RpcRegistry.builder();
        collectComponentTypes(scene, tableBuilder, procedureBuilder);
        collectRegisteredTypes(tableBuilder, procedureBuilder);
        addDefaultTransformProperties(tableBuilder);
        addSynchronizerProperties(scene, tableBuilder);
        table = tableBuilder.build();
        remoteProcedures = procedureBuilder.build();
        rebuildCodecs();
        reportWarnings();
        logger.info("[net.replication] table=" + table.componentTypes().stream().map(Class::getSimpleName).toList());
    }

    private void rebuildCodecs() {
        snapshotWriter = new SnapshotWriter(table);
        snapshotReader = new SnapshotReader(table);
        stateCapture = new StateCapture(table);
        stateApply = new StateApply(table);
    }

    private void reportWarnings() {
        for (String warning : table.warnings()) {
            logger.warn("[net.replication] " + warning);
        }
        for (String warning : remoteProcedures.warnings()) {
            logger.warn("[net.rpc] " + warning);
        }
    }

    private static void collectComponentTypes(Scene scene, ReplicationTable.Builder tableBuilder,
                                              RpcRegistry.Builder procedureBuilder) {
        for (GameObject gameObject : scene.gameObjects()) {
            for (IComponent component : gameObject.components()) {
                tableBuilder.addComponentType(component.getClass());
                procedureBuilder.addComponentType(component.getClass());
            }
        }
    }

    private static void collectRegisteredTypes(ReplicationTable.Builder tableBuilder,
                                               RpcRegistry.Builder procedureBuilder) {
        for (ComponentRegistry.Entry entry : ComponentRegistry.populated().entries()) {
            tableBuilder.addComponentType(entry.componentClass());
            procedureBuilder.addComponentType(entry.componentClass());
        }
    }

    private static void addDefaultTransformProperties(ReplicationTable.Builder tableBuilder) {
        tableBuilder.addSynchronizedProperty(Transform3D.class, new SynchronizedProperty()
                .setComponentType(Transform3D.class.getName())
                .setFieldName(TRANSFORM_POSITION_FIELD)
                .setInterpolate(true)
                .setPrecision(TRANSFORM_POSITION_PRECISION));
        tableBuilder.addSynchronizedProperty(Transform3D.class, new SynchronizedProperty()
                .setComponentType(Transform3D.class.getName())
                .setFieldName(TRANSFORM_ROTATION_FIELD)
                .setInterpolate(true)
                .setPrecision(TRANSFORM_ROTATION_PRECISION));
    }

    private static void addSynchronizerProperties(Scene scene, ReplicationTable.Builder tableBuilder) {
        for (NetworkSynchronizer synchronizer : scene.componentsOf(NetworkSynchronizer.class)) {
            GameObject owner = synchronizer.ownerOrNull();
            if (owner == null) {
                continue;
            }
            for (SynchronizedProperty property : synchronizer.properties()) {
                resolveAndAdd(tableBuilder, owner, property);
            }
        }
    }

    private static void resolveAndAdd(ReplicationTable.Builder tableBuilder, GameObject owner,
                                      SynchronizedProperty property) {
        if (!property.isResolvable()) {
            return;
        }
        ReplicationTable.findComponentNamed(owner, property.componentType())
                .ifPresent(component -> tableBuilder.addSynchronizedProperty(component.getClass(), property));
    }

    public ReplicationTable table() {
        return table;
    }

    public RpcRegistry remoteProcedures() {
        return remoteProcedures;
    }

    public NetworkObjectRegistry objects() {
        return objects;
    }

    public WorldState serverState() {
        return serverState;
    }

    public WorldState clientState() {
        return clientState;
    }

    public SnapshotWriter snapshotWriter() {
        return snapshotWriter;
    }

    public int lastAppliedSnapshotTick() {
        return lastAppliedSnapshotTick;
    }

    public void captureServerState() {
        for (int networkId : objects.networkIdView()) {
            objects.find(networkId).ifPresent(gameObject ->
                    stateCapture.capture(gameObject, networkId, serverState));
        }
        serverState.retainOnly(objects.networkIdView());
    }

    public Optional<SnapshotReader.ReadResult> readSnapshot(NetReader reader) {
        Optional<SnapshotReader.ReadResult> result = snapshotReader.read(reader, this::appliedStateAt);
        if (result.isEmpty() || result.get().serverTick() <= lastAppliedSnapshotTick) {
            return Optional.empty();
        }
        acceptSnapshot(result.get());
        return result;
    }

    private void acceptSnapshot(SnapshotReader.ReadResult result) {
        clientState = result.state();
        lastAppliedSnapshotTick = result.serverTick();
        rememberAppliedState(result.serverTick());
        recordInterpolationSamples(result);
    }

    private Optional<WorldState> appliedStateAt(int tick) {
        return Optional.ofNullable(appliedStatesByTick.get(tick));
    }

    private void rememberAppliedState(int tick) {
        appliedStatesByTick.put(tick, clientState.shallowCopy());
        while (appliedStatesByTick.size() > RETAINED_CLIENT_STATES) {
            appliedStatesByTick.pollFirstEntry();
        }
    }

    private void recordInterpolationSamples(SnapshotReader.ReadResult result) {
        for (int networkId : result.touchedNetworkIds()) {
            clientState.find(networkId).ifPresent(state -> interpolationByNetworkId
                    .computeIfAbsent(networkId, ignored -> new InterpolationBuffer())
                    .push(result.serverTick(), state));
        }
    }

    public void applyToScene(int localPeer, float interpolatedTick, Set<Class<?>> ownedExclusions) {
        for (int networkId : clientState.networkIds()) {
            objects.find(networkId).ifPresent(gameObject ->
                    applyToObject(gameObject, networkId, localPeer, interpolatedTick, ownedExclusions));
        }
    }

    private void applyToObject(GameObject gameObject, int networkId, int localPeer,
                               float interpolatedTick, Set<Class<?>> ownedExclusions) {
        NetworkObject networkObject = gameObject.getComponentOrNull(NetworkObject.class);
        boolean predictedLocally = networkObject != null
                && networkObject.isOwnedBy(localPeer)
                && networkObject.predictOwnedMovement();
        if (predictedLocally) {
            Set<Class<?>> excluded = predictedExclusionsFor(gameObject, ownedExclusions);
            clientState.find(networkId).ifPresent(state -> stateApply.apply(gameObject, state, excluded));
            return;
        }
        applyInterpolated(gameObject, networkId, interpolatedTick);
    }

    private static Set<Class<?>> predictedExclusionsFor(GameObject gameObject,
                                                        Set<Class<?>> ownedExclusions) {
        Set<Class<?>> excluded = new HashSet<>(ownedExclusions);
        for (IComponent component : gameObject.components()) {
            if (component instanceof PredictedMovement) {
                excluded.add(component.getClass());
            }
        }
        return excluded;
    }

    private void applyInterpolated(GameObject gameObject, int networkId, float interpolatedTick) {
        InterpolationBuffer buffer = interpolationByNetworkId.get(networkId);
        if (buffer == null || buffer.isEmpty()) {
            clientState.find(networkId).ifPresent(state -> stateApply.apply(gameObject, state, Set.of()));
            return;
        }
        buffer.blendAt(interpolatedTick).ifPresent(blend ->
                stateApply.applyBlended(gameObject, blend.from(), blend.to(), blend.alpha(), Set.of()));
    }

    public Optional<Object> replicatedValue(int networkId, Class<?> componentType, String fieldName) {
        int componentIndex = table.indexOf(componentType);
        if (componentIndex < 0) {
            return Optional.empty();
        }
        List<ReplicatedField> fields = table.fieldsFor(componentType);
        return clientState.find(networkId)
                .flatMap(object -> object.find(componentIndex))
                .flatMap(state -> valueOf(state, fields, fieldName));
    }

    private static Optional<Object> valueOf(WorldState.ComponentState state, List<ReplicatedField> fields,
                                            String fieldName) {
        for (int index = 0; index < fields.size() && index < state.fieldCount(); index++) {
            if (!fields.get(index).fieldName().equals(fieldName)) {
                continue;
            }
            Object value = state.valueAt(index);
            return value == WorldState.ABSENT ? Optional.empty() : Optional.of(value);
        }
        return Optional.empty();
    }

    public void forget(int networkId) {
        clientState.remove(networkId);
        serverState.remove(networkId);
        interpolationByNetworkId.remove(networkId);
        objects.unregister(networkId);
    }

    public void reset() {
        clientState.clear();
        serverState.clear();
        interpolationByNetworkId.clear();
        appliedStatesByTick.clear();
        objects.clear();
        lastAppliedSnapshotTick = SnapshotRequest.NO_BASELINE;
    }
}
