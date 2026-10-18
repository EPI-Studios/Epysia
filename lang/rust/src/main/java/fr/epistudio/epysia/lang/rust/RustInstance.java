package fr.epistudio.epysia.lang.rust;

import fr.epistudio.epysia.EngineServices;
import fr.epistudio.epysia.components.transforms.Transform3D;
import fr.epistudio.epysia.gameobjects.GameObject;
import fr.epistudio.epysia.input.InputState;
import fr.epistudio.epysia.scene.serialization.JsonWriter;
import fr.epistudio.epysia.scripting.foreign.ForeignInstance;
import fr.epistudio.epysia.scripting.foreign.ForeignPropertyDefinition;
import org.joml.Vector3f;

import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Supplier;

final class RustInstance implements ForeignInstance {

    private final RustModule module;
    private final int handle;
    private final Map<String, ForeignPropertyDefinition> definitions;
    private final Consumer<String> failures;
    private Transform3D transform;

    RustInstance(RustModule module, int handle, Map<String, ForeignPropertyDefinition> definitions,
                 Consumer<String> failures) {
        this.module = module;
        this.handle = handle;
        this.definitions = definitions;
        this.failures = failures;
    }

    @Override
    public Object read(String property) {
        ForeignPropertyDefinition definition = definitions.get(property);
        if (definition == null) {
            return null;
        }
        return guard("read " + property, () -> RustValues.toJava(module.read(handle, property), definition.type()));
    }

    @Override
    public void write(String property, Object value) {
        ForeignPropertyDefinition definition = definitions.get(property);
        if (definition == null) {
            return;
        }
        guard("write " + property, () -> {
            module.write(handle, property, RustValues.encode(value, definition.type()));
            return null;
        });
    }

    @Override
    public void onAttached(GameObject owner) {
        transform = owner.transform3DOrNull();
    }

    @Override
    public void onStart(EngineServices services) {
        guard("on_start", () -> {
            applyTransform(module.onStart(handle, transformJson()));
            return null;
        });
    }

    @Override
    public void onUpdate(InputState input, float deltaTimeSeconds) {
        guard("on_update", () -> {
            applyTransform(module.onUpdate(handle, deltaTimeSeconds, transformJson(), RustInputSnapshot.toJson(input)));
            return null;
        });
    }

    @Override
    public void onFixedUpdate(float fixedStepSeconds) {
        guard("on_fixed_update", () -> {
            applyTransform(module.onFixedUpdate(handle, fixedStepSeconds, transformJson()));
            return null;
        });
    }

    @Override
    public void onDestroy() {
        guard("on_destroy", () -> {
            module.onDestroy(handle);
            return null;
        });
    }

    private String transformJson() {
        if (transform == null) {
            return "{}";
        }
        JsonWriter writer = new JsonWriter();
        writer.beginObject();
        writer.key("position");
        writeVector(writer, transform.position());
        writer.key("scale");
        writeVector(writer, transform.scale());
        writer.key("worldPosition");
        writeVector(writer, transform.worldPosition(new Vector3f()));
        writer.endObject();
        return writer.toString();
    }

    private void applyTransform(Object decoded) {
        if (transform == null || !(decoded instanceof Map<?, ?> map)) {
            return;
        }
        Vector3f position = vectorOf(map, "position");
        if (position != null) {
            transform.setPosition(position.x, position.y, position.z);
        }
        Vector3f scale = vectorOf(map, "scale");
        if (scale != null) {
            transform.setScale(scale.x, scale.y, scale.z);
        }
    }

    private static void writeVector(JsonWriter writer, Vector3f vector) {
        writer.beginArray();
        writer.valueNumber(vector.x);
        writer.valueNumber(vector.y);
        writer.valueNumber(vector.z);
        writer.endArray();
    }

    private static Vector3f vectorOf(Map<?, ?> map, String key) {
        if (!(map.get(key) instanceof List<?> array) || array.size() != 3) {
            return null;
        }
        return new Vector3f(numberAt(array, 0), numberAt(array, 1), numberAt(array, 2));
    }

    private static float numberAt(List<?> array, int index) {
        return array.get(index) instanceof Number number ? number.floatValue() : 0.0f;
    }

    private <T> T guard(String what, Supplier<T> action) {
        try {
            return action.get();
        } catch (RuntimeException failure) {
            failures.accept("Rust " + what + " failed: " + failure.getMessage());
            return null;
        }
    }
}
