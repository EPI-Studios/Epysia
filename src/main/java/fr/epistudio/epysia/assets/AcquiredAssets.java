package fr.epistudio.epysia.assets;

import java.util.ArrayList;
import java.util.List;

public final class AcquiredAssets {

    private record Owned(Class<?> type, AssetUri uri, AssetVariant variant) {
    }

    private final List<Owned> owned = new ArrayList<>();

    public void record(Class<?> type, AssetUri uri, AssetVariant variant) {
        owned.add(new Owned(type, uri, variant));
    }

    public boolean isEmpty() {
        return owned.isEmpty();
    }

    public int size() {
        return owned.size();
    }

    public void releaseAll(AssetRegistry registry) {
        for (Owned entry : owned) {
            registry.release(entry.type(), entry.uri(), entry.variant());
        }
        owned.clear();
    }
}
