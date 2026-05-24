package fr.epistudio.epysia.render.mesh;

import fr.epistudio.epysia.exceptions.EpysiaException;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class ObjMesh {

    private ObjMesh() {
    }

    public static MeshData parseFromFile(Path objPath) {
        return parseDetailedFromFile(objPath).mesh();
    }

    public static MeshData parseFromSource(String objSource) {
        return parseDetailedFromSource(objSource).mesh();
    }

    public static ObjParseResult parseDetailedFromFile(Path objPath) {
        try {
            return parseDetailedFromSource(Files.readString(objPath));
        } catch (IOException exception) {
            throw new EpysiaException("Failed to read OBJ file " + objPath + ": " + exception.getMessage());
        }
    }

    public static ObjParseResult parseDetailedFromSource(String objSource) {
        Parser parser = new Parser();
        for (String line : objSource.split("\\r?\\n")) {
            parser.consumeLine(line);
        }
        return parser.build();
    }

    private static final class Parser {

        private final List<float[]> sourcePositions = new ArrayList<>();
        private final List<float[]> sourceNormals = new ArrayList<>();
        private final List<float[]> sourceUvs = new ArrayList<>();
        private final List<Float> outPositions = new ArrayList<>();
        private final List<Float> outNormals = new ArrayList<>();
        private final List<Float> outUvs = new ArrayList<>();
        private final List<Integer> outIndices = new ArrayList<>();
        private final Map<String, Integer> vertexIndexByKey = new HashMap<>();
        private final List<Submesh> submeshes = new ArrayList<>();
        private final Map<String, Integer> materialSlotByName = new HashMap<>();
        private final List<String> materialNamesBySlot = new ArrayList<>();
        private final List<String> mtllibPaths = new ArrayList<>();
        private int currentMaterialSlot;
        private int currentSubmeshStart;

        void consumeLine(String rawLine) {
            String line = rawLine.trim();
            if (line.isEmpty() || line.startsWith("#")) {
                return;
            }
            String[] tokens = line.split("\\s+");
            switch (tokens[0]) {
                case "v" -> sourcePositions.add(parseFloats(tokens, 3));
                case "vn" -> sourceNormals.add(parseFloats(tokens, 3));
                case "vt" -> sourceUvs.add(parseFloats(tokens, 2));
                case "f" -> consumeFace(tokens);
                case "usemtl" -> switchMaterial(tokens[1]);
                case "mtllib" -> appendMtllibs(tokens);
                default -> {
                }
            }
        }

        private static float[] parseFloats(String[] tokens, int count) {
            float[] values = new float[count];
            for (int i = 0; i < count; i++) {
                values[i] = Float.parseFloat(tokens[i + 1]);
            }
            return values;
        }

        private void appendMtllibs(String[] tokens) {
            for (int i = 1; i < tokens.length; i++) {
                mtllibPaths.add(tokens[i]);
            }
        }

        private void switchMaterial(String materialName) {
            Integer existing = materialSlotByName.get(materialName);
            int newSlot;
            if (existing != null) {
                newSlot = existing;
            } else {
                newSlot = materialNamesBySlot.size();
                materialSlotByName.put(materialName, newSlot);
                materialNamesBySlot.add(materialName);
            }
            if (newSlot == currentMaterialSlot && outIndices.isEmpty()) {
                return;
            }
            flushSubmesh();
            currentMaterialSlot = newSlot;
        }

        private void flushSubmesh() {
            int count = outIndices.size() - currentSubmeshStart;
            if (count > 0) {
                submeshes.add(new Submesh(currentSubmeshStart, count, currentMaterialSlot));
            }
            currentSubmeshStart = outIndices.size();
        }

        private void consumeFace(String[] tokens) {
            int faceVertexCount = tokens.length - 1;
            if (faceVertexCount < 3) {
                throw new EpysiaException("OBJ face has fewer than 3 vertices.");
            }
            int firstIndex = resolveVertex(tokens[1]);
            int previousIndex = resolveVertex(tokens[2]);
            for (int i = 3; i <= faceVertexCount; i++) {
                int currentIndex = resolveVertex(tokens[i]);
                outIndices.add(firstIndex);
                outIndices.add(previousIndex);
                outIndices.add(currentIndex);
                previousIndex = currentIndex;
            }
        }

        private int resolveVertex(String token) {
            Integer cached = vertexIndexByKey.get(token);
            if (cached != null) {
                return cached;
            }
            int newIndex = vertexIndexByKey.size();
            vertexIndexByKey.put(token, newIndex);
            appendVertex(token);
            return newIndex;
        }

        private void appendVertex(String token) {
            String[] parts = token.split("/", -1);
            int positionIndex = Integer.parseInt(parts[0]) - 1;
            int uvIndex = parts.length > 1 && !parts[1].isEmpty() ? Integer.parseInt(parts[1]) - 1 : -1;
            int normalIndex = parts.length > 2 && !parts[2].isEmpty() ? Integer.parseInt(parts[2]) - 1 : -1;
            appendFloats(outPositions, sourcePositions.get(positionIndex));
            appendFloats(outNormals, normalIndex >= 0 ? sourceNormals.get(normalIndex) : new float[]{0.0f, 1.0f, 0.0f});
            appendFloats(outUvs, uvIndex >= 0 ? sourceUvs.get(uvIndex) : new float[]{0.0f, 0.0f});
        }

        private static void appendFloats(List<Float> destination, float[] values) {
            for (float value : values) {
                destination.add(value);
            }
        }

        ObjParseResult build() {
            flushSubmesh();
            MeshData mesh = new MeshData(
                    toFloatArray(outPositions),
                    toFloatArray(outNormals),
                    toFloatArray(outUvs),
                    new float[0],
                    toIntArray(outIndices),
                    submeshes
            );
            return new ObjParseResult(mesh, materialNamesBySlot, mtllibPaths);
        }

        private static float[] toFloatArray(List<Float> source) {
            float[] result = new float[source.size()];
            for (int i = 0; i < source.size(); i++) {
                result[i] = source.get(i);
            }
            return result;
        }

        private static int[] toIntArray(List<Integer> source) {
            int[] result = new int[source.size()];
            for (int i = 0; i < source.size(); i++) {
                result[i] = source.get(i);
            }
            return result;
        }
    }
}
