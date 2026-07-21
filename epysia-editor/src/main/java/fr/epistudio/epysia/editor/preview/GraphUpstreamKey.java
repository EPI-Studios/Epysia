package fr.epistudio.epysia.editor.preview;

import fr.epistudio.epysia.graph.GraphAsset;
import fr.epistudio.epysia.graph.GraphEdge;
import fr.epistudio.epysia.graph.GraphNode;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;

public final class GraphUpstreamKey {

    private GraphUpstreamKey() {
    }

    public static String of(GraphAsset asset, int nodeId, String pinName) {
        StringBuilder builder = new StringBuilder(asset.kind().name()).append('#').append(pinName);
        appendNode(asset, nodeId, builder, new HashSet<>());
        return builder.toString();
    }

    private static void appendNode(GraphAsset asset, int nodeId, StringBuilder out, Set<Integer> visited) {
        if (!visited.add(nodeId)) {
            out.append("<seen ").append(nodeId).append('>');
            return;
        }
        Optional<GraphNode> found = asset.findNode(nodeId);
        if (found.isEmpty()) {
            out.append("<missing ").append(nodeId).append('>');
            return;
        }
        GraphNode node = found.get();
        out.append('(').append(node.typeKey());
        appendValues(node, out);
        appendInputs(asset, nodeId, out, visited);
        out.append(')');
    }

    private static void appendValues(GraphNode node, StringBuilder out) {
        for (var entry : new TreeMap<>(node.values()).entrySet()) {
            out.append('{').append(entry.getKey()).append('=').append(entry.getValue()).append('}');
        }
    }

    private static void appendInputs(GraphAsset asset, int nodeId, StringBuilder out, Set<Integer> visited) {
        for (GraphEdge edge : incomingSorted(asset, nodeId)) {
            out.append('[').append(edge.toPin()).append('<').append(edge.fromPin());
            appendNode(asset, edge.fromNode(), out, visited);
            out.append(']');
        }
    }

    private static List<GraphEdge> incomingSorted(GraphAsset asset, int nodeId) {
        List<GraphEdge> incoming = new ArrayList<>();
        for (GraphEdge edge : asset.edges()) {
            if (edge.toNode() == nodeId) {
                incoming.add(edge);
            }
        }
        incoming.sort(Comparator.comparing(GraphEdge::toPin).thenComparing(GraphEdge::fromPin));
        return incoming;
    }
}
