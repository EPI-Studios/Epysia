package fr.epistudio.epysia.input.action;

import fr.epistudio.epysia.input.InputState;

import java.util.List;

public record InputAction(String name, List<InputBinding> positive, List<InputBinding> negative) {

    public InputAction {
        positive = List.copyOf(positive);
        negative = List.copyOf(negative);
    }

    public static InputAction button(String name, InputBinding... bindings) {
        return new InputAction(name, List.of(bindings), List.of());
    }

    public static InputAction axis(String name, List<InputBinding> positive, List<InputBinding> negative) {
        return new InputAction(name, positive, negative);
    }

    public boolean isDown(InputState input) {
        return anyDown(positive, input) || anyDown(negative, input);
    }

    public boolean wasPressed(InputState input) {
        return anyPressed(positive, input) || anyPressed(negative, input);
    }

    public boolean wasReleased(InputState input) {
        return anyReleased(positive, input) || anyReleased(negative, input);
    }

    public float value(InputState input) {
        return Math.clamp(strongest(positive, input) - strongest(negative, input), -1.0f, 1.0f);
    }

    public boolean consumeBufferedPress(InputState input, float withinSeconds) {
        for (InputBinding binding : positive) {
            if (binding.consumeBufferedPress(input, withinSeconds)) {
                return true;
            }
        }
        for (InputBinding binding : negative) {
            if (binding.consumeBufferedPress(input, withinSeconds)) {
                return true;
            }
        }
        return false;
    }

    private static float strongest(List<InputBinding> bindings, InputState input) {
        float best = 0.0f;
        for (InputBinding binding : bindings) {
            best = Math.max(best, binding.value(input));
        }
        return best;
    }

    private static boolean anyDown(List<InputBinding> bindings, InputState input) {
        for (InputBinding binding : bindings) {
            if (binding.isDown(input)) {
                return true;
            }
        }
        return false;
    }

    private static boolean anyPressed(List<InputBinding> bindings, InputState input) {
        for (InputBinding binding : bindings) {
            if (binding.wasPressed(input)) {
                return true;
            }
        }
        return false;
    }

    private static boolean anyReleased(List<InputBinding> bindings, InputState input) {
        for (InputBinding binding : bindings) {
            if (binding.wasReleased(input)) {
                return true;
            }
        }
        return false;
    }
}
