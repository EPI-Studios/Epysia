package fr.epistudio.epysia.render.material;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

public abstract class Material {

    private final String vertexShaderPath;
    private final String fragmentShaderPath;
    private final Map<String, String> texturePaths = new LinkedHashMap<>();
    private boolean transparent;
    private boolean doubleSided;

    protected Material(String vertexShaderPath, String fragmentShaderPath) {
        this.vertexShaderPath = vertexShaderPath;
        this.fragmentShaderPath = fragmentShaderPath;
    }

    public final String vertexShaderPath() {
        return vertexShaderPath;
    }

    public final String fragmentShaderPath() {
        return fragmentShaderPath;
    }

    public final boolean transparent() {
        return transparent;
    }

    public final Material setTransparent(boolean value) {
        this.transparent = value;
        return this;
    }

    public final boolean doubleSided() {
        return doubleSided;
    }

    public final Material setDoubleSided(boolean value) {
        this.doubleSided = value;
        return this;
    }

    public final Optional<String> texturePath(String fieldName) {
        String path = texturePaths.get(fieldName);
        return path == null || path.isEmpty() ? Optional.empty() : Optional.of(path);
    }

    public final Map<String, String> texturePaths() {
        return Collections.unmodifiableMap(texturePaths);
    }

    public final Material setTexturePath(String fieldName, String path) {
        if (path == null || path.isEmpty()) {
            texturePaths.remove(fieldName);
        } else {
            texturePaths.put(fieldName, path);
        }
        return this;
    }
}
