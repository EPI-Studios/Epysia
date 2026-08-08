package fr.epistudio.epysia.input.action;

import fr.epistudio.epysia.input.InputState;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public final class InputActions {
    public static final String UNNAMED_ACTION = "NewAction";

    private final Map<String, InputAction> byName = new LinkedHashMap<>();

    public static InputActions defaults() {
        return new InputActions();
    }

    public static List<InputAction> defaultActions() {
        return List.of();
    }

    public static String uniqueNameAmong(List<InputAction> actions, String desiredName) {
        String trimmed = desiredName.trim();
        String base = trimmed.isEmpty() ? UNNAMED_ACTION : trimmed;
        String candidate = base;
        int suffix = 2;
        while (containsName(actions, candidate)) {
            candidate = base + suffix;
            suffix++;
        }
        return candidate;
    }

    private static boolean containsName(List<InputAction> actions, String name) {
        for (InputAction action : actions) {
            if (action.name().equals(name)) {
                return true;
            }
        }
        return false;
    }

    public void replaceAll(List<InputAction> actions) {
        byName.clear();
        for (InputAction action : actions) {
            byName.put(action.name(), action);
        }
    }

    public List<InputAction> all() {
        return List.copyOf(byName.values());
    }

    public Optional<InputAction> find(String name) {
        return Optional.ofNullable(byName.get(name));
    }

    public boolean isDown(String name, InputState input) {
        InputAction action = byName.get(name);
        return action != null && action.isDown(input);
    }

    public boolean wasPressed(String name, InputState input) {
        InputAction action = byName.get(name);
        return action != null && action.wasPressed(input);
    }

    public boolean wasReleased(String name, InputState input) {
        InputAction action = byName.get(name);
        return action != null && action.wasReleased(input);
    }

    public float value(String name, InputState input) {
        InputAction action = byName.get(name);
        return action == null ? 0.0f : action.value(input);
    }

    public boolean consumeBufferedPress(String name, InputState input, float withinSeconds) {
        InputAction action = byName.get(name);
        return action != null && action.consumeBufferedPress(input, withinSeconds);
    }
}
