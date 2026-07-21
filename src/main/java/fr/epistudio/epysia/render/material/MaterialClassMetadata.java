package fr.epistudio.epysia.render.material;

import fr.epistudio.epysia.exceptions.EpysiaException;
import fr.epistudio.epysia.render.backend.TextureHandle;

import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
import java.lang.reflect.Field;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class MaterialClassMetadata {

    private static final Pattern SAMPLER_BINDING_PATTERN = Pattern.compile(
            "layout\\s*\\(\\s*binding\\s*=\\s*(\\d+)\\s*\\)\\s*uniform\\s+sampler(?:2D|2DShadow|Cube)\\s+(\\w+)\\s*;"
    );

    private final List<UniformFieldDescriptor> uniformFields;
    private final List<TextureFieldDescriptor> textureFields;
    private final int uniformBufferSize;

    private MaterialClassMetadata(List<UniformFieldDescriptor> uniformFields, List<TextureFieldDescriptor> textureFields, int uniformBufferSize) {
        this.uniformFields = List.copyOf(uniformFields);
        this.textureFields = List.copyOf(textureFields);
        this.uniformBufferSize = uniformBufferSize;
    }

    public List<UniformFieldDescriptor> uniformFields() {
        return uniformFields;
    }

    public List<TextureFieldDescriptor> textureFields() {
        return textureFields;
    }

    public int uniformBufferSize() {
        return uniformBufferSize;
    }

    public boolean hasUniformBuffer() {
        return uniformBufferSize > 0 && !uniformFields.isEmpty();
    }

    public static MaterialClassMetadata reflect(Class<? extends Material> materialClass, String fragmentShaderSource) {
        Map<String, Integer> samplerBindings = parseSamplerBindings(fragmentShaderSource);
        List<UniformFieldDescriptor> uniformFields = new ArrayList<>();
        List<TextureFieldDescriptor> textureFields = new ArrayList<>();
        int currentOffset = 0;
        for (Field field : collectAnnotatedFields(materialClass)) {
            field.setAccessible(true);
            if (field.isAnnotationPresent(Uniform.class)) {
                currentOffset = appendUniformField(field, uniformFields, currentOffset);
            } else if (field.isAnnotationPresent(Texture.class)) {
                appendTextureField(field, textureFields, samplerBindings);
            }
        }
        int paddedSize = uniformFields.isEmpty() ? 0 : alignTo(Math.max(currentOffset, 16), 16);
        return new MaterialClassMetadata(uniformFields, textureFields, paddedSize);
    }

    private static int appendUniformField(Field field, List<UniformFieldDescriptor> fields, int currentOffset) {
        UniformType type = UniformType.forField(field.getType());
        int alignedOffset = alignTo(currentOffset, type.byteAlignment());
        fields.add(new UniformFieldDescriptor(field, varHandleFor(field), type, alignedOffset));
        return alignedOffset + type.byteSize();
    }

    private static void appendTextureField(Field field, List<TextureFieldDescriptor> fields, Map<String, Integer> samplerBindings) {
        Integer slot = samplerBindings.get(field.getName());
        if (slot == null) {
            throw new EpysiaException("@Texture field '" + field.getName() + "' has no matching sampler declaration in fragment shader.");
        }
        fields.add(new TextureFieldDescriptor(field, varHandleFor(field), slot));
    }

    private static VarHandle varHandleFor(Field field) {
        try {
            MethodHandles.Lookup lookup = MethodHandles.privateLookupIn(field.getDeclaringClass(), MethodHandles.lookup());
            return lookup.unreflectVarHandle(field);
        } catch (IllegalAccessException exception) {
            throw new EpysiaException("Failed to access material field '" + field.getName() + "': " + exception.getMessage());
        }
    }

    private static List<Field> collectAnnotatedFields(Class<? extends Material> materialClass) {
        List<Field> ordered = new ArrayList<>();
        List<Class<?>> chain = new ArrayList<>();
        Class<?> current = materialClass;
        while (current != null && current != Material.class && current != Object.class) {
            chain.add(0, current);
            current = current.getSuperclass();
        }
        for (Class<?> declaringClass : chain) {
            for (Field field : declaringClass.getDeclaredFields()) {
                if (field.isAnnotationPresent(Uniform.class) || field.isAnnotationPresent(Texture.class)) {
                    ordered.add(field);
                }
            }
        }
        return ordered;
    }

    public void writeUniformBuffer(Material instance, ByteBuffer destination) {
        for (UniformFieldDescriptor descriptor : uniformFields) {
            Object value = descriptor.accessor().get(instance);
            if (value != null) {
                descriptor.type().write(destination, descriptor.byteOffset(), value);
            }
        }
    }

    public TextureHandle readTexture(Material instance, TextureFieldDescriptor descriptor) {
        return (TextureHandle) descriptor.accessor().get(instance);
    }

    private static int alignTo(int offset, int alignment) {
        return ((offset + alignment - 1) / alignment) * alignment;
    }

    public static Map<String, Integer> samplerBindings(String shaderSource) {
        return parseSamplerBindings(shaderSource);
    }

    private static Map<String, Integer> parseSamplerBindings(String shaderSource) {
        Map<String, Integer> result = new HashMap<>();
        Matcher matcher = SAMPLER_BINDING_PATTERN.matcher(shaderSource);
        while (matcher.find()) {
            result.put(matcher.group(2), Integer.parseInt(matcher.group(1)));
        }
        return result;
    }

    public record UniformFieldDescriptor(Field reflectField, VarHandle accessor, UniformType type, int byteOffset) {
    }

    public record TextureFieldDescriptor(Field reflectField, VarHandle accessor, int slotIndex) {
    }
}
