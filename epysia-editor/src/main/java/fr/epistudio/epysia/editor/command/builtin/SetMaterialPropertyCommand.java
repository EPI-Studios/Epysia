package fr.epistudio.epysia.editor.command.builtin;

import fr.epistudio.epysia.editor.command.CommandContext;
import fr.epistudio.epysia.editor.command.EditorCommand;
import fr.epistudio.epysia.exceptions.EpysiaException;
import fr.epistudio.epysia.render.material.LitMaterial;
import fr.epistudio.epysia.render.shader.ShaderUniformValue;
import fr.epistudio.epysia.render.material.Material;
import fr.epistudio.epysia.render.material.MaterialFields;
import org.joml.Vector3f;

import java.lang.reflect.Field;

public final class SetMaterialPropertyCommand implements EditorCommand {

    public enum Target {
        UNIFORM,
        TEXTURE,
        TRANSPARENT,
        DOUBLE_SIDED,
        ANIMATED_SHADOW,
        RECEIVE_SHADOWS,
        SURFACE_SHADER,
        SURFACE_UNIFORM
    }

    private final Material material;
    private final Target target;
    private final String fieldName;
    private final Object beforeValue;
    private final Object afterValue;

    public SetMaterialPropertyCommand(Material material, Target target, String fieldName,
                                      Object beforeValue, Object afterValue) {
        this.material = material;
        this.target = target;
        this.fieldName = fieldName;
        this.beforeValue = snapshot(beforeValue);
        this.afterValue = snapshot(afterValue);
    }

    @Override
    public void apply(CommandContext context) {
        switch (target) {
            case UNIFORM -> applyUniform();
            case TEXTURE -> applyTexture(context);
            case TRANSPARENT -> material.setTransparent((Boolean) afterValue);
            case DOUBLE_SIDED -> material.setDoubleSided((Boolean) afterValue);
            case ANIMATED_SHADOW -> applyAnimatedShadow();
            case RECEIVE_SHADOWS -> applyReceiveShadows();
            case SURFACE_SHADER -> applySurfaceShader();
            case SURFACE_UNIFORM -> applySurfaceUniform();
        }
    }

    private void applyAnimatedShadow() {
        if (material instanceof LitMaterial lit) {
            lit.setAnimatedShadow((Boolean) afterValue);
        }
    }

    private void applyReceiveShadows() {
        if (material instanceof LitMaterial lit) {
            lit.setReceiveShadows((Boolean) afterValue);
        }
    }

    private void applySurfaceShader() {
        if (material instanceof LitMaterial lit) {
            lit.setSurfaceShaderPath((String) afterValue);
        }
    }

    private void applySurfaceUniform() {
        if (afterValue instanceof ShaderUniformValue value) {
            material.surfaceUniforms().set(fieldName, value);
        }
    }

    private void applyUniform() {
        Field field = MaterialFields.uniformField(material.getClass(), fieldName)
                .orElseThrow(() -> new EpysiaException("Unknown material uniform: " + fieldName));
        Object current = MaterialFields.read(material, field);
        if (current instanceof Vector3f vector && afterValue instanceof Vector3f source) {
            vector.set(source);
        } else if (afterValue instanceof Number number) {
            MaterialFields.write(material, field, number.floatValue());
        }
    }

    private void applyTexture(CommandContext context) {
        Field field = MaterialFields.textureField(material.getClass(), fieldName)
                .orElseThrow(() -> new EpysiaException("Unknown material texture: " + fieldName));
        material.setTexturePath(fieldName, (String) afterValue);
        MaterialFields.write(material, field, null);
        MaterialFields.resolveTextures(material, context.services().assets());
    }

    @Override
    public EditorCommand invert(CommandContext context) {
        return new SetMaterialPropertyCommand(material, target, fieldName, afterValue, beforeValue);
    }

    @Override
    public String coalesceKey() {
        return "material:" + System.identityHashCode(material) + "." + target + "." + fieldName;
    }

    @Override
    public String label() {
        return "Set Material " + (fieldName.isEmpty() ? target.name().toLowerCase(java.util.Locale.ROOT) : fieldName);
    }

    private static Object snapshot(Object value) {
        return value instanceof Vector3f vector ? new Vector3f(vector) : value;
    }
}
