package fr.epistudio.epysia.graph;

import fr.epistudio.epysia.scene.serialization.JsonReader;
import fr.epistudio.epysia.scene.serialization.JsonWriter;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

public final class GraphJsonCodec {

    public static final String FILE_EXTENSION = ".epygraph";
    private static final int FORMAT_VERSION = 1;

    public String write(GraphAsset asset) {
        JsonWriter writer = new JsonWriter();
        writer.beginObject();
        writer.key("formatVersion").valueNumber(FORMAT_VERSION);
        writer.key("kind").valueString(asset.kind().name());
        writeNodes(writer, asset);
        writeEdges(writer, asset);
        writeVariables(writer, asset);
        writer.endObject();
        return writer.toString();
    }

    public void writeToFile(GraphAsset asset, Path path) throws IOException {
        Files.writeString(path, write(asset));
    }

    private void writeNodes(JsonWriter writer, GraphAsset asset) {
        writer.key("nodes").beginArray();
        for (GraphNode node : asset.nodes()) {
            writeNode(writer, node);
        }
        writer.endArray();
    }

    private void writeNode(JsonWriter writer, GraphNode node) {
        writer.beginObject();
        writer.key("id").valueNumber(node.id());
        writer.key("type").valueString(node.typeKey());
        writer.key("x").valueNumber(node.positionX());
        writer.key("y").valueNumber(node.positionY());
        writer.key("values").beginObject();
        for (Map.Entry<String, Object> entry : node.values().entrySet()) {
            writer.key(entry.getKey());
            GraphValueJson.write(writer, entry.getValue());
        }
        writer.endObject();
        writer.endObject();
    }

    private void writeEdges(JsonWriter writer, GraphAsset asset) {
        writer.key("edges").beginArray();
        for (GraphEdge edge : asset.edges()) {
            writer.beginObject();
            writer.key("fromNode").valueNumber(edge.fromNode());
            writer.key("fromPin").valueString(edge.fromPin());
            writer.key("toNode").valueNumber(edge.toNode());
            writer.key("toPin").valueString(edge.toPin());
            writer.endObject();
        }
        writer.endArray();
    }

    private void writeVariables(JsonWriter writer, GraphAsset asset) {
        writer.key("variables").beginArray();
        for (GraphVariable variable : asset.variables()) {
            writer.beginObject();
            writer.key("name").valueString(variable.name());
            writer.key("type").valueString(variable.type().name());
            writer.key("defaultValue");
            GraphValueJson.write(writer, variable.defaultValue());
            writer.key("exposed").valueBoolean(variable.exposed());
            writer.endObject();
        }
        writer.endArray();
    }

    public GraphAsset read(String text) {
        Map<String, Object> root = new JsonReader(text).readRootObject();
        GraphAsset asset = new GraphAsset();
        asset.setKind(GraphKind.parse(GraphValues.asString(root.get("kind"))));
        readNodes(asset, listOf(root, "nodes"));
        readEdges(asset, listOf(root, "edges"));
        readVariables(asset, listOf(root, "variables"));
        return asset;
    }

    public GraphAsset readFromFile(Path path) throws IOException {
        return read(Files.readString(path));
    }

    @SuppressWarnings("unchecked")
    private static List<Object> listOf(Map<String, Object> root, String key) {
        return root.get(key) instanceof List<?> list ? (List<Object>) list : List.of();
    }

    @SuppressWarnings("unchecked")
    private void readNodes(GraphAsset asset, List<Object> nodesJson) {
        for (Object element : nodesJson) {
            if (element instanceof Map<?, ?> nodeJson) {
                readNode(asset, (Map<String, Object>) nodeJson);
            }
        }
    }

    @SuppressWarnings("unchecked")
    private void readNode(GraphAsset asset, Map<String, Object> nodeJson) {
        int id = GraphValues.asInt(nodeJson.get("id"));
        String typeKey = GraphValues.asString(nodeJson.get("type"));
        GraphNode node = new GraphNode(id, typeKey);
        node.setPosition(GraphValues.asFloat(nodeJson.get("x")), GraphValues.asFloat(nodeJson.get("y")));
        if (nodeJson.get("values") instanceof Map<?, ?> valuesJson) {
            for (Map.Entry<String, Object> entry : ((Map<String, Object>) valuesJson).entrySet()) {
                node.values().put(entry.getKey(), GraphValueJson.normalize(entry.getValue()));
            }
        }
        asset.nodes().add(node);
    }

    private void readEdges(GraphAsset asset, List<Object> edgesJson) {
        for (Object element : edgesJson) {
            if (element instanceof Map<?, ?> edgeJson) {
                asset.edges().add(new GraphEdge(
                        GraphValues.asInt(edgeJson.get("fromNode")),
                        GraphValues.asString(edgeJson.get("fromPin")),
                        GraphValues.asInt(edgeJson.get("toNode")),
                        GraphValues.asString(edgeJson.get("toPin"))));
            }
        }
    }

    private void readVariables(GraphAsset asset, List<Object> variablesJson) {
        for (Object element : variablesJson) {
            if (element instanceof Map<?, ?> variableJson) {
                readVariable(asset, variableJson);
            }
        }
    }

    private void readVariable(GraphAsset asset, Map<?, ?> variableJson) {
        String typeName = GraphValues.asString(variableJson.get("type"));
        PinType type = parseType(typeName);
        asset.variables().add(new GraphVariable(
                GraphValues.asString(variableJson.get("name")),
                type,
                GraphValues.coerce(GraphValueJson.normalize(variableJson.get("defaultValue")), type),
                GraphValues.asBoolean(variableJson.get("exposed"))));
    }

    private static PinType parseType(String typeName) {
        try {
            PinType parsed = PinType.valueOf(typeName);
            return parsed.isData() ? parsed : PinType.FLOAT;
        } catch (IllegalArgumentException unknown) {
            return PinType.FLOAT;
        }
    }
}
