package fr.epistudio.epysia.reflection;

import fr.epistudio.epysia.components.IComponent;
import fr.epistudio.epysia.exceptions.EpysiaException;

import java.lang.reflect.Constructor;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;

public final class ComponentRegistry {

    private final List<Entry> builtinEntries = new ArrayList<>();
    private final List<Entry> userEntries = new ArrayList<>();
    private List<Entry> combinedEntries = List.of();

    public void populateFromScan(List<DiscoveredComponent> discovered) {
        builtinEntries.clear();
        for (DiscoveredComponent component : discovered) {
            builtinEntries.add(toEntry(component));
        }
        rebuildCombinedEntries();
    }

    public void setUserComponents(List<DiscoveredComponent> discovered) {
        userEntries.clear();
        for (DiscoveredComponent component : discovered) {
            userEntries.add(toEntry(component));
        }
        rebuildCombinedEntries();
    }

    private void rebuildCombinedEntries() {
        List<Entry> combined = new ArrayList<>(builtinEntries.size() + userEntries.size());
        combined.addAll(builtinEntries);
        combined.addAll(userEntries);
        combinedEntries = List.copyOf(combined);
    }

    public List<Entry> entries() {
        return combinedEntries;
    }

    public Optional<Supplier<IComponent>> factoryFor(Class<? extends IComponent> componentClass) {
        for (Entry entry : entries()) {
            if (entry.componentClass().equals(componentClass)) {
                return Optional.of(entry.factory());
            }
        }
        return Optional.empty();
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
                    + " needs a no-arg constructor for runtime instantiation.");
        }
    }

    private static IComponent instantiate(Constructor<? extends IComponent> constructor) {
        try {
            return constructor.newInstance();
        } catch (ReflectiveOperationException exception) {
            throw new EpysiaException("Failed to instantiate component "
                    + constructor.getDeclaringClass().getName(), exception);
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
