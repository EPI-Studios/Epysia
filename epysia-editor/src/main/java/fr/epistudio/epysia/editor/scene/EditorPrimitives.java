package fr.epistudio.epysia.editor.scene;

import fr.epistudio.epysia.EditorContext;
import fr.epistudio.epysia.EngineModule;
import fr.epistudio.epysia.gameobjects.GameObject;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.ServiceLoader;
import java.util.function.Supplier;

public final class EditorPrimitives implements EditorContext {

    public record Entry(String displayName, Supplier<GameObject> factory) {
    }

    private final List<Entry> entries = new ArrayList<>();

    @Override
    public void registerPrimitive(String displayName, Supplier<GameObject> factory) {
        entries.add(new Entry(displayName, factory));
    }

    public List<Entry> entries() {
        return List.copyOf(entries);
    }

    public static EditorPrimitives fromModules() {
        EditorPrimitives primitives = new EditorPrimitives();
        List<EngineModule> modules = new ArrayList<>();
        for (EngineModule module : ServiceLoader.load(EngineModule.class)) {
            modules.add(module);
        }
        modules.sort(Comparator.comparingInt(EngineModule::order));
        for (EngineModule module : modules) {
            module.registerEditorExtensions(primitives);
        }
        return primitives;
    }
}
