package fr.epistudio.epysia;

import fr.epistudio.epysia.exceptions.EpysiaException;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class SystemRegistryImpl implements SystemRegistry {

    private final Map<Class<? extends GameSystem>, GameSystem> systemsByType = new LinkedHashMap<>();

    @Override
    public void add(GameSystem system) {
        if (system == null) {
            throw new EpysiaException("Cannot register a null GameSystem.");
        }
        Class<? extends GameSystem> type = system.getClass();
        if (systemsByType.containsKey(type)) {
            throw new EpysiaException("GameSystem already registered: " + type.getName());
        }
        systemsByType.put(type, system);
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
        return Collections.unmodifiableList(new ArrayList<>(systemsByType.values()));
    }
}
