package fr.epistudio.epysia.render.shader;

import java.util.Optional;

public final class ShaderUniformDefaults {

    private ShaderUniformDefaults() {
    }

    public static Optional<ShaderUniformValue> of(ShaderUniformDeclaration declaration) {
        if (!declaration.hasDefault() || declaration.isArray()) {
            return Optional.empty();
        }
        float[] components = parseComponents(declaration.defaultText());
        return switch (declaration.kind()) {
            case FLOAT -> Optional.of(new ShaderUniformValue.FloatValue(component(components, 0)));
            case INT -> Optional.of(new ShaderUniformValue.IntValue((int) component(components, 0)));
            case BOOL -> Optional.of(new ShaderUniformValue.BoolValue(component(components, 0) != 0.0f));
            case VECTOR2 -> Optional.of(new ShaderUniformValue.Vector2Value(
                    component(components, 0), component(components, 1)));
            case VECTOR3 -> Optional.of(new ShaderUniformValue.Vector3Value(
                    component(components, 0), component(components, 1), component(components, 2)));
            case VECTOR4 -> Optional.of(new ShaderUniformValue.Vector4Value(
                    component(components, 0), component(components, 1),
                    component(components, 2), component(components, 3)));
            case MATRIX4, SAMPLER2D -> Optional.empty();
        };
    }

    private static float[] parseComponents(String text) {
        String[] parts = text.split(",");
        float[] components = new float[parts.length];
        for (int index = 0; index < parts.length; index++) {
            components[index] = parseFloat(parts[index].strip());
        }
        return components;
    }

    private static float parseFloat(String text) {
        try {
            return Float.parseFloat(text);
        } catch (NumberFormatException invalid) {
            return 0.0f;
        }
    }

    private static float component(float[] components, int index) {
        return index < components.length ? components[index] : 0.0f;
    }
}
