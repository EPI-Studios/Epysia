package fr.epistudio.epysia.editor;

import fr.epistudio.epysia.gameobjects.GameObject;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

public final class EditorPrimitiveRegistry {

    private final List<Entry> entries = new ArrayList<>();

    public void register(String displayName, Supplier<GameObject> factory) {
        entries.add(new Entry(displayName, factory));
    }

    public List<Entry> entries() {
        return entries;
    }

    public record Entry(String displayName, Supplier<GameObject> factory) {
    }
}
