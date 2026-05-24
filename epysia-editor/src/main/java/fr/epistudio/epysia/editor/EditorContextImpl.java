package fr.epistudio.epysia.editor;

import fr.epistudio.epysia.EditorContext;
import fr.epistudio.epysia.gameobjects.GameObject;

import java.util.function.Supplier;

public final class EditorContextImpl implements EditorContext {

    private final EditorPrimitiveRegistry primitiveRegistry;

    public EditorContextImpl(EditorPrimitiveRegistry primitiveRegistry) {
        this.primitiveRegistry = primitiveRegistry;
    }

    @Override
    public void registerPrimitive(String displayName, Supplier<GameObject> factory) {
        primitiveRegistry.register(displayName, factory);
    }
}
