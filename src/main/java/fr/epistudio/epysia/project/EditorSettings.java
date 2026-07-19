package fr.epistudio.epysia.project;

import java.util.ArrayList;
import java.util.List;

public record EditorSettings(List<String> layerNames, int[] collisionMatrix) {

    public static final int LAYER_COUNT = 16;

    public EditorSettings {
        layerNames = List.copyOf(layerNames);
        collisionMatrix = collisionMatrix.clone();
    }

    public static EditorSettings defaults() {
        List<String> names = new ArrayList<>(LAYER_COUNT);
        for (int i = 0; i < LAYER_COUNT; i++) {
            names.add("Layer " + i);
        }
        int[] matrix = new int[LAYER_COUNT];
        int allLayers = (1 << LAYER_COUNT) - 1;
        for (int i = 0; i < LAYER_COUNT; i++) {
            matrix[i] = allLayers;
        }
        return new EditorSettings(names, matrix);
    }

    public boolean collides(int layerA, int layerB) {
        return (collisionMatrix[layerA] & (1 << layerB)) != 0;
    }

    public EditorSettings withCollision(int layerA, int layerB, boolean enabled) {
        int[] matrix = collisionMatrix.clone();
        if (enabled) {
            matrix[layerA] |= (1 << layerB);
            matrix[layerB] |= (1 << layerA);
        } else {
            matrix[layerA] &= ~(1 << layerB);
            matrix[layerB] &= ~(1 << layerA);
        }
        return new EditorSettings(layerNames, matrix);
    }

    public EditorSettings withLayerName(int layer, String name) {
        List<String> names = new ArrayList<>(layerNames);
        names.set(layer, name);
        return new EditorSettings(names, collisionMatrix);
    }

    public int[] collisionMatrix() {
        return collisionMatrix.clone();
    }
}
