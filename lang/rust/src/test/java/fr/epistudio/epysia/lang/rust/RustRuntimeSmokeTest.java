package fr.epistudio.epysia.lang.rust;

import fr.epistudio.epysia.scripting.foreign.ForeignComponentType;
import fr.epistudio.epysia.scripting.foreign.ForeignInstance;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RustRuntimeSmokeTest {

    @Test
    void discoversAndRoundTripsAComponent(@TempDir Path projectDirectory) throws IOException {
        Path scriptsDirectory = projectDirectory.resolve("Scripts");
        Files.createDirectories(scriptsDirectory);
        Files.writeString(scriptsDirectory.resolve("Spinner.rs"), """
                use epysia_sdk::{epysia_component, Behaviour};

                #[epysia_component(category = "Scripts")]
                pub struct Spinner {
                    #[export(default = 90.0, label = "Degrees Per Second", min = -720.0, max = 720.0, step = 5.0)]
                    pub degrees_per_second: f32,
                }

                impl Behaviour for Spinner {}
                """, StandardCharsets.UTF_8);

        List<String> messages = new ArrayList<>();
        RustRuntime runtime = new RustRuntime();
        try {
            List<ForeignComponentType> types = runtime.load(scriptsDirectory, messages::add);

            assertTrue(messages.isEmpty(), "unexpected messages: " + messages);
            assertEquals(1, types.size());
            ForeignComponentType spinner = types.get(0);
            assertEquals("Spinner", spinner.name());
            assertEquals("Scripts", spinner.category());
            assertEquals(1, spinner.properties().size());
            assertEquals("degrees_per_second", spinner.properties().get(0).name());
            assertEquals(90.0f, (float) spinner.properties().get(0).defaultValue());

            ForeignInstance instance = spinner.instantiate();
            assertEquals(90.0f, (float) instance.read("degrees_per_second"));
            instance.write("degrees_per_second", 42.5f);
            assertEquals(42.5f, (float) instance.read("degrees_per_second"));
        } finally {
            runtime.shutdown();
        }
    }
}
