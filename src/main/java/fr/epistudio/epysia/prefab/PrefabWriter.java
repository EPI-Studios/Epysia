package fr.epistudio.epysia.prefab;

import fr.epistudio.epysia.components.transforms.Transform3D;
import fr.epistudio.epysia.gameobjects.GameObject;
import fr.epistudio.epysia.reflection.ComponentRegistry;
import fr.epistudio.epysia.scene.serialization.GameObjectJsonCodec;
import fr.epistudio.epysia.scene.serialization.JsonWriter;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public final class PrefabWriter {

    private final GameObjectJsonCodec codec;

    public PrefabWriter(ComponentRegistry componentRegistry) {
        this.codec = new GameObjectJsonCodec(componentRegistry).omitPrefabLinks();
    }

    public void write(GameObject root, Path path) throws IOException {
        Files.writeString(path, serialize(root));
    }

    public String serialize(GameObject root) {
        List<GameObject> subtree = new ArrayList<>();
        collectSubtree(root, subtree);
        JsonWriter writer = new JsonWriter();
        writer.beginObject();
        writer.key("name").valueString(root.name());
        writer.key("gameObjects");
        codec.writeGameObjectArray(writer, subtree);
        writer.endObject();
        return writer.toString();
    }

    private static void collectSubtree(GameObject current, List<GameObject> out) {
        out.add(current);
        current.getComponent(Transform3D.class).ifPresent(transform -> {
            for (Transform3D child : transform.children()) {
                child.owner().ifPresent(childOwner -> collectSubtree(childOwner, out));
            }
        });
    }
}
