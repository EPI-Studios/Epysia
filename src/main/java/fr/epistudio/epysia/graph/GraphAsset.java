package fr.epistudio.epysia.graph;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public final class GraphAsset {

    private final List<GraphNode> nodes = new ArrayList<>();
    private final List<GraphEdge> edges = new ArrayList<>();
    private final List<GraphVariable> variables = new ArrayList<>();
    private GraphKind kind = GraphKind.LOGIC;

    public GraphKind kind() {
        return kind;
    }

    public void setKind(GraphKind kind) {
        this.kind = kind;
    }

    public List<GraphNode> nodes() {
        return nodes;
    }

    public List<GraphEdge> edges() {
        return edges;
    }

    public List<GraphVariable> variables() {
        return variables;
    }

    public GraphNode addNode(String typeKey, float x, float y) {
        GraphNode node = new GraphNode(nextNodeId(), typeKey);
        node.setPosition(x, y);
        nodes.add(node);
        return node;
    }

    public int nextNodeId() {
        int highest = 0;
        for (GraphNode node : nodes) {
            highest = Math.max(highest, node.id());
        }
        return highest + 1;
    }

    public void removeNode(int nodeId) {
        nodes.removeIf(node -> node.id() == nodeId);
        edges.removeIf(edge -> edge.fromNode() == nodeId || edge.toNode() == nodeId);
    }

    public Optional<GraphNode> findNode(int nodeId) {
        for (GraphNode node : nodes) {
            if (node.id() == nodeId) {
                return Optional.of(node);
            }
        }
        return Optional.empty();
    }

    public List<GraphNode> nodesOfType(String typeKey) {
        List<GraphNode> matching = new ArrayList<>();
        for (GraphNode node : nodes) {
            if (node.typeKey().equals(typeKey)) {
                matching.add(node);
            }
        }
        return matching;
    }

    public Optional<GraphEdge> edgeInto(int nodeId, String pinName) {
        for (GraphEdge edge : edges) {
            if (edge.toNode() == nodeId && edge.toPin().equals(pinName)) {
                return Optional.of(edge);
            }
        }
        return Optional.empty();
    }

    public List<GraphEdge> edgesFrom(int nodeId, String pinName) {
        List<GraphEdge> matching = new ArrayList<>();
        for (GraphEdge edge : edges) {
            if (edge.fromNode() == nodeId && edge.fromPin().equals(pinName)) {
                matching.add(edge);
            }
        }
        return matching;
    }

    public Optional<GraphVariable> findVariable(String name) {
        for (GraphVariable variable : variables) {
            if (variable.name().equals(name)) {
                return Optional.of(variable);
            }
        }
        return Optional.empty();
    }
}
