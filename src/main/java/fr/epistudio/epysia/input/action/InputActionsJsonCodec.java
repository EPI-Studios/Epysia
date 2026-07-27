package fr.epistudio.epysia.input.action;

import fr.epistudio.epysia.scene.serialization.JsonWriter;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public final class InputActionsJsonCodec {

    public void write(JsonWriter writer, List<InputAction> actions) {
        writer.beginArray();
        for (InputAction action : actions) {
            writer.beginObject().key("name").valueString(action.name());
            writeBindings(writer, "positive", action.positive());
            writeBindings(writer, "negative", action.negative());
            writer.endObject();
        }
        writer.endArray();
    }

    public List<InputAction> read(List<?> root) {
        List<InputAction> actions = new ArrayList<>();
        for (Object entry : root) {
            if (entry instanceof Map<?, ?> members && members.get("name") instanceof String name) {
                actions.add(new InputAction(name, readBindings(members.get("positive")),
                        readBindings(members.get("negative"))));
            }
        }
        return actions.isEmpty() ? InputActions.defaultActions() : actions;
    }

    private void writeBindings(JsonWriter writer, String key, List<InputBinding> bindings) {
        writer.key(key).beginArray();
        for (InputBinding binding : bindings) {
            writer.valueString(binding.serialized());
        }
        writer.endArray();
    }

    private List<InputBinding> readBindings(Object raw) {
        List<InputBinding> bindings = new ArrayList<>();
        if (!(raw instanceof List<?> values)) {
            return bindings;
        }
        for (Object value : values) {
            if (value instanceof String text) {
                InputBinding.parse(text).ifPresent(bindings::add);
            }
        }
        return bindings;
    }
}
