package fr.epistudio.epysia.editor.preview;

import fr.epistudio.epysia.graph.GraphAsset;
import fr.epistudio.epysia.graph.GraphEdge;
import fr.epistudio.epysia.graph.GraphNode;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.TreeMap;

public final class GraphMainKey {

    private GraphMainKey() {
    }

    public static String of(GraphAsset asset) {
        StringBuilder builder = new StringBuilder(asset.kind().name());
        for (GraphNode node : sortedNodes(asset)) {
            builder.append('(').append(node.id()).append(':').append(node.typeKey());
            for (var entry : new TreeMap<>(node.values()).entrySet()) {
                builder.append('{').append(entry.getKey()).append('=').append(entry.getValue()).append('}');
            }
            builder.append(')');
        }
        for (GraphEdge edge : sortedEdges(asset)) {
            builder.append('[').append(edge.fromNode()).append('.').append(edge.fromPin())
                    .append(">").append(edge.toNode()).append('.').append(edge.toPin()).append(']');
        }
        return builder.toString();
    }

    private static List<GraphNode> sortedNodes(GraphAsset asset) {
        List<GraphNode> nodes = new ArrayList<>(asset.nodes());
        nodes.sort(Comparator.comparingInt(GraphNode::id));
        return nodes;
    }

    private static List<GraphEdge> sortedEdges(GraphAsset asset) {
        List<GraphEdge> edges = new ArrayList<>(asset.edges());
        edges.sort(Comparator.comparingInt(GraphEdge::toNode)
                .thenComparing(GraphEdge::toPin)
                .thenComparingInt(GraphEdge::fromNode)
                .thenComparing(GraphEdge::fromPin));
        return edges;
    }
}
