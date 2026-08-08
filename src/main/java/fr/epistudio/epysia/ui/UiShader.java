package fr.epistudio.epysia.ui;

public record UiShader(String vertexPath, String fragmentPath) {
    public static UiShader of(String vertexPath, String fragmentPath) {
        return new UiShader(vertexPath, fragmentPath);
    }
}
