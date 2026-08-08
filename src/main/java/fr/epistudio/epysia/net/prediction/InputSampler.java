package fr.epistudio.epysia.net.prediction;

import fr.epistudio.epysia.input.InputState;
import fr.epistudio.epysia.input.action.InputAction;
import fr.epistudio.epysia.input.action.InputActions;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public final class InputSampler {
    private final List<String> actionNames;

    private InputSampler(List<String> actionNames) {
        this.actionNames = List.copyOf(actionNames);
    }

    public static InputSampler forActions(InputActions actions) {
        List<String> names = new ArrayList<>();
        for (InputAction action : actions.all()) {
            if (names.size() < Long.SIZE) {
                names.add(action.name());
            }
        }
        return new InputSampler(names);
    }

    public int actionCount() {
        return actionNames.size();
    }

    public Optional<Integer> indexOf(String actionName) {
        int index = actionNames.indexOf(actionName);
        return index < 0 ? Optional.empty() : Optional.of(index);
    }

    public InputSample sample(int tick, InputActions actions, InputState input) {
        long downMask = 0L;
        float[] axisValues = new float[actionNames.size()];
        for (int index = 0; index < actionNames.size(); index++) {
            String name = actionNames.get(index);
            if (actions.isDown(name, input)) {
                downMask |= 1L << index;
            }
            axisValues[index] = actions.value(name, input);
        }
        return new InputSample(tick, downMask, axisValues);
    }
}
