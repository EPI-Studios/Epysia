package fr.epistudio.epysia;

import fr.epistudio.epysia.gameobjects.GameObject;

import java.util.function.Supplier;

public interface EditorContext {

    void registerPrimitive(String displayName, Supplier<GameObject> factory);
}
