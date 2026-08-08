package fr.epistudio.epysia.net.prediction;

import fr.epistudio.epysia.physics.components.CharacterControllerComponent;

@FunctionalInterface
public interface CharacterInputMapper {
    CharacterInputMapper INERT = (controller, input, deltaTimeSeconds) -> {
    };

    void applyTo(CharacterControllerComponent controller, InputSample input, float deltaTimeSeconds);
}
