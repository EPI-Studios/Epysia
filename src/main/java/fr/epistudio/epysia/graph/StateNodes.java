package fr.epistudio.epysia.graph;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

public final class StateNodes {

    public static final String STATE = "state.state";
    public static final String TRANSITION = "state.transition";

    public static final String STATE_NAME_SETTING = "stateName";
    public static final String INITIAL_SETTING = "initialState";
    public static final String ALWAYS_SETTING = "always";
    public static final String THRESHOLD_SETTING = "threshold";
    public static final String PRIORITY_SETTING = "priority";

    public static final String STATE_IN_PIN = "In";
    public static final String TRANSITIONS_PIN = "Transitions";
    public static final String ON_ENTER_PIN = "On Enter";
    public static final String ON_UPDATE_PIN = "On Update";
    public static final String ON_EXIT_PIN = "On Exit";
    public static final String FROM_PIN = "From";
    public static final String TO_PIN = "To";

    public static final String CATEGORY = "State Machine";
    public static final String DEFAULT_STATE_NAME = "State";
    public static final String DEFAULT_OPERATOR = ">";
    public static final int DEFAULT_PRIORITY = 1;

    private StateNodes() {
    }

    public static void registerInto(GraphNodeRegistry registry) {
        registry.register(stateDefinition());
        registry.register(transitionDefinition());
    }

    private static NodeDefinition stateDefinition() {
        return new NodeDefinition(STATE, "State", CATEGORY, false, false,
                List.of(PinDefinition.exec(STATE_IN_PIN)),
                List.of(PinDefinition.exec(TRANSITIONS_PIN),
                        PinDefinition.exec(ON_ENTER_PIN),
                        PinDefinition.exec(ON_UPDATE_PIN),
                        new PinDefinition(BuiltinNodes.DELTA_TIME_PIN, PinType.FLOAT),
                        PinDefinition.exec(ON_EXIT_PIN)),
                List.of(new NodeSetting(STATE_NAME_SETTING, SettingKind.TEXT, DEFAULT_STATE_NAME),
                        new NodeSetting(INITIAL_SETTING, SettingKind.TOGGLE, false)),
                context -> {
                });
    }

    private static NodeDefinition transitionDefinition() {
        return new NodeDefinition(TRANSITION, "Transition", CATEGORY, false, false,
                List.of(PinDefinition.exec(FROM_PIN),
                        new PinDefinition(BuiltinNodes.CONDITION_PIN, PinType.BOOLEAN)),
                List.of(PinDefinition.exec(TO_PIN)),
                List.of(new NodeSetting(ALWAYS_SETTING, SettingKind.TOGGLE, true),
                        new NodeSetting(BuiltinNodes.VARIABLE_NAME_SETTING, SettingKind.VARIABLE_NAME, ""),
                        new NodeSetting(BuiltinNodes.OPERATOR_SETTING, SettingKind.COMPARISON, DEFAULT_OPERATOR),
                        new NodeSetting(THRESHOLD_SETTING, SettingKind.NUMBER, 0.0f),
                        new NodeSetting(PRIORITY_SETTING, SettingKind.WHOLE_NUMBER, DEFAULT_PRIORITY)),
                context -> {
                });
    }

    public static boolean isState(GraphNode node) {
        return node.typeKey().equals(STATE);
    }

    public static boolean isTransition(GraphNode node) {
        return node.typeKey().equals(TRANSITION);
    }

    public static String stateName(GraphNode node) {
        String name = GraphValues.asString(
                node.values().getOrDefault(STATE_NAME_SETTING, DEFAULT_STATE_NAME));
        return name.isEmpty() ? DEFAULT_STATE_NAME : name;
    }

    public static boolean markedInitial(GraphNode node) {
        return GraphValues.asBoolean(node.values().get(INITIAL_SETTING));
    }

    public static boolean alwaysTaken(GraphNode transition) {
        return GraphValues.asBoolean(transition.values().getOrDefault(ALWAYS_SETTING, Boolean.TRUE));
    }

    public static Optional<GraphNode> initialState(GraphAsset asset) {
        List<GraphNode> states = asset.nodesOfType(STATE);
        for (GraphNode state : states) {
            if (markedInitial(state)) {
                return Optional.of(state);
            }
        }
        return states.isEmpty() ? Optional.empty() : Optional.of(states.get(0));
    }

    public static List<GraphNode> outgoingTransitions(GraphAsset asset, GraphNode state) {
        List<GraphNode> transitions = new ArrayList<>();
        for (GraphEdge edge : asset.edgesFrom(state.id(), TRANSITIONS_PIN)) {
            asset.findNode(edge.toNode()).filter(StateNodes::isTransition).ifPresent(transitions::add);
        }
        transitions.sort(Comparator.comparingInt(StateNodes::priorityOf));
        return transitions;
    }

    public static int priorityOf(GraphNode transition) {
        Object value = transition.values().get(PRIORITY_SETTING);
        return value instanceof Number number ? number.intValue() : DEFAULT_PRIORITY;
    }

    public static Optional<GraphNode> transitionTarget(GraphAsset asset, GraphNode transition) {
        for (GraphEdge edge : asset.edgesFrom(transition.id(), TO_PIN)) {
            Optional<GraphNode> target = asset.findNode(edge.toNode()).filter(StateNodes::isState);
            if (target.isPresent()) {
                return target;
            }
        }
        return Optional.empty();
    }

    public static String conditionSummary(GraphNode transition) {
        if (alwaysTaken(transition)) {
            return "Always";
        }
        String variable = GraphValues.asString(
                transition.values().getOrDefault(BuiltinNodes.VARIABLE_NAME_SETTING, ""));
        String operator = GraphValues.asString(
                transition.values().getOrDefault(BuiltinNodes.OPERATOR_SETTING, DEFAULT_OPERATOR));
        float threshold = GraphValues.asFloat(
                transition.values().getOrDefault(THRESHOLD_SETTING, 0.0f));
        String shownVariable = variable.isEmpty() ? "?" : variable;
        return shownVariable + " " + operator + " " + threshold;
    }
}
