package fr.epistudio.epysia.render.material;

public abstract class Material {

    private final String vertexShaderPath;
    private final String fragmentShaderPath;

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
}
