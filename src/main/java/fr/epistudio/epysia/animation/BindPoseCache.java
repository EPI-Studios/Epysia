package fr.epistudio.epysia.animation;

import java.util.IdentityHashMap;
import java.util.Map;

public final class BindPoseCache {
    private final Map<Skeleton, BindPose> bindPoses = new IdentityHashMap<>();

    public BindPose of(Skeleton skeleton) {
        BindPose cached = bindPoses.get(skeleton);
        if (cached != null) {
            return cached;
        }
        BindPose created = new BindPose(skeleton);
        bindPoses.put(skeleton, created);
        return created;
    }

    public void clear() {
        bindPoses.clear();
    }
}
