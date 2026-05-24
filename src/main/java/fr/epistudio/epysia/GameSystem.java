package fr.epistudio.epysia;

import fr.epistudio.epysia.input.InputState;
import fr.epistudio.epysia.scene.Scene;

public interface GameSystem {

    default void initialize(EngineServices services) {
    }

    void update(Scene scene, InputState input, float deltaTimeSeconds);

    default void shutdown() {
    }
}
