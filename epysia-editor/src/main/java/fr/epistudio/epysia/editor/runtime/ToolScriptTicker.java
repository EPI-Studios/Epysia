package fr.epistudio.epysia.editor.runtime;

import fr.epistudio.epysia.EngineServices;
import fr.epistudio.epysia.input.InputState;
import fr.epistudio.epysia.scene.Scene;
import fr.epistudio.epysia.scripting.Behaviour;
import fr.epistudio.epysia.scripting.RunsInEditor;

import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.WeakHashMap;
import java.util.function.Consumer;

public final class ToolScriptTicker {

    private final Set<Behaviour> started = Collections.newSetFromMap(new WeakHashMap<>());

    public boolean tick(Scene scene, EngineServices services, float deltaTimeSeconds,
                        Consumer<String> onFailure) {
        List<Behaviour> behaviours = List.copyOf(scene.componentsOf(Behaviour.class));
        boolean ticked = false;
        for (Behaviour behaviour : behaviours) {
            if (runsInEditor(behaviour)) {
                ticked |= tickOne(behaviour, services, deltaTimeSeconds, onFailure);
            }
        }
        return ticked;
    }

    private boolean tickOne(Behaviour behaviour, EngineServices services, float deltaTimeSeconds,
                            Consumer<String> onFailure) {
        if (!behaviour.enabled()) {
            return false;
        }
        try {
            if (started.add(behaviour)) {
                behaviour.onStart(services);
            }
            behaviour.onUpdate(InputState.inactive(), deltaTimeSeconds);
            return true;
        } catch (RuntimeException error) {
            started.remove(behaviour);
            onFailure.accept(behaviour.getClass().getSimpleName() + " tool script failed: " + error.getMessage());
            return false;
        }
    }

    public void forget(Behaviour behaviour) {
        started.remove(behaviour);
    }

    public void reset() {
        started.clear();
    }

    private static boolean runsInEditor(Behaviour behaviour) {
        return behaviour.getClass().isAnnotationPresent(RunsInEditor.class);
    }
}
