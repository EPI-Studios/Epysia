package fr.epistudio.epysia.input.action;

import fr.epistudio.epysia.input.InputState;
import fr.epistudio.epysia.input.KeyCode;
import fr.epistudio.epysia.input.MouseButton;
import fr.epistudio.epysia.input.gamepad.GamepadAxis;
import fr.epistudio.epysia.input.gamepad.GamepadButton;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public final class InputActions {

    public static final String MOVE_FORWARD = "MoveForward";
    public static final String MOVE_RIGHT = "MoveRight";
    public static final String JUMP = "Jump";
    public static final String SPRINT = "Sprint";
    public static final String FIRE = "Fire";
    public static final String CANCEL = "Cancel";

    private final Map<String, InputAction> byName = new LinkedHashMap<>();

    public static InputActions defaults() {
        InputActions actions = new InputActions();
        actions.replaceAll(defaultActions());
        return actions;
    }

    public static List<InputAction> defaultActions() {
        List<InputAction> actions = new ArrayList<>();
        actions.add(InputAction.axis(MOVE_FORWARD,
                List.of(InputBinding.key(KeyCode.W),
                        InputBinding.gamepadAxis(GamepadAxis.LEFT_Y, 0, true)),
                List.of(InputBinding.key(KeyCode.S),
                        InputBinding.gamepadAxis(GamepadAxis.LEFT_Y, 0, false))));
        actions.add(InputAction.axis(MOVE_RIGHT,
                List.of(InputBinding.key(KeyCode.D),
                        InputBinding.gamepadAxis(GamepadAxis.LEFT_X, 0, false)),
                List.of(InputBinding.key(KeyCode.A),
                        InputBinding.gamepadAxis(GamepadAxis.LEFT_X, 0, true))));
        actions.add(InputAction.button(JUMP, InputBinding.key(KeyCode.SPACE),
                InputBinding.gamepadButton(GamepadButton.SOUTH)));
        actions.add(InputAction.button(SPRINT, InputBinding.key(KeyCode.LEFT_SHIFT),
                InputBinding.gamepadButton(GamepadButton.LEFT_THUMB)));
        actions.add(InputAction.button(FIRE, InputBinding.mouse(MouseButton.LEFT),
                InputBinding.gamepadAxis(GamepadAxis.RIGHT_TRIGGER)));
        actions.add(InputAction.button(CANCEL, InputBinding.key(KeyCode.ESCAPE),
                InputBinding.gamepadButton(GamepadButton.START)));
        return actions;
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
