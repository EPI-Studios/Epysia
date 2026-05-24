package fr.epistudio.epysia.editor;

import fr.epistudio.epysia.components.IComponent;
import fr.epistudio.epysia.exceptions.EpysiaException;
import fr.epistudio.epysia.reflection.DiscoveredComponent;

import java.lang.reflect.Constructor;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

public final class EditorComponentRegistry {

    private final List<Entry> entries = new ArrayList<>();

    public void populateFromScan(List<DiscoveredComponent> discovered) {
        entries.clear();
        for (DiscoveredComponent component : discovered) {
            entries.add(toEntry(component));
        }
    }

    public List<Entry> entries() {
        return entries;
    }

    public java.util.Optional<Supplier<IComponent>> factoryFor(Class<? extends IComponent> componentClass) {
        for (Entry entry : entries) {
            if (entry.componentClass().equals(componentClass)) {
                return java.util.Optional.of(entry.factory());
            }
        }
        return java.util.Optional.empty();
    }

    private static Entry toEntry(DiscoveredComponent discovered) {
        Supplier<IComponent> factory = defaultFactoryFor(discovered.componentClass());
        return new Entry(discovered.displayName(), discovered.category(), discovered.icon(),
                discovered.componentClass(), factory);
    }

    private static Supplier<IComponent> defaultFactoryFor(Class<? extends IComponent> componentClass) {
        Constructor<? extends IComponent> noArgConstructor = resolveNoArgConstructor(componentClass);
        return () -> instantiate(noArgConstructor);
    }

    private static Constructor<? extends IComponent> resolveNoArgConstructor(Class<? extends IComponent> componentClass) {
        try {
            Constructor<? extends IComponent> constructor = componentClass.getDeclaredConstructor();
            constructor.setAccessible(true);
            return constructor;
        } catch (NoSuchMethodException exception) {
            throw new EpysiaException("@EpysiaComponent " + componentClass.getName()
                    + " needs a public no-arg constructor for the editor to instantiate it.");
        }
    }

    private static IComponent instantiate(Constructor<? extends IComponent> constructor) {
        try {
            return constructor.newInstance();
        } catch (ReflectiveOperationException exception) {
            throw new EpysiaException("Failed to instantiate component " + constructor.getDeclaringClass().getName(), exception);
        }
    }

    public record Entry(
            String displayName,
            String category,
            String icon,
            Class<? extends IComponent> componentClass,
            Supplier<IComponent> factory
    ) {
    }
}
