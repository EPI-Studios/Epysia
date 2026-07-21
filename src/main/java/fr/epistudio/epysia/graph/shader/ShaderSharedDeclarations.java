package fr.epistudio.epysia.graph.shader;

import fr.epistudio.epysia.exceptions.EpysiaException;

import java.util.LinkedHashMap;
import java.util.Map;

public final class ShaderSharedDeclarations {

    public static final String NOISE_INCLUDE = "#include \"lib/graph_noise.glsl\"";

    private final Map<String, String> declarationsByName = new LinkedHashMap<>();
    private final Map<Integer, String> functionsByNodeId = new LinkedHashMap<>();
    private boolean noiseRequired;

    public void requireNoise() {
        noiseRequired = true;
    }

    public boolean noiseRequired() {
        return noiseRequired;
    }

    public void declare(String name, String line) {
        String existing = declarationsByName.get(name);
        if (existing == null) {
            declarationsByName.put(name, line);
            return;
        }
        if (!existing.equals(line)) {
            throw new EpysiaException("Conflicting shader parameter declarations for '" + name + "'");
        }
    }

    public void declareFunction(int nodeId, String functionSource) {
        functionsByNodeId.putIfAbsent(nodeId, functionSource);
    }

    public String block() {
        StringBuilder block = new StringBuilder();
        for (String line : declarationsByName.values()) {
            block.append(line).append('\n');
        }
        for (String functionSource : functionsByNodeId.values()) {
            block.append('\n').append(functionSource).append('\n');
        }
        return block.toString();
    }
}
