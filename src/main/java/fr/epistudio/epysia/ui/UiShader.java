package fr.epistudio.epysia.ui;

public record UiShader(String vertexPath, String fragmentPath, UiShaderKind kind) {

    public static UiShader panel(String vertexPath, String fragmentPath) {
        return new UiShader(vertexPath, fragmentPath, UiShaderKind.PANEL);
    }

    public static UiShader image(String vertexPath, String fragmentPath) {
        return new UiShader(vertexPath, fragmentPath, UiShaderKind.IMAGE);
    }
}
