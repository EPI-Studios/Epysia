package fr.epistudio.epysia.graph;

import fr.epistudio.epysia.EngineServices;

import java.util.Map;

public record GraphRuntimeLink(GraphInterpreter interpreter, EngineServices services, GraphInstance instance) {

    public void fire(String typeKey, Map<String, Object> payload) {
        interpreter.fireEventNodes(instance, typeKey, payload, services);
    }
}
