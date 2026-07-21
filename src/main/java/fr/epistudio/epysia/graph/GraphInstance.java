package fr.epistudio.epysia.graph;

import fr.epistudio.epysia.gameobjects.GameObject;

import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

public final class GraphInstance {

    private final GraphAsset asset;
    private final String sourcePath;
    private final GameObject self;
    private final Map<String, Object> variableValues = new LinkedHashMap<>();
    private final Map<Integer, Map<String, Object>> nodeOutputs = new HashMap<>();
    private final Set<Integer> memoizedNodes = new HashSet<>();
    private final Set<Integer> evaluatingNodes = new HashSet<>();
    private final Set<String> warnedTypeKeys = new HashSet<>();
    private final Map<Integer, Long> nodeFireNanos = new HashMap<>();
    private final Map<Integer, Integer> nodeFireCounts = new HashMap<>();
    private final Map<GraphEdge, Long> edgeFireNanos = new HashMap<>();
    private static final int NO_ACTIVE_STATE = -1;

    private int budgetRemaining;
    private boolean budgetWarningIssued;
    private boolean startFired;
    private int activeStateId = NO_ACTIVE_STATE;
    private long stateEnteredNanos;

    public GraphInstance(GraphAsset asset, String sourcePath, GameObject self,
                         Map<String, Object> variableOverrides) {
        this.asset = asset;
        this.sourcePath = sourcePath;
        this.self = self;
        initializeVariables(variableOverrides);
    }

    private void initializeVariables(Map<String, Object> variableOverrides) {
        for (GraphVariable variable : asset.variables()) {
            Object overridden = variableOverrides.get(variable.name());
            Object initial = overridden == null ? variable.defaultValue() : overridden;
            variableValues.put(variable.name(), GraphValues.coerce(initial, variable.type()));
        }
    }

    public GraphAsset asset() {
        return asset;
    }

    public String sourcePath() {
        return sourcePath;
    }

    public GameObject self() {
        return self;
    }

    public Object variableValue(String name) {
        Object value = variableValues.get(name);
        return value == null ? GraphValues.ABSENT : value;
    }

    public void setVariableValue(String name, Object value) {
        PinType type = asset.findVariable(name).map(GraphVariable::type).orElse(PinType.OBJECT);
        variableValues.put(name, GraphValues.coerce(value, type));
    }

    public Object output(int nodeId, String pinName) {
        Map<String, Object> outputs = nodeOutputs.get(nodeId);
        if (outputs == null) {
            return GraphValues.ABSENT;
        }
        Object value = outputs.get(pinName);
        return value == null ? GraphValues.ABSENT : value;
    }

    public void setOutput(int nodeId, String pinName, Object value) {
        nodeOutputs.computeIfAbsent(nodeId, ignored -> new HashMap<>()).put(pinName, value);
    }

    public void beginPass() {
        memoizedNodes.clear();
        evaluatingNodes.clear();
    }

    public boolean isMemoized(int nodeId) {
        return memoizedNodes.contains(nodeId);
    }

    public void markMemoized(int nodeId) {
        memoizedNodes.add(nodeId);
    }

    public boolean beginEvaluating(int nodeId) {
        return evaluatingNodes.add(nodeId);
    }

    public void endEvaluating(int nodeId) {
        evaluatingNodes.remove(nodeId);
    }

    public boolean warnOnceFor(String typeKey) {
        return warnedTypeKeys.add(typeKey);
    }

    public void resetTickBudget(int budget) {
        budgetRemaining = budget;
        budgetWarningIssued = false;
    }

    public boolean consumeBudget() {
        if (budgetRemaining <= 0) {
            return false;
        }
        budgetRemaining--;
        return true;
    }

    public boolean markBudgetWarningIssued() {
        if (budgetWarningIssued) {
            return false;
        }
        budgetWarningIssued = true;
        return true;
    }

    public boolean startFired() {
        return startFired;
    }

    public void markStartFired() {
        startFired = true;
    }

    public boolean hasActiveState() {
        return activeStateId != NO_ACTIVE_STATE;
    }

    public int activeStateId() {
        return activeStateId;
    }

    public void setActiveState(int nodeId) {
        activeStateId = nodeId;
        stateEnteredNanos = System.nanoTime();
    }

    public void clearActiveState() {
        activeStateId = NO_ACTIVE_STATE;
        stateEnteredNanos = 0L;
    }

    public long stateEnteredNanos() {
        return stateEnteredNanos;
    }

    public void stampNodeFire(int nodeId) {
        nodeFireNanos.put(nodeId, System.nanoTime());
        nodeFireCounts.merge(nodeId, 1, Integer::sum);
    }

    public int nodeFireCount(int nodeId) {
        return nodeFireCounts.getOrDefault(nodeId, 0);
    }

    public void stampEdgeFire(GraphEdge edge) {
        edgeFireNanos.put(edge, System.nanoTime());
    }

    public long nodeFireNanos(int nodeId) {
        return nodeFireNanos.getOrDefault(nodeId, 0L);
    }

    public long edgeFireNanos(GraphEdge edge) {
        return edgeFireNanos.getOrDefault(edge, 0L);
    }
}
