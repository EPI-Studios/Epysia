package fr.epistudio.epysia.net.prediction;

import fr.epistudio.epysia.gameobjects.GameObject;
import fr.epistudio.epysia.physics.PhysicsSystem;
import fr.epistudio.epysia.physics.components.CharacterControllerComponent;

public final class CharacterRollback implements PredictedPhysics {

    private final PhysicsSystem physics;
    private final GameObject gameObject;
    private final Runnable restoreAuthoritativeState;

    public CharacterRollback(PhysicsSystem physics, GameObject gameObject,
                             Runnable restoreAuthoritativeState) {
        this.physics = physics;
        this.gameObject = gameObject;
        this.restoreAuthoritativeState = restoreAuthoritativeState;
    }

    public boolean hasCharacter() {
        return gameObject.getComponentOrNull(CharacterControllerComponent.class) != null;
    }

    @Override
    public void beginReplay() {
        restoreAuthoritativeState.run();
        physics.syncCharacterToTransform(gameObject);
    }

    @Override
    public void stepReplay(float deltaTimeSeconds) {
        physics.stepCharacter(gameObject, deltaTimeSeconds);
    }

    @Override
    public void endReplay() {
        physics.syncCharacterToTransform(gameObject);
    }
}
