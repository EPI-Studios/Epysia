package fr.epistudio.epysia.editor.inspector;

import fr.epistudio.epysia.components.IComponent;
import fr.epistudio.epysia.gameobjects.GameObject;

import java.util.function.BiConsumer;

public final class TypedComponentSection<T extends IComponent> implements ComponentSection {

    private final Class<T> componentType;
    private final BiConsumer<GameObject, T> body;

    private TypedComponentSection(Class<T> componentType, BiConsumer<GameObject, T> body) {
        this.componentType = componentType;
        this.body = body;
    }

    public static <T extends IComponent> ComponentSection of(Class<T> componentType,
                                                             BiConsumer<GameObject, T> body) {
        return new TypedComponentSection<>(componentType, body);
    }

    @Override
    public boolean handles(IComponent component) {
        return componentType.isInstance(component);
    }

    @Override
    public void render(GameObject gameObject, IComponent component) {
        body.accept(gameObject, componentType.cast(component));
    }
}
