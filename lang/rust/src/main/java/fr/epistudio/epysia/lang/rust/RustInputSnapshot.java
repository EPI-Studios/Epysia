package fr.epistudio.epysia.lang.rust;

import fr.epistudio.epysia.input.InputState;
import fr.epistudio.epysia.input.KeyCode;
import fr.epistudio.epysia.input.MouseButton;
import fr.epistudio.epysia.scene.serialization.JsonWriter;

import java.util.function.Predicate;

final class RustInputSnapshot {

    private RustInputSnapshot() {
    }

    static String toJson(InputState input) {
        JsonWriter writer = new JsonWriter();
        writer.beginObject();
        writer.key("keysDown");
        writeKeys(writer, input::isKeyDown);
        writer.key("keysPressed");
        writeKeys(writer, input::wasKeyPressed);
        writer.key("keysReleased");
        writeKeys(writer, input::wasKeyReleased);
        writer.key("mouseDown");
        writeButtons(writer, input::isMouseButtonDown);
        writer.key("mousePressed");
        writeButtons(writer, input::wasMouseButtonPressed);
        writer.key("cursor");
        writePair(writer, input.cursorX(), input.cursorY());
        writer.key("mouseDelta");
        writePair(writer, input.mouseDeltaX(), input.mouseDeltaY());
        writer.key("scroll");
        writer.valueNumber(input.scrollDeltaY());
        writer.endObject();
        return writer.toString();
    }

    private static void writeKeys(JsonWriter writer, Predicate<KeyCode> active) {
        writer.beginArray();
        for (KeyCode key : KeyCode.values()) {
            if (active.test(key)) {
                writer.valueString(key.name());
            }
        }
        writer.endArray();
    }

    private static void writeButtons(JsonWriter writer, Predicate<MouseButton> active) {
        writer.beginArray();
        for (MouseButton button : MouseButton.values()) {
            if (active.test(button)) {
                writer.valueString(button.name());
            }
        }
        writer.endArray();
    }

    private static void writePair(JsonWriter writer, float x, float y) {
        writer.beginArray();
        writer.valueNumber(x);
        writer.valueNumber(y);
        writer.endArray();
    }
}
