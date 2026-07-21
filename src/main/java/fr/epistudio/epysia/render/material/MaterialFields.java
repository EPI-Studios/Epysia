package fr.epistudio.epysia.render.material;

import fr.epistudio.epysia.assets.AssetRegistry;
import fr.epistudio.epysia.assets.loaders.TextureAssetLoader;
import fr.epistudio.epysia.exceptions.EpysiaException;
import fr.epistudio.epysia.render.backend.TextureHandle;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public final class MaterialFields {

    private MaterialFields() {
    }

    public static List<Field> uniformFields(Class<? extends Material> materialClass) {
        return annotatedFields(materialClass, Uniform.class);
    }

    public static List<Field> textureFields(Class<? extends Material> materialClass) {
        return annotatedFields(materialClass, Texture.class);
    }

    public static Optional<Field> uniformField(Class<? extends Material> materialClass, String name) {
        return byName(uniformFields(materialClass), name);
    }

    public static Optional<Field> textureField(Class<? extends Material> materialClass, String name) {
        return byName(textureFields(materialClass), name);
    }

    private static Optional<Field> byName(List<Field> fields, String name) {
        for (Field field : fields) {
            if (field.getName().equals(name)) {
                return Optional.of(field);
            }
        }
        return Optional.empty();
    }

    public static Object read(Material material, Field field) {
        try {
            return field.get(material);
        } catch (IllegalAccessException error) {
            throw new EpysiaException("Cannot read material field " + field.getName() + ": " + error.getMessage());
        }
    }

    public static void write(Material material, Field field, Object value) {
        try {
            field.set(material, value);
        } catch (IllegalAccessException error) {
            throw new EpysiaException("Cannot write material field " + field.getName() + ": " + error.getMessage());
        }
    }

    public static void resolveTextures(Material material, AssetRegistry assets) {
        for (Field field : textureFields(material.getClass())) {
            Optional<String> path = material.texturePath(field.getName());
            if (path.isEmpty() || read(material, field) != null) {
                continue;
            }
            assets.resolve(TextureHandle.class, resolvePath(field, path.get()))
                    .ifPresent(handle -> write(material, field, handle));
        }
    }

    private static String resolvePath(Field field, String path) {
        Texture annotation = field.getAnnotation(Texture.class);
        if (annotation != null && annotation.srgb() && !path.startsWith(TextureAssetLoader.SRGB_PREFIX)) {
            return TextureAssetLoader.SRGB_PREFIX + path;
        }
        return path;
    }

    private static List<Field> annotatedFields(Class<? extends Material> materialClass,
                                               Class<? extends java.lang.annotation.Annotation> annotation) {
        List<Field> ordered = new ArrayList<>();
        for (Class<?> declaringClass : classChain(materialClass)) {
            for (Field field : declaringClass.getDeclaredFields()) {
                if (field.isAnnotationPresent(annotation)) {
                    field.setAccessible(true);
                    ordered.add(field);
                }
            }
        }
        return ordered;
    }

    private static List<Class<?>> classChain(Class<? extends Material> materialClass) {
        List<Class<?>> chain = new ArrayList<>();
        Class<?> current = materialClass;
        while (current != null && current != Material.class && current != Object.class) {
            chain.add(0, current);
            current = current.getSuperclass();
        }
        return chain;
    }
}
