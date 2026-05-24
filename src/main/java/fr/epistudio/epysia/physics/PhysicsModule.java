package fr.epistudio.epysia.physics;

import fr.epistudio.epysia.EditorContext;
import fr.epistudio.epysia.EngineModule;
import fr.epistudio.epysia.SystemRegistry;
import fr.epistudio.epysia.components.Camera3D;
import fr.epistudio.epysia.components.transforms.Transform3D;
import fr.epistudio.epysia.gameobjects.GameObject;
import fr.epistudio.epysia.physics.components.CharacterControllerComponent;
import fr.epistudio.epysia.physics.components.CharacterControllerSystem;
import fr.epistudio.epysia.physics.components.ColliderShape;

public final class PhysicsModule implements EngineModule {

    @Override
    public int order() {
        return 100;
    }

    @Override
    public void registerSystems(SystemRegistry registry) {
        registry.add(new PhysicsSystem());
        registry.add(new CharacterControllerSystem());
    }

    @Override
    public void registerEditorExtensions(EditorContext editor) {
        editor.registerPrimitive("Player (capsule + controller)", () -> {
            GameObject player = new GameObject("Player");
            player.addComponent(new Transform3D().setPosition(0.0f, 2.0f, 0.0f));
            player.addComponent(new Camera3D().setFieldOfViewDegrees(60.0f).setNearFar(0.05f, 500.0f));
            player.addComponent(new CharacterControllerComponent()
                    .setShape(ColliderShape.capsule(0.4f, 0.9f))
                    .setMoveSpeed(5.0f)
                    .setJumpSpeed(6.5f));
            return player;
        });
    }
}
