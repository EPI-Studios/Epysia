package fr.epistudio.epysia.prefab;

import fr.epistudio.epysia.gameobjects.GameObject;
import fr.epistudio.epysia.reflection.ComponentRegistry;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public final class PrefabApplier {

    private final PrefabWriter writer;

    public PrefabApplier(ComponentRegistry componentRegistry) {
        this.writer = new PrefabWriter(componentRegistry);
    }

    public void applyToPrefab(GameObject instance, Path prefabFile) throws IOException {
        if (!instance.isPrefabInstance()) {
            return;
        }
        Files.writeString(prefabFile, writer.serialize(instance));
        instance.clearOverrides();
    }

    public String serializeInstance(GameObject instance) {
        return writer.serialize(instance);
    }
}
