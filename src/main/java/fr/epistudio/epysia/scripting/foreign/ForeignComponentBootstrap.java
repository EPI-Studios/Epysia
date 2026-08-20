package fr.epistudio.epysia.scripting.foreign;

import fr.epistudio.epysia.exceptions.EpysiaException;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class ForeignComponentBootstrap {

    private static final Map<String, ForeignComponentType> TYPES = new ConcurrentHashMap<>();

    private ForeignComponentBootstrap() {
    }

    public static void register(String key, ForeignComponentType type) {
        TYPES.put(key, type);
    }

    public static void clear() {
        TYPES.clear();
    }

    public static ForeignComponentType typeOf(String key) {
        ForeignComponentType type = TYPES.get(key);
        if (type == null) {
            throw new EpysiaException("No foreign component registered for " + key);
        }
        return type;
    }

    public static ForeignInstance instanceOf(String key) {
        return typeOf(key).instantiate();
    }
}
