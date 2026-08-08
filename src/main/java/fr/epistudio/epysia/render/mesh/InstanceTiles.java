package fr.epistudio.epysia.render.mesh;

import org.joml.Vector3f;
import java.util.Arrays;

final class InstanceTiles {

    private static final int FLOATS_PER_INSTANCE = 32;
    private static final int TRANSLATION_OFFSET = 12;
    private static final int TARGET_INSTANCES_PER_TILE = 400;
    private static final int MAXIMUM_ROWS = 16;

    private final Vector3f instanceMin = new Vector3f();
    private final Vector3f instanceMax = new Vector3f();

    private float[] payload = new float[0];
    private int[] tileStart = new int[0];
    private int[] tileLength = new int[0];
    private float[] tileBounds = new float[0];
    private int[] cellOfInstance = new int[0];
    private int[] cellCounts = new int[0];

    float[] payload() {
        return payload;
    }

    int[] tileStart() {
        return tileStart;
    }

    int[] tileLength() {
        return tileLength;
    }

    float[] tileBounds() {
        return tileBounds;
    }

    void build(float[] source, int count, Aabb localBounds, Vector3f outMin, Vector3f outMax) {
        int rows = rowCount(count);
        assignCells(source, count, rows);
        sortIntoTiles(source, count, rows * rows);
        computeTileBounds(localBounds, outMin, outMax);
    }

    private static int rowCount(int count) {
        int rows = (int) Math.round(Math.sqrt(count / (double) TARGET_INSTANCES_PER_TILE));
        return Math.max(1, Math.min(MAXIMUM_ROWS, rows));
    }

    private void assignCells(float[] source, int count, int rows) {
        if (cellOfInstance.length < count) {
            cellOfInstance = new int[count];
        }
        float minX = Float.POSITIVE_INFINITY;
        float minZ = Float.POSITIVE_INFINITY;
        float maxX = Float.NEGATIVE_INFINITY;
        float maxZ = Float.NEGATIVE_INFINITY;
        for (int instance = 0; instance < count; instance++) {
            int base = instance * FLOATS_PER_INSTANCE + TRANSLATION_OFFSET;
            minX = Math.min(minX, source[base]);
            maxX = Math.max(maxX, source[base]);
            minZ = Math.min(minZ, source[base + 2]);
            maxZ = Math.max(maxZ, source[base + 2]);
        }
        float spanX = Math.max(1.0e-4f, maxX - minX);
        float spanZ = Math.max(1.0e-4f, maxZ - minZ);
        for (int instance = 0; instance < count; instance++) {
            int base = instance * FLOATS_PER_INSTANCE + TRANSLATION_OFFSET;
            int column = cellIndex(source[base] - minX, spanX, rows);
            int row = cellIndex(source[base + 2] - minZ, spanZ, rows);
            cellOfInstance[instance] = row * rows + column;
        }
    }

    private static int cellIndex(float offset, float span, int rows) {
        return Math.max(0, Math.min(rows - 1, (int) (offset / span * rows)));
    }

    private void sortIntoTiles(float[] source, int count, int cells) {
        if (cellCounts.length < cells + 1) {
            cellCounts = new int[cells + 1];
        }
        Arrays.fill(cellCounts, 0, cells + 1, 0);
        for (int instance = 0; instance < count; instance++) {
            cellCounts[cellOfInstance[instance] + 1]++;
        }
        int used = countNonEmpty(cellCounts, cells);
        for (int cell = 0; cell < cells; cell++) {
            cellCounts[cell + 1] += cellCounts[cell];
        }
        allocateTiles(used, count);
        writeTiles(source, count, cells);
    }

    private static int countNonEmpty(int[] counts, int cells) {
        int used = 0;
        for (int cell = 0; cell < cells; cell++) {
            if (counts[cell + 1] > 0) {
                used++;
            }
        }
        return used;
    }

    private void allocateTiles(int used, int count) {
        if (tileStart.length != used) {
            tileStart = new int[used];
            tileLength = new int[used];
            tileBounds = new float[used * 6];
        }
        if (payload.length < count * FLOATS_PER_INSTANCE) {
            payload = new float[count * FLOATS_PER_INSTANCE];
        }
    }

    private void writeTiles(float[] source, int count, int cells) {
        int[] cursor = new int[cells];
        for (int cell = 0; cell < cells; cell++) {
            cursor[cell] = cellCounts[cell];
        }
        for (int instance = 0; instance < count; instance++) {
            int slot = cursor[cellOfInstance[instance]]++;
            System.arraycopy(source, instance * FLOATS_PER_INSTANCE,
                    payload, slot * FLOATS_PER_INSTANCE, FLOATS_PER_INSTANCE);
        }
        int tile = 0;
        for (int cell = 0; cell < cells; cell++) {
            int length = cellCounts[cell + 1] - cellCounts[cell];
            if (length > 0) {
                tileStart[tile] = cellCounts[cell];
                tileLength[tile] = length;
                tile++;
            }
        }
    }

    private void computeTileBounds(Aabb localBounds, Vector3f outMin, Vector3f outMax) {
        outMin.set(Float.POSITIVE_INFINITY);
        outMax.set(Float.NEGATIVE_INFINITY);
        for (int tile = 0; tile < tileStart.length; tile++) {
            resetTileBounds(tile);
            for (int slot = tileStart[tile]; slot < tileStart[tile] + tileLength[tile]; slot++) {
                transformedBounds(slot, localBounds);
                accumulate(tile, instanceMin, instanceMax);
            }
            outMin.set(Math.min(outMin.x, tileBounds[tile * 6]),
                    Math.min(outMin.y, tileBounds[tile * 6 + 1]),
                    Math.min(outMin.z, tileBounds[tile * 6 + 2]));
            outMax.set(Math.max(outMax.x, tileBounds[tile * 6 + 3]),
                    Math.max(outMax.y, tileBounds[tile * 6 + 4]),
                    Math.max(outMax.z, tileBounds[tile * 6 + 5]));
        }
    }

    private void transformedBounds(int slot, Aabb localBounds) {
        int base = slot * FLOATS_PER_INSTANCE;
        float centerX = (localBounds.minX() + localBounds.maxX()) * 0.5f;
        float centerY = (localBounds.minY() + localBounds.maxY()) * 0.5f;
        float centerZ = (localBounds.minZ() + localBounds.maxZ()) * 0.5f;
        float extentX = (localBounds.maxX() - localBounds.minX()) * 0.5f;
        float extentY = (localBounds.maxY() - localBounds.minY()) * 0.5f;
        float extentZ = (localBounds.maxZ() - localBounds.minZ()) * 0.5f;
        for (int axis = 0; axis < 3; axis++) {
            float row0 = payload[base + axis];
            float row1 = payload[base + 4 + axis];
            float row2 = payload[base + 8 + axis];
            float center = row0 * centerX + row1 * centerY + row2 * centerZ + payload[base + 12 + axis];
            float extent = Math.abs(row0) * extentX + Math.abs(row1) * extentY + Math.abs(row2) * extentZ;
            instanceMin.setComponent(axis, center - extent);
            instanceMax.setComponent(axis, center + extent);
        }
    }

    private void resetTileBounds(int tile) {
        int base = tile * 6;
        tileBounds[base] = Float.POSITIVE_INFINITY;
        tileBounds[base + 1] = Float.POSITIVE_INFINITY;
        tileBounds[base + 2] = Float.POSITIVE_INFINITY;
        tileBounds[base + 3] = Float.NEGATIVE_INFINITY;
        tileBounds[base + 4] = Float.NEGATIVE_INFINITY;
        tileBounds[base + 5] = Float.NEGATIVE_INFINITY;
    }

    private void accumulate(int tile, Vector3f worldMin, Vector3f worldMax) {
        int base = tile * 6;
        tileBounds[base] = Math.min(tileBounds[base], worldMin.x);
        tileBounds[base + 1] = Math.min(tileBounds[base + 1], worldMin.y);
        tileBounds[base + 2] = Math.min(tileBounds[base + 2], worldMin.z);
        tileBounds[base + 3] = Math.max(tileBounds[base + 3], worldMax.x);
        tileBounds[base + 4] = Math.max(tileBounds[base + 4], worldMax.y);
        tileBounds[base + 5] = Math.max(tileBounds[base + 5], worldMax.z);
    }
}
