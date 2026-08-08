package fr.epistudio.epysia.editor.gizmo;

import fr.epistudio.epysia.physics.api.ShapeDescriptor;

import java.util.HashMap;
import java.util.Map;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public final class ColliderEdgeCache {

    private final Map<ShapeDescriptor, float[]> edgesByShape = new HashMap<>();

    public float[] edgesOf(ShapeDescriptor shape) {
        float[] cached = edgesByShape.get(shape);
        if (cached != null) {
            return cached;
        }
        float[] built = ColliderShapeWriter.buildLocalEdges(shape);
        edgesByShape.put(shape, built);
        return built;
    }

    public void clear() {
        edgesByShape.clear();
    }

    public int size() {
        return edgesByShape.size();
    }
}
