package fr.epistudio.epysia.editor.scripts;

import fr.epistudio.epysia.exceptions.EpysiaException;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public final class ScriptScaffold {

    private static final Path SCRIPTS_DIRECTORY = Path.of("examples/java/com/meekdev/scripts");
    private static final String PACKAGE_DECLARATION = "com.meekdev.scripts";

    private ScriptScaffold() {
    }

    public static Path createNewScript() {
        ensureDirectoryExists();
        String className = nextAvailableClassName();
        Path scriptPath = SCRIPTS_DIRECTORY.resolve(className + ".java");
        String body = templateFor(className);
        writeFile(scriptPath, body);
        return scriptPath;
    }

    private static void ensureDirectoryExists() {
        try {
            Files.createDirectories(SCRIPTS_DIRECTORY);
        } catch (IOException exception) {
            throw new EpysiaException("Failed to create scripts directory " + SCRIPTS_DIRECTORY, exception);
        }
    }

    private static String nextAvailableClassName() {
        int counter = 1;
        while (true) {
            String candidate = "MyScript" + counter;
            Path candidatePath = SCRIPTS_DIRECTORY.resolve(candidate + ".java");
            if (!Files.exists(candidatePath)) {
                return candidate;
            }
            counter++;
        }
    }

    private static void writeFile(Path destination, String contents) {
        try {
            Files.writeString(destination, contents);
        } catch (IOException exception) {
            throw new EpysiaException("Failed to write script " + destination, exception);
        }
    }

    private static String templateFor(String className) {
        return """
                package %1$s;

                import fr.epistudio.epysia.EngineServices;
                import fr.epistudio.epysia.components.EpysiaComponent;
                import fr.epistudio.epysia.components.Export;
                import fr.epistudio.epysia.components.transforms.Transform3D;
                import fr.epistudio.epysia.input.InputState;
                import fr.epistudio.epysia.scripting.Behaviour;

                @EpysiaComponent(name = "%2$s", category = "Scripts")
                public final class %2$s extends Behaviour {

                    @Export(label = "Speed", min = 0.0f, max = 100.0f, step = 0.1f)
                    private float speed = 1.0f;

                    private Transform3D transform;

                    @Override
                    public void onStart(EngineServices services) {
                        transform = owner().orElseThrow().getComponent(Transform3D.class).orElse(null);
                    }

                    @Override
                    public void onUpdate(InputState input, float deltaTimeSeconds) {
                        if (transform == null) {
                            return;
                        }
                        // example: spin around the Y axis at `speed` radians/second
                        transform.rotateAxisAngle(0.0f, 1.0f, 0.0f, speed * deltaTimeSeconds);
                    }
                }
                """.formatted(PACKAGE_DECLARATION, className);
    }
}
