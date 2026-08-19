package fr.epistudio.epysia.scripting;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

public final class LanguageResource {

    private LanguageResource() {
    }

    public static String read(Class<?> owner, String resourceName) {
        try (InputStream stream = owner.getResourceAsStream(resourceName)) {
            if (stream == null) {
                throw new IllegalStateException("Missing language resource " + resourceName
                        + " beside " + owner.getName());
            }
            return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException unreadable) {
            throw new IllegalStateException("Could not read language resource " + resourceName,
                    unreadable);
        }
    }
}
