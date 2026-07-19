package fr.epistudio.epysia.editor;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

public record BuildInfo(String version, String repository) {

    private static final String RESOURCE = "/editor-build.properties";
    private static final String FALLBACK_VERSION = "0.0.0";
    private static final String FALLBACK_REPOSITORY = "EPI-Studios/Epysia";

    public static BuildInfo load() {
        Properties properties = new Properties();
        try (InputStream stream = BuildInfo.class.getResourceAsStream(RESOURCE)) {
            if (stream != null) {
                properties.load(stream);
            }
        } catch (IOException error) {
            return new BuildInfo(FALLBACK_VERSION, FALLBACK_REPOSITORY);
        }
        return new BuildInfo(
                properties.getProperty("version", FALLBACK_VERSION),
                properties.getProperty("repository", FALLBACK_REPOSITORY));
    }
}
