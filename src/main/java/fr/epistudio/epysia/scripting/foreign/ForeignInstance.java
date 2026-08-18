package fr.epistudio.epysia.scripting.foreign;

import fr.epistudio.epysia.EngineServices;
import fr.epistudio.epysia.gameobjects.GameObject;
import fr.epistudio.epysia.input.InputState;

public interface ForeignInstance {

    Object read(String property);

    void write(String property, Object value);

    default void onAttached(GameObject owner) {
    }

    default void onStart(EngineServices services) {
    }

    default void onUpdate(InputState input, float deltaTimeSeconds) {
    }

    default void onFixedUpdate(float fixedStepSeconds) {
    }

    default void onDestroy() {
    }
}
