package fr.epistudio.epysia.lang.rust;

import fr.epistudio.epysia.components.transforms.Transform3D;
import fr.epistudio.epysia.gameobjects.GameObject;
import fr.epistudio.epysia.input.KeyCode;
import fr.epistudio.epysia.input.MutableInputState;
import fr.epistudio.epysia.scripting.foreign.ForeignComponent;
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

class RustGameplaySmokeTest {

    @Test
    void movesAGameObjectFromInput(@TempDir Path projectDirectory) throws IOException {
        Path scriptsDirectory = projectDirectory.resolve("Scripts");
        Files.createDirectories(scriptsDirectory);
        Files.writeString(scriptsDirectory.resolve("Mover.rs"), """
                use epysia_sdk::{epysia_component, Behaviour, Input, Transform, Vec3};

                #[epysia_component(category = "Scripts")]
                pub struct Mover {
                    #[export(default = 5.0, label = "Speed")]
                    pub speed: f32,
                }

                impl Behaviour for Mover {
                    fn on_update(&mut self, transform: &mut Transform, input: &Input, delta_seconds: f32) {
                        let mut direction = Vec3::ZERO;
                        if input.key("D") {
                            direction.x += 1.0;
                        }
                        transform.translate(direction * self.speed * delta_seconds);
                    }
                }
                """, StandardCharsets.UTF_8);

        List<String> messages = new ArrayList<>();
        RustRuntime runtime = new RustRuntime();
        try {
            List<ForeignComponentType> types = runtime.load(scriptsDirectory, messages::add);
            assertTrue(messages.isEmpty(), "unexpected messages: " + messages);
            assertEquals(1, types.size());

            GameObject player = new GameObject("Player");
            Transform3D transform = player.addComponent(new Transform3D());
            ForeignComponentType moverType = types.get(0);
            ForeignInstance instance = moverType.instantiate();
            ForeignComponent mover = player.addComponent(new ForeignComponent(moverType, instance));
            mover.onStart(null);

            MutableInputState input = new MutableInputState();
            input.onKey(KeyCode.D, true);

            mover.onUpdate(input, 0.5f);

            assertEquals(2.5f, transform.position().x, 0.001f);
            assertEquals(0.0f, transform.position().y, 0.001f);
            assertEquals(0.0f, transform.position().z, 0.001f);
        } finally {
            runtime.shutdown();
        }
    }
}
