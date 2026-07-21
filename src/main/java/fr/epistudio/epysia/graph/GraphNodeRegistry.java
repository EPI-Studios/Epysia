package fr.epistudio.epysia.graph;

import fr.epistudio.epysia.graph.shader.ShaderNodes;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public final class GraphNodeRegistry {

    @FunctionalInterface
    public interface ClassResolver {
        Optional<Class<?>> resolve(String className);
    }

    private final Map<String, NodeDefinition> definitions = new LinkedHashMap<>();
    private ClassResolver classResolver = GraphNodeRegistry::resolveWithDefaultLoader;

    public static GraphNodeRegistry withBuiltins() {
        GraphNodeRegistry registry = new GraphNodeRegistry();
        BuiltinNodes.registerInto(registry);
        StateNodes.registerInto(registry);
        ShaderNodes.registerInto(registry);
        return registry;
    }

    private static Optional<Class<?>> resolveWithDefaultLoader(String className) {
        try {
            return Optional.of(Class.forName(className, false, GraphNodeRegistry.class.getClassLoader()));
        } catch (ClassNotFoundException | LinkageError missing) {
            return Optional.empty();
        }
    }

    public void setClassResolver(ClassResolver classResolver) {
        this.classResolver = classResolver;
    }

    public ClassResolver classResolver() {
        return classResolver;
    }

    public void register(NodeDefinition definition) {
        definitions.put(definition.typeKey(), definition);
    }

    public Optional<NodeDefinition> find(String typeKey) {
        NodeDefinition known = definitions.get(typeKey);
        if (known != null) {
            return Optional.of(known);
        }
        if (!typeKey.startsWith(ReflectionNodes.TYPE_KEY_PREFIX)) {
            return Optional.empty();
        }
        Optional<NodeDefinition> resolved = ReflectionNodes.definitionFor(typeKey, classResolver);
        resolved.ifPresent(this::register);
        return resolved;
    }

    public List<NodeDefinition> all() {
        return List.copyOf(definitions.values());
    }

    public List<PinDefinition> effectiveInputPins(GraphAsset asset, GraphNode node) {
        Optional<NodeDefinition> definition = find(node.typeKey());
        if (definition.isEmpty()) {
            return List.of();
        }
        if (node.typeKey().equals(BuiltinNodes.VARIABLE_SET)) {
            return variableSetInputs(asset, node);
        }
        if (node.typeKey().equals(ShaderNodes.CUSTOM_CODE)) {
            return ShaderNodes.customCodeInputs(node);
        }
        return definition.get().inputPins();
    }

    public List<PinDefinition> effectiveOutputPins(GraphAsset asset, GraphNode node) {
        Optional<NodeDefinition> definition = find(node.typeKey());
        if (definition.isEmpty()) {
            return List.of();
        }
        if (node.typeKey().equals(BuiltinNodes.VARIABLE_GET)) {
            return List.of(new PinDefinition(BuiltinNodes.VALUE_PIN, variableTypeOf(asset, node)));
        }
        if (node.typeKey().equals(BuiltinNodes.FLOW_SEQUENCE)) {
            return sequenceOutputs(node);
        }
        if (node.typeKey().equals(ShaderNodes.CUSTOM_CODE)) {
            return ShaderNodes.customCodeOutputs(node);
        }
        return definition.get().outputPins();
    }

    private static List<PinDefinition> variableSetInputs(GraphAsset asset, GraphNode node) {
        return List.of(
                PinDefinition.exec(BuiltinNodes.IN_PIN),
                new PinDefinition(BuiltinNodes.VALUE_PIN, variableTypeOf(asset, node)));
    }

    private static PinType variableTypeOf(GraphAsset asset, GraphNode node) {
        String name = GraphValues.asString(node.values().get(BuiltinNodes.VARIABLE_NAME_SETTING));
        return asset.findVariable(name).map(GraphVariable::type).orElse(PinType.OBJECT);
    }

    private static List<PinDefinition> sequenceOutputs(GraphNode node) {
        int count = Math.max(1, GraphValues.asInt(
                node.values().getOrDefault(BuiltinNodes.OUTPUT_COUNT_SETTING, 2)));
        List<PinDefinition> outputs = new ArrayList<>();
        for (int index = 1; index <= count; index++) {
            outputs.add(PinDefinition.exec(BuiltinNodes.sequencePinName(index)));
        }
        return outputs;
    }
}
