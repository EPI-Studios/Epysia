package fr.epistudio.epysia.render.mesh;

import fr.epistudio.epysia.components.MeshRenderer;
import org.joml.Vector3f;

import java.util.List;

final class SceneRenderIndex {

    interface BoundsSource {
        boolean worldBounds(int slot, MeshRenderer renderer, Vector3f outMinimum, Vector3f outMaximum);
    }

    private static final int FLOATS_PER_ENTRY = 6;

    private final BoundsHierarchy hierarchy = new BoundsHierarchy();
    private final Vector3f scratchMinimum = new Vector3f();
    private final Vector3f scratchMaximum = new Vector3f();

    private float[] entryBounds = new float[0];
    private int[] entrySlots = new int[0];
    private int entryCount;
    private long builtSceneVersion = Long.MIN_VALUE;
    private int builtEntryCount = -1;
    private int candidateCount;

    void refresh(List<MeshRenderer> renderers, BoundsSource source, long sceneVersion) {
        ensureCapacity(renderers.size());
        entryCount = 0;
        for (int slot = 0; slot < renderers.size(); slot++) {
            if (!source.worldBounds(slot, renderers.get(slot), scratchMinimum, scratchMaximum)) {
                continue;
            }
            storeEntry(entryCount, slot);
            entryCount++;
        }
        rebuildOrRefit(sceneVersion);
    }

    private void storeEntry(int entry, int slot) {
        int base = entry * FLOATS_PER_ENTRY;
        entryBounds[base] = scratchMinimum.x;
        entryBounds[base + 1] = scratchMinimum.y;
        entryBounds[base + 2] = scratchMinimum.z;
        entryBounds[base + 3] = scratchMaximum.x;
        entryBounds[base + 4] = scratchMaximum.y;
        entryBounds[base + 5] = scratchMaximum.z;
        entrySlots[entry] = slot;
    }

    private void rebuildOrRefit(long sceneVersion) {
        if (sceneVersion != builtSceneVersion || entryCount != builtEntryCount) {
            hierarchy.build(entryBounds, entryCount);
            builtSceneVersion = sceneVersion;
            builtEntryCount = entryCount;
            return;
        }
        hierarchy.refit(entryBounds);
    }

    private void ensureCapacity(int renderers) {
        if (entrySlots.length < renderers) {
            entrySlots = new int[renderers];
            entryBounds = new float[renderers * FLOATS_PER_ENTRY];
        }
    }

    void query(BoundsHierarchy.BoxTest test, BoundsHierarchy.Visitor visitor) {
        candidateCount = 0;
        hierarchy.query(test, entry -> {
            candidateCount++;
            visitor.accept(entrySlots[entry]);
        });
    }

    int entryCount() {
        return entryCount;
    }

    int candidateCount() {
        return candidateCount;
    }
}
