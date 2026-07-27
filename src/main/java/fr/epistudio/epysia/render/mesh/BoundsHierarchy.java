package fr.epistudio.epysia.render.mesh;

import java.util.Arrays;

public final class BoundsHierarchy {

    public interface Visitor {
        void accept(int entryIndex);
    }

    public interface BoxTest {
        boolean overlaps(float minX, float minY, float minZ, float maxX, float maxY, float maxZ);
    }

    private static final int LEAF_CAPACITY = 4;
    private static final int NO_CHILD = -1;

    private float[] bounds = new float[0];
    private int[] leftChild = new int[0];
    private int[] rightChild = new int[0];
    private int[] firstEntry = new int[0];
    private int[] entryCount = new int[0];
    private int[] order = new int[0];
    private int nodeCount;
    private int entryTotal;

    public void build(float[] entryBounds, int entries) {
        entryTotal = entries;
        if (entries == 0) {
            nodeCount = 0;
            return;
        }
        ensureCapacity(entries);
        for (int index = 0; index < entries; index++) {
            order[index] = index;
        }
        nodeCount = 0;
        createNode(entryBounds, 0, entries);
    }

    private void ensureCapacity(int entries) {
        int maximumNodes = Math.max(1, entries * 2);
        if (order.length < entries) {
            order = new int[entries];
        }
        if (leftChild.length < maximumNodes) {
            leftChild = new int[maximumNodes];
            rightChild = new int[maximumNodes];
            firstEntry = new int[maximumNodes];
            entryCount = new int[maximumNodes];
            bounds = new float[maximumNodes * 6];
        }
    }

    private int createNode(float[] entryBounds, int from, int count) {
        int node = nodeCount++;
        computeBounds(entryBounds, from, count, node);
        if (count <= LEAF_CAPACITY) {
            leftChild[node] = NO_CHILD;
            rightChild[node] = NO_CHILD;
            firstEntry[node] = from;
            entryCount[node] = count;
            return node;
        }
        int axis = widestAxis(node);
        int middle = from + count / 2;
        partition(entryBounds, from, count, axis, middle);
        entryCount[node] = 0;
        leftChild[node] = createNode(entryBounds, from, middle - from);
        rightChild[node] = createNode(entryBounds, middle, from + count - middle);
        return node;
    }

    private void computeBounds(float[] entryBounds, int from, int count, int node) {
        float minX = Float.MAX_VALUE;
        float minY = Float.MAX_VALUE;
        float minZ = Float.MAX_VALUE;
        float maxX = -Float.MAX_VALUE;
        float maxY = -Float.MAX_VALUE;
        float maxZ = -Float.MAX_VALUE;
        for (int slot = from; slot < from + count; slot++) {
            int base = order[slot] * 6;
            minX = Math.min(minX, entryBounds[base]);
            minY = Math.min(minY, entryBounds[base + 1]);
            minZ = Math.min(minZ, entryBounds[base + 2]);
            maxX = Math.max(maxX, entryBounds[base + 3]);
            maxY = Math.max(maxY, entryBounds[base + 4]);
            maxZ = Math.max(maxZ, entryBounds[base + 5]);
        }
        int target = node * 6;
        bounds[target] = minX;
        bounds[target + 1] = minY;
        bounds[target + 2] = minZ;
        bounds[target + 3] = maxX;
        bounds[target + 4] = maxY;
        bounds[target + 5] = maxZ;
    }

    private int widestAxis(int node) {
        int base = node * 6;
        float spanX = bounds[base + 3] - bounds[base];
        float spanY = bounds[base + 4] - bounds[base + 1];
        float spanZ = bounds[base + 5] - bounds[base + 2];
        if (spanX >= spanY && spanX >= spanZ) {
            return 0;
        }
        return spanY >= spanZ ? 1 : 2;
    }

    private void partition(float[] entryBounds, int from, int count, int axis, int middle) {
        Integer[] slice = new Integer[count];
        for (int index = 0; index < count; index++) {
            slice[index] = order[from + index];
        }
        Arrays.sort(slice, (first, second) ->
                Float.compare(centre(entryBounds, first, axis), centre(entryBounds, second, axis)));
        for (int index = 0; index < count; index++) {
            order[from + index] = slice[index];
        }
    }

    private static float centre(float[] entryBounds, int entry, int axis) {
        int base = entry * 6;
        return (entryBounds[base + axis] + entryBounds[base + 3 + axis]) * 0.5f;
    }

    public void refit(float[] entryBounds) {
        for (int node = nodeCount - 1; node >= 0; node--) {
            if (entryCount[node] > 0) {
                computeBounds(entryBounds, firstEntry[node], entryCount[node], node);
            } else if (leftChild[node] != NO_CHILD) {
                mergeChildren(node);
            }
        }
    }

    private void mergeChildren(int node) {
        int left = leftChild[node] * 6;
        int right = rightChild[node] * 6;
        int target = node * 6;
        for (int axis = 0; axis < 3; axis++) {
            bounds[target + axis] = Math.min(bounds[left + axis], bounds[right + axis]);
            bounds[target + 3 + axis] = Math.max(bounds[left + 3 + axis], bounds[right + 3 + axis]);
        }
    }

    public void query(BoxTest test, Visitor visitor) {
        if (nodeCount == 0 || entryTotal == 0) {
            return;
        }
        visit(0, test, visitor);
    }

    private void visit(int node, BoxTest test, Visitor visitor) {
        int base = node * 6;
        if (!test.overlaps(bounds[base], bounds[base + 1], bounds[base + 2],
                bounds[base + 3], bounds[base + 4], bounds[base + 5])) {
            return;
        }
        if (entryCount[node] > 0) {
            for (int slot = firstEntry[node]; slot < firstEntry[node] + entryCount[node]; slot++) {
                visitor.accept(order[slot]);
            }
            return;
        }
        if (leftChild[node] == NO_CHILD) {
            return;
        }
        visit(leftChild[node], test, visitor);
        visit(rightChild[node], test, visitor);
    }

    public int nodeCount() {
        return nodeCount;
    }
}
