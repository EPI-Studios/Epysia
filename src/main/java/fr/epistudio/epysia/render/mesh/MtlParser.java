package fr.epistudio.epysia.render.mesh;

import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public final class MtlParser {

    private MtlParser() {
    }

    public static List<MtlDefinition> parseFromSource(String mtlSource) {
        List<MtlDefinition> definitions = new ArrayList<>();
        State state = new State();
        for (String rawLine : mtlSource.split("\\r?\\n")) {
            consumeLine(rawLine, state, definitions);
        }
        state.flushTo(definitions);
        return definitions;
    }

    private static void consumeLine(String rawLine, State state, List<MtlDefinition> definitions) {
        String line = rawLine.trim();
        if (line.isEmpty() || line.startsWith("#")) {
            return;
        }
        String[] tokens = line.split("\\s+");
        switch (tokens[0]) {
            case "newmtl" -> {
                state.flushTo(definitions);
                state.startMaterial(tokens[1]);
            }
            case "Kd" -> state.setDiffuse(parseFloat(tokens, 1), parseFloat(tokens, 2), parseFloat(tokens, 3));
            case "map_Kd" -> state.setDiffuseTexture(extractTexturePath(tokens));
            case "bump", "map_Bump", "norm", "map_bump" -> state.setNormalTexture(extractTexturePath(tokens));
            default -> {
            }
        }
    }

    private static float parseFloat(String[] tokens, int index) {
        return Float.parseFloat(tokens[index]);
    }

    private static String extractTexturePath(String[] tokens) {
        return tokens[tokens.length - 1];
    }

    private static final class State {
        private String currentName;
        private final Vector3f diffuseColor = new Vector3f(1.0f, 1.0f, 1.0f);
        private String diffuseTexturePath;
        private String normalTexturePath;

        void startMaterial(String name) {
            currentName = name;
            diffuseColor.set(1.0f, 1.0f, 1.0f);
            diffuseTexturePath = null;
            normalTexturePath = null;
        }

        void setDiffuse(float red, float green, float blue) {
            diffuseColor.set(red, green, blue);
        }

        void setDiffuseTexture(String relativePath) {
            this.diffuseTexturePath = relativePath;
        }

        void setNormalTexture(String relativePath) {
            this.normalTexturePath = relativePath;
        }

        void flushTo(List<MtlDefinition> definitions) {
            if (currentName == null) {
                return;
            }
            definitions.add(new MtlDefinition(
                    currentName,
                    new Vector3f(diffuseColor),
                    Optional.ofNullable(diffuseTexturePath),
                    Optional.ofNullable(normalTexturePath)
            ));
            currentName = null;
            diffuseTexturePath = null;
            normalTexturePath = null;
        }
    }
}
