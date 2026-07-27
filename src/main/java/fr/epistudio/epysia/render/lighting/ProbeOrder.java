package fr.epistudio.epysia.render.lighting;

import fr.epistudio.epysia.assets.epyprobes.BakedProbes;
import org.joml.Vector3f;

import java.util.Arrays;
import java.util.Comparator;

final class ProbeOrder {

    private static final float REBUILD_DISTANCE = 2.0f;

    private final Vector3f lastCameraPosition = new Vector3f(Float.NaN, Float.NaN, Float.NaN);
    private int[] order = new int[0];

    void rebuildIfNeeded(BakedProbes probes, Vector3f cameraPosition) {
        if (order.length == probes.probeCount() && !cameraMovedFar(cameraPosition)) {
            return;
        }
        lastCameraPosition.set(cameraPosition);
        order = sortedByDistance(probes, cameraPosition);
    }

    private boolean cameraMovedFar(Vector3f cameraPosition) {
        if (Float.isNaN(lastCameraPosition.x)) {
            return true;
        }
        return lastCameraPosition.distanceSquared(cameraPosition) > REBUILD_DISTANCE * REBUILD_DISTANCE;
    }

    private static int[] sortedByDistance(BakedProbes probes, Vector3f cameraPosition) {
        float[] positions = probes.positions();
        Integer[] indices = new Integer[probes.probeCount()];
        Arrays.setAll(indices, index -> index);
        Arrays.sort(indices, Comparator.comparingDouble(
                index -> squaredDistance(positions, index, cameraPosition)));
        int[] result = new int[indices.length];
        for (int slot = 0; slot < indices.length; slot++) {
            result[slot] = indices[slot];
        }
        return result;
    }

    private static double squaredDistance(float[] positions, int probeIndex, Vector3f cameraPosition) {
        double deltaX = positions[probeIndex * 3] - cameraPosition.x;
        double deltaY = positions[probeIndex * 3 + 1] - cameraPosition.y;
        double deltaZ = positions[probeIndex * 3 + 2] - cameraPosition.z;
        return deltaX * deltaX + deltaY * deltaY + deltaZ * deltaZ;
    }

    int probeAt(int cursor, int probeCount) {
        if (order.length == 0 || probeCount <= 0) {
            return 0;
        }
        return order[Math.floorMod(cursor, order.length)];
    }
}
