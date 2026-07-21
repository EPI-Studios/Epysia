package fr.epistudio.epysia.graph;

import java.util.LinkedHashMap;
import java.util.Map;

public final class GraphNode {

    private final int id;
    private final String typeKey;
    private float positionX;
    private float positionY;
    private final Map<String, Object> values = new LinkedHashMap<>();

    public GraphNode(int id, String typeKey) {
        this.id = id;
        this.typeKey = typeKey;
    }

    public int id() {
        return id;
    }

    public String typeKey() {
        return typeKey;
    }

    public float positionX() {
        return positionX;
    }

    public float positionY() {
        return positionY;
    }

    public void setPosition(float x, float y) {
        this.positionX = x;
        this.positionY = y;
    }

    public Map<String, Object> values() {
        return values;
    }
}
