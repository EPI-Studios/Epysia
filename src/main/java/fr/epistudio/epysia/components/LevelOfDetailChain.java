package fr.epistudio.epysia.components;

import fr.epistudio.epysia.assets.AssetRef;
import fr.epistudio.epysia.assets.AssetRegistry;
import fr.epistudio.epysia.render.mesh.UploadedMesh;

import java.util.ArrayList;
import java.util.List;

final class LevelOfDetailChain {

    private static final float HYSTERESIS = 0.92f;

    private record Level(AssetRef<UploadedMesh> mesh, float switchDistance) {
    }

    private final List<Level> levels = new ArrayList<>();
    private int activeLevel;

    void addDirect(UploadedMesh levelMesh, float switchDistance) {
        AssetRef<UploadedMesh> reference = new AssetRef<>(UploadedMesh.class);
        reference.setDirect(levelMesh);
        levels.add(new Level(reference, switchDistance));
    }

    void addPath(String path, float switchDistance) {
        AssetRef<UploadedMesh> reference = new AssetRef<>(UploadedMesh.class);
        reference.setPath(path);
        levels.add(new Level(reference, switchDistance));
    }

    int count() {
        return levels.size();
    }

    int activeLevel() {
        return activeLevel;
    }

    int selectLevel(float distance) {
        int level = Math.min(activeLevel, levels.size());
        while (level < levels.size() && distance >= levels.get(level).switchDistance()) {
            level++;
        }
        while (level > 0 && distance < levels.get(level - 1).switchDistance() * HYSTERESIS) {
            level--;
        }
        activeLevel = level;
        return level;
    }

    UploadedMesh meshForDistance(float distance, UploadedMesh base) {
        if (levels.isEmpty()) {
            return base;
        }
        if (selectLevel(distance) == 0) {
            return base;
        }
        UploadedMesh selected = levels.get(activeLevel - 1).mesh().directOrNull();
        return selected == null ? base : selected;
    }

    void resolve(AssetRegistry registry) {
        for (Level level : levels) {
            if (level.mesh().direct().isEmpty() && !level.mesh().isEmpty()) {
                level.mesh().resolve(registry);
            }
        }
    }
}
