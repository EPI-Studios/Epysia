package fr.epistudio.epysia.net.replication;

import fr.epistudio.epysia.components.Component;
import fr.epistudio.epysia.components.EpysiaComponent;
import fr.epistudio.epysia.components.RequiresComponent;
import fr.epistudio.epysia.gameobjects.GameObject;
import fr.epistudio.epysia.net.prediction.CharacterInputMapper;
import fr.epistudio.epysia.net.prediction.InputSample;
import fr.epistudio.epysia.net.prediction.PredictedMovement;
import fr.epistudio.epysia.physics.components.CharacterControllerComponent;

import java.util.Optional;

@EpysiaComponent(name = "Network Character Controller", category = "Networking")
@RequiresComponent(NetworkObject.class)
public final class NetworkCharacterController extends Component implements PredictedMovement {
    @Replicated
    private float verticalVelocity;
    @Replicated
    private boolean grounded;

    private CharacterInputMapper inputMapper = CharacterInputMapper.INERT;

    public NetworkCharacterController setInputMapper(CharacterInputMapper mapper) {
        this.inputMapper = mapper == null ? CharacterInputMapper.INERT : mapper;
        return this;
    }

    public boolean predicts() {
        return inputMapper != CharacterInputMapper.INERT;
    }

    @Override
    public void simulatePredictedStep(InputSample input, float deltaTimeSeconds) {
        controller().ifPresent(controller -> inputMapper.applyTo(controller, input, deltaTimeSeconds));
    }

    @Override
    public void onReplicatedStateCapture() {
        controller().ifPresent(controller -> {
            verticalVelocity = controller.verticalVelocity();
            grounded = controller.grounded();
        });
    }

    @Override
    public void onReplicatedStateApplied() {
        controller().ifPresent(controller -> {
            controller.setVerticalVelocity(verticalVelocity);
            controller.setGrounded(grounded);
        });
    }

    private Optional<CharacterControllerComponent> controller() {
        GameObject owner = ownerOrNull();
        if (owner == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(owner.getComponentOrNull(CharacterControllerComponent.class));
    }
}
