package fr.epistudio.epysia.scene.serialization;

import fr.epistudio.epysia.render.material.LitMaterial;
import fr.epistudio.epysia.render.material.Material;
import fr.epistudio.epysia.render.material.MaterialFields;
import fr.epistudio.epysia.render.shader.ShaderUniformValue;
import org.joml.Vector2f;
import org.joml.Vector3f;
import org.joml.Vector4f;

import java.lang.reflect.Field;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public final class MaterialJsonCodec {

    public String writeSingle(Material material) {
        JsonWriter writer = new JsonWriter();
        writeMaterial(writer, material);
        return writer.toString();
    }

    public Optional<Material> readSingle(String json) {
        return readMaterial(new JsonReader(json).readRootObject());
    }

    public void writeMaterialArray(JsonWriter writer, List<Material> materials) {
        writer.beginArray();
        for (Material material : materials) {
            if (material.assetPath().isEmpty()) {
                writeMaterial(writer, material);
            } else {
                writeMaterialReference(writer, material);
            }
        }
        writer.endArray();
    }

    private void writeMaterialReference(JsonWriter writer, Material material) {
        writer.beginObject();
        writer.key("asset").valueString(material.assetPath());
        writer.endObject();
    }

    private void writeMaterial(JsonWriter writer, Material material) {
        writer.beginObject();
        writer.key("class").valueString(material.getClass().getName());
        writer.key("vertexShader").valueString(material.vertexShaderPath());
        writer.key("fragmentShader").valueString(material.fragmentShaderPath());
        if (material instanceof LitMaterial lit && !lit.surfaceShaderPath().isEmpty()) {
            writer.key("surfaceShader").valueString(lit.surfaceShaderPath());
        }
        if (material instanceof LitMaterial lit && !lit.animatedShadow()) {
            writer.key("animatedShadow").valueBoolean(false);
        }
        if (material instanceof LitMaterial lit && !lit.receiveShadows()) {
            writer.key("receiveShadows").valueBoolean(false);
        }
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
        writeSurfaceUniforms(writer, material);
        writer.endObject();
    }

    private void writeSurfaceUniforms(JsonWriter writer, Material material) {
        if (material.surfaceUniforms().isEmpty()) {
            return;
        }
        writer.key("surfaceUniforms").beginObject();
        for (Map.Entry<String, ShaderUniformValue> entry : material.surfaceUniforms().all().entrySet()) {
            writeSurfaceUniform(writer, entry.getKey(), entry.getValue());
        }
        writer.endObject();
    }

    private void writeSurfaceUniform(JsonWriter writer, String name, ShaderUniformValue value) {
        writer.key(name).beginObject().key("type").valueString(SurfaceUniformTypeTags.of(value));
        switch (value) {
            case ShaderUniformValue.FloatValue number -> writer.key("value").valueNumber(number.value());
            case ShaderUniformValue.IntValue number -> writer.key("value").valueNumber(number.value());
            case ShaderUniformValue.BoolValue flag -> writer.key("value").valueBoolean(flag.value());
            case ShaderUniformValue.TextureValue texture -> writer.key("value").valueString(texture.path());
            case ShaderUniformValue.Vector2Value vector -> writeComponents(writer, "value", vector.x(), vector.y());
            case ShaderUniformValue.Vector3Value vector ->
                    writeComponents(writer, "value", vector.x(), vector.y(), vector.z());
            case ShaderUniformValue.Vector4Value vector ->
                    writeComponents(writer, "value", vector.x(), vector.y(), vector.z(), vector.w());
            case ShaderUniformValue.Matrix4Value matrix ->
                    writeComponents(writer, "value", matrix.columnMajorElements());
            case ShaderUniformValue.FloatArrayValue floats -> writeComponents(writer, "value", floats.elements());
            case ShaderUniformValue.Vector4ArrayValue vectors ->
                    writeComponents(writer, "value", vectors.flattenedElements());
        }
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
        if (materialJson.get("asset") instanceof String referencedPath && !referencedPath.isEmpty()) {
            LitMaterial placeholder = new LitMaterial();
            placeholder.setAssetPath(referencedPath);
            return Optional.of(placeholder);
        }
        String className = materialJson.get("class") instanceof String name ? name : "";
        Optional<Material> instantiated = instantiate(className,
                stringValue(materialJson, "vertexShader"), stringValue(materialJson, "fragmentShader"));
        instantiated.ifPresent(material -> {
            if (material instanceof LitMaterial lit) {
                lit.setSurfaceShaderPath(stringValue(materialJson, "surfaceShader"));
                if (materialJson.get("animatedShadow") instanceof Boolean animatedShadow) {
                    lit.setAnimatedShadow(animatedShadow);
                }
                if (materialJson.get("receiveShadows") instanceof Boolean receiveShadows) {
                    lit.setReceiveShadows(receiveShadows);
                }
            }
            if (materialJson.get("transparent") instanceof Boolean transparent) {
                material.setTransparent(transparent);
            }
            if (materialJson.get("doubleSided") instanceof Boolean doubleSided) {
                material.setDoubleSided(doubleSided);
            }
            applyUniforms(material, (Map<String, Object>) materialJson.getOrDefault("uniforms", Map.of()));
            applyTexturePaths(material, (Map<String, Object>) materialJson.getOrDefault("textures", Map.of()));
            applySurfaceUniforms(material, (Map<String, Object>) materialJson.getOrDefault("surfaceUniforms", Map.of()));
        });
        return instantiated;
    }

    private Optional<Material> instantiate(String className, String vertexShaderPath, String fragmentShaderPath) {
        try {
            Class<?> loaded = Class.forName(className, true, Thread.currentThread().getContextClassLoader());
            if (!Material.class.isAssignableFrom(loaded)) {
                return Optional.empty();
            }
            return construct(loaded, vertexShaderPath, fragmentShaderPath);
        } catch (ReflectiveOperationException | RuntimeException error) {
            return Optional.empty();
        }
    }

    private Optional<Material> construct(Class<?> loaded, String vertexShaderPath, String fragmentShaderPath)
            throws ReflectiveOperationException {
        try {
            return Optional.of((Material) loaded.getDeclaredConstructor().newInstance());
        } catch (NoSuchMethodException missingDefaultConstructor) {
            if (vertexShaderPath.isEmpty() || fragmentShaderPath.isEmpty()) {
                return Optional.empty();
            }
            return Optional.of((Material) loaded.getDeclaredConstructor(String.class, String.class)
                    .newInstance(vertexShaderPath, fragmentShaderPath));
        }
    }

    private static String stringValue(Map<String, Object> json, String key) {
        return json.get(key) instanceof String value ? value : "";
    }

    private void applyTexturePaths(Material material, Map<String, Object> textures) {
        for (Map.Entry<String, Object> entry : textures.entrySet()) {
            if (entry.getValue() instanceof String path) {
                material.setTexturePath(entry.getKey(), path);
            }
        }
    }

    @SuppressWarnings("unchecked")
    private void applySurfaceUniforms(Material material, Map<String, Object> surfaceUniforms) {
        for (Map.Entry<String, Object> entry : surfaceUniforms.entrySet()) {
            if (entry.getValue() instanceof Map<?, ?> encoded) {
                applySurfaceUniform(material, entry.getKey(), (Map<String, Object>) encoded);
            }
        }
    }

    private void applySurfaceUniform(Material material, String name, Map<String, Object> encoded) {
        String tag = encoded.get("type") instanceof String type ? type : "";
        SurfaceUniformTypeTags.parse(tag, encoded.get("value"))
                .ifPresent(value -> material.surfaceUniforms().set(name, value));
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
