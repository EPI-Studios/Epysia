package fr.epistudio.epysia.prefab;

import fr.epistudio.epysia.components.IComponent;
import fr.epistudio.epysia.gameobjects.GameObject;
import fr.epistudio.epysia.reflection.ComponentFieldCodec;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class PrefabInstanceSnapshot {

    private record ComponentState(IComponent component, Map<String, Object> fields) {
    }

    private final List<ComponentState> componentStates;
    private final Set<String> overriddenProperties;

    private PrefabInstanceSnapshot(List<ComponentState> componentStates,
                                   Set<String> overriddenProperties) {
        this.componentStates = componentStates;
        this.overriddenProperties = overriddenProperties;
    }

    public static PrefabInstanceSnapshot capture(GameObject instance) {
        List<ComponentState> states = new ArrayList<>();
        for (IComponent component : instance.components()) {
            states.add(new ComponentState(component, ComponentFieldCodec.capture(component)));
        }
        return new PrefabInstanceSnapshot(states,
                new LinkedHashSet<>(instance.overriddenProperties()));
    }

    public void restoreInto(GameObject instance, PrefabFieldApplier applier) {
        for (ComponentState state : componentStates) {
            applier.applyFields(state.component(), state.fields());
        }
        instance.clearOverrides();
        for (String key : overriddenProperties) {
            instance.markOverridden(key);
        }
    }
}
