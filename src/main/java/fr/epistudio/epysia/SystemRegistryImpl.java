package fr.epistudio.epysia;

import fr.epistudio.epysia.exceptions.EpysiaException;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public final class SystemRegistryImpl implements SystemRegistry {
    private final Map<Class<? extends GameSystem>, GameSystem> systemsByType = new LinkedHashMap<>();
    private final List<GameSystem> registrationOrder = new ArrayList<>();

    @Override
    public void add(GameSystem system) {
        if (system == null) {
            throw new EpysiaException("Cannot register a null GameSystem.");
        }
        if (registrationOrder.contains(system)) {
            throw new EpysiaException("GameSystem already registered: " + system.getClass().getName());
        }
        registrationOrder.add(system);
        registerSystemUnderAllTypes(system);
    }

    private void registerSystemUnderAllTypes(GameSystem system) {
        for (Class<?> type : collectGameSystemTypes(system.getClass())) {
            @SuppressWarnings("unchecked")
            Class<? extends GameSystem> systemType = (Class<? extends GameSystem>) type;
            systemsByType.putIfAbsent(systemType, system);
        }
    }

    private static List<Class<?>> collectGameSystemTypes(Class<?> rawType) {
        List<Class<?>> types = new ArrayList<>();
        Class<?> current = rawType;
        while (current != null && current != Object.class) {
            if (GameSystem.class.isAssignableFrom(current)) {
                types.add(current);
            }
            for (Class<?> implemented : current.getInterfaces()) {
                if (GameSystem.class.isAssignableFrom(implemented) && !types.contains(implemented)) {
                    types.add(implemented);
                }
            }
            current = current.getSuperclass();
        }
        return types;
    }

    @Override
    public <T extends GameSystem> Optional<T> find(Class<T> type) {
        GameSystem system = systemsByType.get(type);
        return system == null ? Optional.empty() : Optional.of(type.cast(system));
    }

    @Override
    public <T extends GameSystem> T get(Class<T> type) {
        GameSystem system = systemsByType.get(type);
        if (system == null) {
            throw new EpysiaException("No GameSystem registered for " + type.getName());
        }
        return type.cast(system);
    }

    public List<GameSystem> systems() {
        return Collections.unmodifiableList(new ArrayList<>(registrationOrder));
    }
}
