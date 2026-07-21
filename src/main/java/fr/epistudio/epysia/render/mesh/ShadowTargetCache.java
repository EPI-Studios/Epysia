package fr.epistudio.epysia.render.mesh;

import java.util.Arrays;

final class ShadowTargetCache {

    private final long[] signatures;
    private final boolean[] valid;
    private boolean enabled = true;

    ShadowTargetCache(int targetCount) {
        signatures = new long[targetCount];
        valid = new boolean[targetCount];
    }

    void setEnabled(boolean value) {
        enabled = value;
    }

    void invalidateAll() {
        Arrays.fill(valid, false);
    }

    boolean needsRender(int target, long signature, boolean alwaysDirty) {
        boolean reusable = enabled && !alwaysDirty && valid[target] && signatures[target] == signature;
        signatures[target] = signature;
        valid[target] = !alwaysDirty;
        return !reusable;
    }
}
