package fr.epistudio.epysia.components.viewmodel;

import fr.epistudio.epysia.GameSystem;
import fr.epistudio.epysia.components.transforms.Transform3D;
import fr.epistudio.epysia.gameobjects.GameObject;
import fr.epistudio.epysia.input.InputState;
import fr.epistudio.epysia.scene.Scene;
import org.joml.Vector3f;
import org.joml.Vector3fc;

public final class ViewModelSystem implements GameSystem {

    private final Vector3f scratchAnchorPosition = new Vector3f();

    @Override
    public void update(Scene scene, InputState input, float deltaTimeSeconds) {
        for (GameObject gameObject : scene.gameObjects()) {
            gameObject.getComponent(ViewModelComponent.class).ifPresent(viewModel ->
                    gameObject.getComponent(Transform3D.class).ifPresent(transform ->
                            updateViewModel(viewModel, transform, deltaTimeSeconds)));
        }
    }

    private void updateViewModel(ViewModelComponent viewModel, Transform3D transform, float deltaTimeSeconds) {
        transform.parent().ifPresent(anchor -> {
            anchor.worldMatrix().getTranslation(scratchAnchorPosition);
            boolean moving = viewModel.trackAnchorMovement(scratchAnchorPosition, deltaTimeSeconds);
            viewModel.advance(deltaTimeSeconds, moving);
            pose(viewModel, transform, moving);
        });
    }

    private void pose(ViewModelComponent viewModel, Transform3D transform, boolean moving) {
        float amplitude = moving ? viewModel.bobAmplitude() : viewModel.bobAmplitude() * 0.25f;
        float bobVertical = (float) Math.sin(viewModel.bobPhase() * 2.0) * amplitude;
        float bobHorizontal = (float) Math.cos(viewModel.bobPhase()) * amplitude * 0.6f;
        float recoil = viewModel.recoilStrength();
        Vector3fc rest = viewModel.restOffset();
        transform.setPosition(
                rest.x() + bobHorizontal,
                rest.y() + bobVertical + recoil * viewModel.recoilUpward(),
                rest.z() + recoil * viewModel.recoilBackward());
        transform.setRotationEuler(recoil * viewModel.recoilPitchRadians(), 0.0f, 0.0f);
    }
}
