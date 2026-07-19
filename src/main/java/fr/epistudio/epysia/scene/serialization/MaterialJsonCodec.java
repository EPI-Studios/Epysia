package fr.epistudio.epysia.scene.serialization;

import fr.epistudio.epysia.render.material.Material;
import fr.epistudio.epysia.render.material.MaterialFields;
import org.joml.Vector2f;
import org.joml.Vector3f;
import org.joml.Vector4f;

import java.lang.reflect.Field;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public final class MaterialJsonCodec {

    public void writeMaterialArray(JsonWriter writer, List<Material> materials) {
        writer.beginArray();
        for (Material material : materials) {
            writeMaterial(writer, material);
        }
        writer.endArray();
    }

    private void writeMaterial(JsonWriter writer, Material material) {
        writer.beginObject();
        writer.key("class").valueString(material.getClass().getName());
        writer.key("transparent").valueBoolean(material.transparent());
        writer.key("doubleSided").valueBoolean(material.doubleSided());
        writer.key("uniforms").beginObject();
        for (Field field : MaterialFields.uniformFields(material.getClass())) {
            writeUniform(writer, material, field);
        }
        writer.endObject();
        writer.key("textures").beginObject();
        for (Map.Entry<String, String> entry : material.texturePaths().entrySet()) {
            writer.key(entry.getKey()).valueString(entry.getValue());
        }
        writer.endObject();
        writer.endObject();
    }

    private void writeUniform(JsonWriter writer, Material material, Field field) {
        Object value = MaterialFields.read(material, field);
        switch (value) {
            case Float number -> writer.key(field.getName()).valueNumber(number);
            case Integer number -> writer.key(field.getName()).valueNumber(number);
            case Vector2f vector -> writeComponents(writer, field.getName(), vector.x, vector.y);
            case Vector3f vector -> writeComponents(writer, field.getName(), vector.x, vector.y, vector.z);
            case Vector4f vector -> writeComponents(writer, field.getName(), vector.x, vector.y, vector.z, vector.w);
            case null, default -> {
            }
        }
    }

    private void writeComponents(JsonWriter writer, String name, float... components) {
        writer.key(name).beginArray();
        for (float component : components) {
            writer.valueNumber(component);
        }
        writer.endArray();
    }

    @SuppressWarnings("unchecked")
    public Optional<Material> readMaterial(Map<String, Object> materialJson) {
        String className = materialJson.get("class") instanceof String name ? name : "";
        Optional<Material> instantiated = instantiate(className);
        instantiated.ifPresent(material -> {
            if (materialJson.get("transparent") instanceof Boolean transparent) {
                material.setTransparent(transparent);
            }
            if (materialJson.get("doubleSided") instanceof Boolean doubleSided) {
                material.setDoubleSided(doubleSided);
            }
            applyUniforms(material, (Map<String, Object>) materialJson.getOrDefault("uniforms", Map.of()));
            applyTexturePaths(material, (Map<String, Object>) materialJson.getOrDefault("textures", Map.of()));
        });
        return instantiated;
    }

    private Optional<Material> instantiate(String className) {
        try {
            Class<?> loaded = Class.forName(className, true, Thread.currentThread().getContextClassLoader());
            if (!Material.class.isAssignableFrom(loaded)) {
                return Optional.empty();
            }
            return Optional.of((Material) loaded.getDeclaredConstructor().newInstance());
        } catch (ReflectiveOperationException | RuntimeException error) {
            return Optional.empty();
        }
    }

    private void applyTexturePaths(Material material, Map<String, Object> textures) {
        for (Map.Entry<String, Object> entry : textures.entrySet()) {
            if (entry.getValue() instanceof String path) {
                material.setTexturePath(entry.getKey(), path);
            }
        }
    }

    private void applyUniforms(Material material, Map<String, Object> uniforms) {
        for (Field field : MaterialFields.uniformFields(material.getClass())) {
            Object value = uniforms.get(field.getName());
            if (value != null) {
                applyUniform(material, field, value);
            }
        }
    }

    @SuppressWarnings("unchecked")
    private void applyUniform(Material material, Field field, Object value) {
        Class<?> type = field.getType();
        if ((type == float.class || type == Float.class) && value instanceof Number number) {
            MaterialFields.write(material, field, number.floatValue());
        } else if ((type == int.class || type == Integer.class) && value instanceof Number number) {
            MaterialFields.write(material, field, number.intValue());
        } else if (value instanceof List<?> components) {
            applyVector(material, field, (List<Object>) components);
        }
    }

    private void applyVector(Material material, Field field, List<Object> components) {
        Object current = MaterialFields.read(material, field);
        switch (current) {
            case Vector2f vector when components.size() >= 2 ->
                    vector.set(asFloat(components.get(0)), asFloat(components.get(1)));
            case Vector3f vector when components.size() >= 3 ->
                    vector.set(asFloat(components.get(0)), asFloat(components.get(1)), asFloat(components.get(2)));
            case Vector4f vector when components.size() >= 4 ->
                    vector.set(asFloat(components.get(0)), asFloat(components.get(1)),
                            asFloat(components.get(2)), asFloat(components.get(3)));
            case null, default -> {
            }
        }
    }

    private static float asFloat(Object number) {
        return number instanceof Number numericValue ? numericValue.floatValue() : 0.0f;
    }
}
