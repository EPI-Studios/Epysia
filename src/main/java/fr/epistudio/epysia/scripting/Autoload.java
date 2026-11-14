package fr.epistudio.epysia.scripting;

import fr.epistudio.epysia.EngineServices;
import fr.epistudio.epysia.input.InputState;

public interface Autoload {

    default void onStart(EngineServices services) {
    }

    default void onUpdate(InputState input, float deltaTimeSeconds) {
    }

    default void onStop() {
    }
}
