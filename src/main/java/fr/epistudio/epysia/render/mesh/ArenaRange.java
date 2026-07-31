package fr.epistudio.epysia.render.mesh;

import java.util.ArrayList;
import java.util.List;

final class ArenaRange {

    private final int capacity;
    private final List<int[]> freeBlocks = new ArrayList<>();

    ArenaRange(int capacity) {
        this.capacity = capacity;
        freeBlocks.add(new int[]{0, capacity});
    }

    int capacity() {
        return capacity;
    }

    int allocate(int count) {
        for (int index = 0; index < freeBlocks.size(); index++) {
            int[] block = freeBlocks.get(index);
            if (block[1] < count) {
                continue;
            }
            int offset = block[0];
            if (block[1] == count) {
                freeBlocks.remove(index);
            } else {
                block[0] += count;
                block[1] -= count;
            }
            return offset;
        }
        return -1;
    }

    void release(int offset, int count) {
        if (count <= 0) {
            return;
        }
        int insertion = 0;
        while (insertion < freeBlocks.size() && freeBlocks.get(insertion)[0] < offset) {
            insertion++;
        }
        freeBlocks.add(insertion, new int[]{offset, count});
        coalesceAround(insertion);
    }

    private void coalesceAround(int insertion) {
        mergeWithNext(insertion);
        if (insertion > 0) {
            mergeWithNext(insertion - 1);
        }
    }

    private void mergeWithNext(int index) {
        if (index + 1 >= freeBlocks.size()) {
            return;
        }
        int[] current = freeBlocks.get(index);
        int[] next = freeBlocks.get(index + 1);
        if (current[0] + current[1] != next[0]) {
            return;
        }
        current[1] += next[1];
        freeBlocks.remove(index + 1);
    }

    int largestFreeBlock() {
        int largest = 0;
        for (int[] block : freeBlocks) {
            largest = Math.max(largest, block[1]);
        }
        return largest;
    }

    int freeCount() {
        int free = 0;
        for (int[] block : freeBlocks) {
            free += block[1];
        }
        return free;
    }
}
