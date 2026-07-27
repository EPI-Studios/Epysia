package fr.epistudio.epysia.input.action;

import fr.epistudio.epysia.input.InputState;
import fr.epistudio.epysia.input.KeyCode;
import fr.epistudio.epysia.input.MouseButton;

import java.util.Locale;
import java.util.Optional;

public record InputBinding(Source source, String code) {

    public enum Source {
        KEY,
        MOUSE
    }

    public static InputBinding key(KeyCode key) {
        return new InputBinding(Source.KEY, key.name());
    }

    public static InputBinding mouse(MouseButton button) {
        return new InputBinding(Source.MOUSE, button.name());
    }

    public static Optional<InputBinding> parse(String text) {
        if (text == null || !text.contains(":")) {
            return Optional.empty();
        }
        String[] parts = text.split(":", 2);
        return switch (parts[0].toLowerCase(Locale.ROOT)) {
            case "key" -> keyNamed(parts[1]);
            case "mouse" -> mouseNamed(parts[1]);
            default -> Optional.empty();
        };
    }

    private static Optional<InputBinding> keyNamed(String name) {
        for (KeyCode key : KeyCode.values()) {
            if (key.name().equalsIgnoreCase(name)) {
                return Optional.of(key(key));
            }
        }
        return Optional.empty();
    }

    private static Optional<InputBinding> mouseNamed(String name) {
        for (MouseButton button : MouseButton.values()) {
            if (button.name().equalsIgnoreCase(name)) {
                return Optional.of(mouse(button));
            }
        }
        return Optional.empty();
    }

    public String serialized() {
        return source.name().toLowerCase(Locale.ROOT) + ":" + code;
    }

    public boolean isDown(InputState input) {
        return source == Source.KEY
                ? input.isKeyDown(KeyCode.valueOf(code))
                : input.isMouseButtonDown(MouseButton.valueOf(code));
    }

    public boolean wasPressed(InputState input) {
        return source == Source.KEY
                ? input.wasKeyPressed(KeyCode.valueOf(code))
                : input.wasMouseButtonPressed(MouseButton.valueOf(code));
    }

    public boolean wasReleased(InputState input) {
        return source == Source.KEY
                ? input.wasKeyReleased(KeyCode.valueOf(code))
                : input.wasMouseButtonReleased(MouseButton.valueOf(code));
    }
}
