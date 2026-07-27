package fr.epistudio.epysia.render.postfx;

public final class FogShaderComposer {

    public static final String EXTENSION = ".fog.glsl";

    private static final String FUNCTIONS_MARKER = "// FOG_FUNCTIONS";
    private static final String ENABLED_DEFINE = "#define FOG_SHADER_ENABLED";

    private FogShaderComposer() {
    }

    public static String compose(String postSource, String fogSource) {
        if (fogSource.isBlank() || !postSource.contains(FUNCTIONS_MARKER)) {
            return postSource;
        }
        return postSource.replace(FUNCTIONS_MARKER, ENABLED_DEFINE + "\n" + stripVersion(fogSource));
    }

    private static String stripVersion(String source) {
        int versionStart = source.indexOf("#version");
        if (versionStart < 0) {
            return source;
        }
        int lineEnd = source.indexOf('\n', versionStart);
        return lineEnd < 0 ? "" : source.substring(lineEnd + 1);
    }
}
