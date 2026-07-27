package fr.epistudio.epysia.scripting;

import fr.epistudio.epysia.EngineServices;
import fr.epistudio.epysia.GameSystem;
import fr.epistudio.epysia.components.IComponent;
import fr.epistudio.epysia.gameobjects.GameObject;
import fr.epistudio.epysia.input.InputState;
import fr.epistudio.epysia.scene.Scene;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class ScriptDispatcherSystem implements GameSystem {

    private final Set<Behaviour> startedBehaviours = new HashSet<>();
    private final Map<Behaviour, Boolean> enabledState = new IdentityHashMap<>();
    private final List<Behaviour> cachedBehaviours = new ArrayList<>();
    private Scene cachedScene;
    private long cachedModificationCount = Long.MIN_VALUE;
    private EngineServices services;

    @Override
    public void initialize(EngineServices services) {
        this.services = services;
    }

    @Override
    public void update(Scene scene, InputState input, float deltaTimeSeconds) {
        refreshCacheIfStructureChanged(scene);
        for (Behaviour behaviour : cachedBehaviours) {
            if (startedBehaviours.add(behaviour)) {
                safeOnStart(behaviour);
            }
        }
        for (Behaviour behaviour : cachedBehaviours) {
            updateEnabledState(behaviour);
        }
        for (Behaviour behaviour : cachedBehaviours) {
            if (isCurrentlyEnabled(behaviour)) {
                safeOnUpdate(behaviour, input, deltaTimeSeconds);
            }
        }
    }

    private void refreshCacheIfStructureChanged(Scene scene) {
        long modificationCount = scene.modificationCount();
        if (scene == cachedScene && modificationCount == cachedModificationCount) {
            return;
        }
        cachedScene = scene;
        cachedModificationCount = modificationCount;
        collectBehaviours(scene);
        invokeDestroyForRemovedBehaviours();
    }

    @Override
    public void shutdown() {
        destroyAllStarted();
        invalidateCache();
        services = null;
    }

    public void resetForPlaySession() {
        destroyAllStarted();
        invalidateCache();
    }

    private void destroyAllStarted() {
        for (Behaviour behaviour : startedBehaviours) {
            if (Boolean.TRUE.equals(enabledState.get(behaviour))) {
                safeOnDisable(behaviour);
            }
            safeOnDestroy(behaviour);
        }
        startedBehaviours.clear();
        enabledState.clear();
    }

    private void invalidateCache() {
        cachedBehaviours.clear();
        cachedScene = null;
        cachedModificationCount = Long.MIN_VALUE;
    }

    private boolean isOwnerActive(Behaviour behaviour) {
        return behaviour.owner().map(GameObject::active).orElse(false);
    }

    private void updateEnabledState(Behaviour behaviour) {
        boolean shouldBeEnabled = isOwnerActive(behaviour);
        Boolean previous = enabledState.get(behaviour);
        if (previous == null) {
            enabledState.put(behaviour, shouldBeEnabled);
            if (shouldBeEnabled) {
                safeOnEnable(behaviour);
            }
            return;
        }
        if (previous == shouldBeEnabled) {
            return;
        }
        enabledState.put(behaviour, shouldBeEnabled);
        if (shouldBeEnabled) {
            safeOnEnable(behaviour);
        } else {
            safeOnDisable(behaviour);
        }
    }

    private boolean isCurrentlyEnabled(Behaviour behaviour) {
        return Boolean.TRUE.equals(enabledState.get(behaviour));
    }

    private void safeOnStart(Behaviour behaviour) {
        try {
            behaviour.onStart(services);
        } catch (RuntimeException error) {
            logError("onStart", behaviour, error);
        }
    }

    private void safeOnEnable(Behaviour behaviour) {
        try {
            behaviour.onEnable();
        } catch (RuntimeException error) {
            logError("onEnable", behaviour, error);
        }
    }

    private void safeOnDisable(Behaviour behaviour) {
        try {
            behaviour.onDisable();
        } catch (RuntimeException error) {
            logError("onDisable", behaviour, error);
        }
    }

    private void safeOnUpdate(Behaviour behaviour, InputState input, float deltaTimeSeconds) {
        try {
            behaviour.onUpdate(input, deltaTimeSeconds);
        } catch (RuntimeException error) {
            logError("onUpdate", behaviour, error);
        }
    }

    private void safeOnDestroy(Behaviour behaviour) {
        try {
            behaviour.onDestroy();
        } catch (RuntimeException error) {
            logError("onDestroy", behaviour, error);
        }
    }

    private void logError(String hook, Behaviour behaviour, RuntimeException error) {
        if (services != null) {
            services.logger().error("[ScriptDispatcher] " + hook + " threw in "
                    + behaviour.getClass().getName(), error);
        }
    }

    private void collectBehaviours(Scene scene) {
        cachedBehaviours.clear();
        cachedBehaviours.addAll(scene.componentsOf(Behaviour.class));
    }

    private void invokeDestroyForRemovedBehaviours() {
        Set<Behaviour> alive = new HashSet<>(cachedBehaviours);
        startedBehaviours.removeIf(behaviour -> {
            if (alive.contains(behaviour)) {
                return false;
            }
            if (Boolean.TRUE.equals(enabledState.get(behaviour))) {
                safeOnDisable(behaviour);
            }
            enabledState.remove(behaviour);
            safeOnDestroy(behaviour);
            return true;
        });
    }
}
