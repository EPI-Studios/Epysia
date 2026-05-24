package fr.epistudio.epysia.scripting;

import fr.epistudio.epysia.EngineServices;
import fr.epistudio.epysia.GameSystem;
import fr.epistudio.epysia.components.IComponent;
import fr.epistudio.epysia.gameobjects.GameObject;
import fr.epistudio.epysia.input.InputState;
import fr.epistudio.epysia.scene.Scene;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public final class ScriptDispatcherSystem implements GameSystem {

    private final Set<Behaviour> startedBehaviours = new HashSet<>();
    private final List<Behaviour> scratchBehaviours = new ArrayList<>();
    private EngineServices services;

    @Override
    public void initialize(EngineServices services) {
        this.services = services;
    }

    @Override
    public void update(Scene scene, InputState input, float deltaTimeSeconds) {
        collectBehaviours(scene);
        for (Behaviour behaviour : scratchBehaviours) {
            if (startedBehaviours.add(behaviour)) {
                safeOnStart(behaviour);
            }
        }
        for (Behaviour behaviour : scratchBehaviours) {
            safeOnUpdate(behaviour, input, deltaTimeSeconds);
        }
        invokeDestroyForRemovedBehaviours();
    }

    @Override
    public void shutdown() {
        for (Behaviour behaviour : startedBehaviours) {
            safeOnDestroy(behaviour);
        }
        startedBehaviours.clear();
        scratchBehaviours.clear();
        services = null;
    }

    private void safeOnStart(Behaviour behaviour) {
        try {
            behaviour.onStart(services);
        } catch (RuntimeException error) {
            System.err.println("[ScriptDispatcher] onStart threw in "
                    + behaviour.getClass().getName() + ": " + error);
            error.printStackTrace();
        }
    }

    private void safeOnUpdate(Behaviour behaviour, InputState input, float deltaTimeSeconds) {
        try {
            behaviour.onUpdate(input, deltaTimeSeconds);
        } catch (RuntimeException error) {
            System.err.println("[ScriptDispatcher] onUpdate threw in "
                    + behaviour.getClass().getName() + ": " + error);
            error.printStackTrace();
        }
    }

    private void safeOnDestroy(Behaviour behaviour) {
        try {
            behaviour.onDestroy();
        } catch (RuntimeException error) {
            System.err.println("[ScriptDispatcher] onDestroy threw in "
                    + behaviour.getClass().getName() + ": " + error);
        }
    }

    private void collectBehaviours(Scene scene) {
        scratchBehaviours.clear();
        for (GameObject gameObject : scene.gameObjects()) {
            for (IComponent component : allComponentsOf(gameObject)) {
                if (component instanceof Behaviour behaviour) {
                    scratchBehaviours.add(behaviour);
                }
            }
        }
    }

    private void invokeDestroyForRemovedBehaviours() {
        if (startedBehaviours.size() == scratchBehaviours.size()) {
            return;
        }
        Set<Behaviour> alive = new HashSet<>(scratchBehaviours);
        startedBehaviours.removeIf(behaviour -> {
            if (alive.contains(behaviour)) {
                return false;
            }
            safeOnDestroy(behaviour);
            return true;
        });
    }

    private static List<IComponent> allComponentsOf(GameObject gameObject) {
        return gameObject.components();
    }
}
