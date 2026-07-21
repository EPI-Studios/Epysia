package fr.epistudio.epysia.graph;

import fr.epistudio.epysia.EngineServices;
import fr.epistudio.epysia.reflection.ComponentRegistry;
import fr.epistudio.epysia.reflection.ComponentScanner;

import java.util.List;
import java.util.Map;
import java.util.Optional;

public final class GraphInterpreter {

    public static final int EXECUTION_BUDGET_PER_TICK = 20_000;
    public static final int WHILE_LOOP_ITERATION_LIMIT = 1_000;

    private final GraphNodeRegistry registry;
    private ComponentRegistry componentRegistry;

    public GraphInterpreter(GraphNodeRegistry registry) {
        this.registry = registry;
    }

    public GraphNodeRegistry registry() {
        return registry;
    }

    public void setComponentRegistry(ComponentRegistry componentRegistry) {
        this.componentRegistry = componentRegistry;
    }

    public ComponentRegistry componentRegistry() {
        if (componentRegistry == null) {
            componentRegistry = new ComponentRegistry();
            componentRegistry.populateFromScan(ComponentScanner.scan());
        }
        return componentRegistry;
    }

    public void fireEventNodes(GraphInstance instance, String typeKey,
                               Map<String, Object> payload, EngineServices services) {
        for (GraphNode node : instance.asset().nodesOfType(typeKey)) {
            fireEventNode(instance, node, payload, services);
        }
    }

    public void fireEventNode(GraphInstance instance, GraphNode node,
                              Map<String, Object> payload, EngineServices services) {
        instance.beginPass();
        for (Map.Entry<String, Object> entry : payload.entrySet()) {
            instance.setOutput(node.id(), entry.getKey(), entry.getValue());
        }
        try {
            executeNode(instance, node, services);
        } catch (RuntimeException error) {
            logFireError(instance, node, services, error);
        }
    }

    public void resumeExec(GraphInstance instance, GraphNode node, String pinName, EngineServices services) {
        instance.beginPass();
        try {
            triggerExec(instance, node, pinName, services);
        } catch (RuntimeException error) {
            logFireError(instance, node, services, error);
        }
    }

    private void logFireError(GraphInstance instance, GraphNode node,
                              EngineServices services, RuntimeException error) {
        services.logger().error("[Graph] Node " + node.typeKey() + " threw in "
                + instance.sourcePath(), error);
    }

    void triggerExec(GraphInstance instance, GraphNode node, String pinName, EngineServices services) {
        for (GraphEdge edge : instance.asset().edgesFrom(node.id(), pinName)) {
            instance.stampEdgeFire(edge);
            instance.asset().findNode(edge.toNode())
                    .ifPresent(target -> executeNode(instance, target, services));
        }
    }

    void scheduleExec(GraphInstance instance, GraphNode node, String pinName,
                      float seconds, EngineServices services) {
        services.scheduler().after(seconds, () -> resumeExec(instance, node, pinName, services));
    }

    private void executeNode(GraphInstance instance, GraphNode node, EngineServices services) {
        if (!instance.consumeBudget()) {
            warnBudgetExceeded(instance, services);
            return;
        }
        instance.stampNodeFire(node.id());
        Optional<NodeDefinition> definition = registry.find(node.typeKey());
        if (definition.isEmpty()) {
            warnUnknownType(instance, node, services);
            return;
        }
        definition.get().behavior().run(new NodeContext(this, instance, services, node));
    }

    private void warnBudgetExceeded(GraphInstance instance, EngineServices services) {
        if (instance.markBudgetWarningIssued()) {
            services.logger().warn("[Graph] Execution budget of " + EXECUTION_BUDGET_PER_TICK
                    + " nodes per tick exceeded in " + instance.sourcePath()
                    + "; remaining execution skipped this tick.");
        }
    }

    private void warnUnknownType(GraphInstance instance, GraphNode node, EngineServices services) {
        if (instance.warnOnceFor(node.typeKey())) {
            services.logger().warn("[Graph] Unknown node type " + node.typeKey()
                    + " in " + instance.sourcePath() + "; node skipped.");
        }
    }

    Object pullValue(GraphInstance instance, GraphNode node, String pinName,
                     PinType targetType, EngineServices services) {
        Optional<GraphEdge> edge = instance.asset().edgeInto(node.id(), pinName);
        if (edge.isEmpty()) {
            return literalValue(node, pinName, targetType);
        }
        instance.stampEdgeFire(edge.get());
        Optional<GraphNode> source = instance.asset().findNode(edge.get().fromNode());
        if (source.isEmpty()) {
            return GraphValues.defaultFor(targetType);
        }
        evaluateSourceIfNeeded(instance, source.get(), services);
        return GraphValues.coerce(instance.output(source.get().id(), edge.get().fromPin()), targetType);
    }

    private static Object literalValue(GraphNode node, String pinName, PinType targetType) {
        Object literal = node.values().get(pinName);
        if (literal == null) {
            return GraphValues.defaultFor(targetType);
        }
        return GraphValues.coerce(literal, targetType);
    }

    private void evaluateSourceIfNeeded(GraphInstance instance, GraphNode source, EngineServices services) {
        Optional<NodeDefinition> definition = registry.find(source.typeKey());
        if (definition.isEmpty() || definition.get().hasExecPins()) {
            return;
        }
        if (definition.get().memoized() && instance.isMemoized(source.id())) {
            return;
        }
        evaluateDataNode(instance, source, definition.get(), services);
    }

    private void evaluateDataNode(GraphInstance instance, GraphNode source,
                                  NodeDefinition definition, EngineServices services) {
        if (!instance.beginEvaluating(source.id())) {
            warnDataCycle(instance, source, services);
            return;
        }
        try {
            instance.stampNodeFire(source.id());
            definition.behavior().run(new NodeContext(this, instance, services, source));
            if (definition.memoized()) {
                instance.markMemoized(source.id());
            }
        } finally {
            instance.endEvaluating(source.id());
        }
    }

    private void warnDataCycle(GraphInstance instance, GraphNode source, EngineServices services) {
        if (instance.warnOnceFor("cycle:" + source.id())) {
            services.logger().warn("[Graph] Data dependency cycle at node " + source.typeKey()
                    + " in " + instance.sourcePath() + "; default value used.");
        }
    }
}
