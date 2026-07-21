package fr.epistudio.epysia.graph;

import fr.epistudio.epysia.EngineServices;

import java.util.Optional;

public final class StateMachineExecutor {

    private static final float TRUE_NUMERIC_VALUE = 1.0f;

    private final GraphInterpreter interpreter;

    public StateMachineExecutor(GraphInterpreter interpreter) {
        this.interpreter = interpreter;
    }

    public void step(GraphInstance instance, float deltaTimeSeconds, EngineServices services) {
        if (!instance.hasActiveState()) {
            enterInitialState(instance, services);
            return;
        }
        Optional<GraphNode> current = instance.asset().findNode(instance.activeStateId());
        if (current.isEmpty()) {
            instance.clearActiveState();
            return;
        }
        stepFrom(instance, current.get(), deltaTimeSeconds, services);
    }

    private void enterInitialState(GraphInstance instance, EngineServices services) {
        StateNodes.initialState(instance.asset())
                .ifPresent(initial -> enterState(instance, initial, services));
    }

    private void stepFrom(GraphInstance instance, GraphNode current,
                          float deltaTimeSeconds, EngineServices services) {
        instance.beginPass();
        for (GraphNode transition : StateNodes.outgoingTransitions(instance.asset(), current)) {
            Optional<GraphNode> target = StateNodes.transitionTarget(instance.asset(), transition);
            if (target.isPresent() && conditionSatisfied(instance, transition, services)) {
                performTransition(instance, current, transition, target.get(), services);
                return;
            }
        }
        fireUpdate(instance, current, deltaTimeSeconds, services);
    }

    private void performTransition(GraphInstance instance, GraphNode from, GraphNode transition,
                                   GraphNode target, EngineServices services) {
        stampTransition(instance, from, transition, target);
        interpreter.resumeExec(instance, from, StateNodes.ON_EXIT_PIN, services);
        enterState(instance, target, services);
    }

    private static void stampTransition(GraphInstance instance, GraphNode from,
                                        GraphNode transition, GraphNode target) {
        instance.stampNodeFire(transition.id());
        instance.stampEdgeFire(new GraphEdge(from.id(), StateNodes.TRANSITIONS_PIN,
                transition.id(), StateNodes.FROM_PIN));
        instance.stampEdgeFire(new GraphEdge(transition.id(), StateNodes.TO_PIN,
                target.id(), StateNodes.STATE_IN_PIN));
    }

    private void enterState(GraphInstance instance, GraphNode state, EngineServices services) {
        instance.setActiveState(state.id());
        interpreter.resumeExec(instance, state, StateNodes.ON_ENTER_PIN, services);
    }

    private void fireUpdate(GraphInstance instance, GraphNode state,
                            float deltaTimeSeconds, EngineServices services) {
        instance.setOutput(state.id(), BuiltinNodes.DELTA_TIME_PIN, deltaTimeSeconds);
        interpreter.resumeExec(instance, state, StateNodes.ON_UPDATE_PIN, services);
    }

    private boolean conditionSatisfied(GraphInstance instance, GraphNode transition,
                                       EngineServices services) {
        if (instance.asset().edgeInto(transition.id(), BuiltinNodes.CONDITION_PIN).isPresent()) {
            return GraphValues.asBoolean(interpreter.pullValue(instance, transition,
                    BuiltinNodes.CONDITION_PIN, PinType.BOOLEAN, services));
        }
        if (StateNodes.alwaysTaken(transition)) {
            return true;
        }
        return comparisonSatisfied(instance, transition);
    }

    private static boolean comparisonSatisfied(GraphInstance instance, GraphNode transition) {
        String variableName = GraphValues.asString(
                transition.values().getOrDefault(BuiltinNodes.VARIABLE_NAME_SETTING, ""));
        float value = numericValue(instance.variableValue(variableName));
        float threshold = GraphValues.asFloat(
                transition.values().getOrDefault(StateNodes.THRESHOLD_SETTING, 0.0f));
        String operator = GraphValues.asString(
                transition.values().getOrDefault(BuiltinNodes.OPERATOR_SETTING, StateNodes.DEFAULT_OPERATOR));
        return compare(value, operator, threshold);
    }

    private static boolean compare(float value, String operator, float threshold) {
        return switch (operator) {
            case "<" -> value < threshold;
            case ">=" -> value >= threshold;
            case "<=" -> value <= threshold;
            case "==" -> value == threshold;
            case "!=" -> value != threshold;
            default -> value > threshold;
        };
    }

    private static float numericValue(Object value) {
        if (value instanceof Boolean flag) {
            return flag ? TRUE_NUMERIC_VALUE : 0.0f;
        }
        return GraphValues.asFloat(value);
    }
}
