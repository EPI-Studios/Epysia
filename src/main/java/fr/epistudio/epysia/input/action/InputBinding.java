package fr.epistudio.epysia.input.action;

import fr.epistudio.epysia.input.InputState;
import fr.epistudio.epysia.input.KeyCode;
import fr.epistudio.epysia.input.KeyModifier;
import fr.epistudio.epysia.input.MouseButton;
import fr.epistudio.epysia.input.gamepad.GamepadAxis;
import fr.epistudio.epysia.input.gamepad.GamepadButton;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

public final class InputBinding {

    private static final KeyCode[] KEYS = KeyCode.values();
    private static final MouseButton[] MOUSE_BUTTONS = MouseButton.values();
    private static final GamepadButton[] GAMEPAD_BUTTONS = GamepadButton.values();
    private static final GamepadAxis[] GAMEPAD_AXES = GamepadAxis.values();

    private static final String MODIFIER_SEPARATOR = "|";
    private static final String MODIFIER_JOIN = "+";
    private static final float BUTTON_THRESHOLD = 0.5f;

    public enum Source {
        KEY,
        MOUSE,
        GAMEPAD_BUTTON,
        GAMEPAD_AXIS
    }

    private final Source source;
    private final int codeOrdinal;
    private final Set<KeyModifier> modifiers;
    private final int gamepadIndex;
    private final boolean invertedAxis;

    private InputBinding(Source source, int codeOrdinal, Set<KeyModifier> modifiers,
                         int gamepadIndex, boolean invertedAxis) {
        this.source = source;
        this.codeOrdinal = codeOrdinal;
        this.modifiers = modifiers.isEmpty() ? Set.of() : EnumSet.copyOf(modifiers);
        this.gamepadIndex = gamepadIndex;
        this.invertedAxis = invertedAxis;
    }

    public static InputBinding key(KeyCode key) {
        return new InputBinding(Source.KEY, key.ordinal(), Set.of(), 0, false);
    }

    public static InputBinding key(KeyCode key, KeyModifier... required) {
        return new InputBinding(Source.KEY, key.ordinal(), Set.of(required), 0, false);
    }

    public static InputBinding mouse(MouseButton button) {
        return new InputBinding(Source.MOUSE, button.ordinal(), Set.of(), 0, false);
    }

    public static InputBinding gamepadButton(GamepadButton button) {
        return gamepadButton(button, 0);
    }

    public static InputBinding gamepadButton(GamepadButton button, int gamepadIndex) {
        return new InputBinding(Source.GAMEPAD_BUTTON, button.ordinal(), Set.of(), gamepadIndex, false);
    }

    public static InputBinding gamepadAxis(GamepadAxis axis) {
        return gamepadAxis(axis, 0, false);
    }

    public static InputBinding gamepadAxis(GamepadAxis axis, int gamepadIndex, boolean inverted) {
        return new InputBinding(Source.GAMEPAD_AXIS, axis.ordinal(), Set.of(), gamepadIndex, inverted);
    }

    public Source source() {
        return source;
    }

    public String code() {
        return switch (source) {
            case KEY -> KEYS[codeOrdinal].name();
            case MOUSE -> MOUSE_BUTTONS[codeOrdinal].name();
            case GAMEPAD_BUTTON -> GAMEPAD_BUTTONS[codeOrdinal].name();
            case GAMEPAD_AXIS -> GAMEPAD_AXES[codeOrdinal].name();
        };
    }

    public Set<KeyModifier> modifiers() {
        return modifiers;
    }

    public int gamepadIndex() {
        return gamepadIndex;
    }

    public static Optional<InputBinding> parse(String text) {
        if (text == null || !text.contains(":")) {
            return Optional.empty();
        }
        String[] halves = text.split(":", 2);
        String body = halves[1];
        Set<KeyModifier> required = new LinkedHashSet<>();
        int separator = body.indexOf(MODIFIER_SEPARATOR);
        if (separator >= 0) {
            collectModifiers(body.substring(separator + 1), required);
            body = body.substring(0, separator);
        }
        return build(halves[0].toLowerCase(Locale.ROOT), body, required);
    }

    private static Optional<InputBinding> build(String sourceName, String body, Set<KeyModifier> required) {
        return switch (sourceName) {
            case "key" -> ordinalOf(KEYS, body).map(ordinal ->
                    new InputBinding(Source.KEY, ordinal, required, 0, false));
            case "mouse" -> ordinalOf(MOUSE_BUTTONS, body).map(ordinal ->
                    new InputBinding(Source.MOUSE, ordinal, required, 0, false));
            case "pad" -> parseGamepadButton(body);
            case "axis" -> parseGamepadAxis(body);
            default -> Optional.empty();
        };
    }

    private static Optional<InputBinding> parseGamepadButton(String body) {
        GamepadReference reference = GamepadReference.of(body);
        return ordinalOf(GAMEPAD_BUTTONS, reference.code()).map(ordinal ->
                new InputBinding(Source.GAMEPAD_BUTTON, ordinal, Set.of(), reference.index(), false));
    }

    private static Optional<InputBinding> parseGamepadAxis(String body) {
        GamepadReference reference = GamepadReference.of(body);
        boolean inverted = reference.code().startsWith("-");
        String bare = inverted ? reference.code().substring(1) : reference.code();
        return ordinalOf(GAMEPAD_AXES, bare).map(ordinal ->
                new InputBinding(Source.GAMEPAD_AXIS, ordinal, Set.of(), reference.index(), inverted));
    }

    private record GamepadReference(int index, String code) {

        private static GamepadReference of(String body) {
            int at = body.indexOf('@');
            if (at < 0) {
                return new GamepadReference(0, body);
            }
            return new GamepadReference(parseIndex(body.substring(at + 1)), body.substring(0, at));
        }

        private static int parseIndex(String text) {
            try {
                return Math.max(0, Integer.parseInt(text.trim()));
            } catch (NumberFormatException malformed) {
                return 0;
            }
        }
    }

    private static Optional<Integer> ordinalOf(Enum<?>[] candidates, String name) {
        for (Enum<?> candidate : candidates) {
            if (candidate.name().equalsIgnoreCase(name)) {
                return Optional.of(candidate.ordinal());
            }
        }
        return Optional.empty();
    }

    private static void collectModifiers(String text, Set<KeyModifier> into) {
        for (String part : text.split("\\" + MODIFIER_JOIN)) {
            KeyModifier.named(part.trim()).ifPresent(into::add);
        }
    }

    public String serialized() {
        return sourcePrefix() + ":" + serializedBody() + serializedModifiers();
    }

    private String sourcePrefix() {
        return switch (source) {
            case KEY -> "key";
            case MOUSE -> "mouse";
            case GAMEPAD_BUTTON -> "pad";
            case GAMEPAD_AXIS -> "axis";
        };
    }

    private String serializedBody() {
        String bare = source == Source.GAMEPAD_AXIS && invertedAxis ? "-" + code() : code();
        if (source != Source.GAMEPAD_BUTTON && source != Source.GAMEPAD_AXIS) {
            return bare;
        }
        return gamepadIndex == 0 ? bare : bare + "@" + gamepadIndex;
    }

    private String serializedModifiers() {
        if (modifiers.isEmpty()) {
            return "";
        }
        List<String> names = new ArrayList<>();
        for (KeyModifier modifier : modifiers) {
            names.add(modifier.name().toLowerCase(Locale.ROOT));
        }
        return MODIFIER_SEPARATOR + String.join(MODIFIER_JOIN, names);
    }

    public boolean isDown(InputState input) {
        return modifiersHeld(input) && switch (source) {
            case KEY -> input.isKeyDown(KEYS[codeOrdinal]);
            case MOUSE -> input.isMouseButtonDown(MOUSE_BUTTONS[codeOrdinal]);
            case GAMEPAD_BUTTON -> input.gamepad(gamepadIndex).isButtonDown(GAMEPAD_BUTTONS[codeOrdinal]);
            case GAMEPAD_AXIS -> axisValue(input) >= BUTTON_THRESHOLD;
        };
    }

    public boolean wasPressed(InputState input) {
        return modifiersHeld(input) && switch (source) {
            case KEY -> input.wasKeyPressed(KEYS[codeOrdinal]);
            case MOUSE -> input.wasMouseButtonPressed(MOUSE_BUTTONS[codeOrdinal]);
            case GAMEPAD_BUTTON -> input.gamepad(gamepadIndex).wasButtonPressed(GAMEPAD_BUTTONS[codeOrdinal]);
            case GAMEPAD_AXIS -> false;
        };
    }

    public boolean wasReleased(InputState input) {
        return switch (source) {
            case KEY -> input.wasKeyReleased(KEYS[codeOrdinal]);
            case MOUSE -> input.wasMouseButtonReleased(MOUSE_BUTTONS[codeOrdinal]);
            case GAMEPAD_BUTTON -> input.gamepad(gamepadIndex).wasButtonReleased(GAMEPAD_BUTTONS[codeOrdinal]);
            case GAMEPAD_AXIS -> false;
        };
    }

    public boolean consumeBufferedPress(InputState input, float withinSeconds) {
        if (!modifiersHeld(input)) {
            return false;
        }
        return switch (source) {
            case KEY -> input.consumeBufferedKeyPress(KEYS[codeOrdinal], withinSeconds);
            case MOUSE -> input.consumeBufferedMouseButtonPress(MOUSE_BUTTONS[codeOrdinal], withinSeconds);
            case GAMEPAD_BUTTON -> input.gamepad(gamepadIndex).wasButtonPressed(GAMEPAD_BUTTONS[codeOrdinal]);
            case GAMEPAD_AXIS -> false;
        };
    }

    public float value(InputState input) {
        if (source != Source.GAMEPAD_AXIS) {
            return isDown(input) ? 1.0f : 0.0f;
        }
        return Math.max(0.0f, axisValue(input));
    }

    private float axisValue(InputState input) {
        float raw = input.gamepad(gamepadIndex).axis(GAMEPAD_AXES[codeOrdinal]);
        return invertedAxis ? -raw : raw;
    }

    private boolean modifiersHeld(InputState input) {
        for (KeyModifier modifier : modifiers) {
            if (!input.isModifierDown(modifier)) {
                return false;
            }
        }
        return true;
    }

    @Override
    public boolean equals(Object other) {
        if (!(other instanceof InputBinding binding)) {
            return false;
        }
        return source == binding.source && codeOrdinal == binding.codeOrdinal
                && modifiers.equals(binding.modifiers) && gamepadIndex == binding.gamepadIndex
                && invertedAxis == binding.invertedAxis;
    }

    @Override
    public int hashCode() {
        return Objects.hash(source, codeOrdinal, modifiers, gamepadIndex, invertedAxis);
    }

    @Override
    public String toString() {
        return serialized();
    }
}
