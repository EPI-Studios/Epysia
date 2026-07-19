package fr.epistudio.epysia.components.viewmodel;

import fr.epistudio.epysia.components.Component;
import fr.epistudio.epysia.components.EpysiaComponent;
import fr.epistudio.epysia.components.Export;
import org.joml.Vector3f;
import org.joml.Vector3fc;

@EpysiaComponent(name = "View Model", category = "Rendering")
public final class ViewModelComponent extends Component {

    @Export(label = "Rest Offset", step = 0.01f)
    private final Vector3f restOffset = new Vector3f(0.28f, -0.24f, -0.55f);

    @Export(label = "Bob Amplitude", min = 0.0f, max = 0.1f, step = 0.002f)
    private float bobAmplitude = 0.016f;

    @Export(label = "Bob Frequency", min = 0.0f, max = 20.0f, step = 0.5f)
    private float bobFrequency = 11.0f;

    @Export(label = "Recoil Backward", min = 0.0f, max = 1.0f, step = 0.01f)
    private float recoilBackward = 0.16f;

    @Export(label = "Recoil Upward", min = 0.0f, max = 1.0f, step = 0.01f)
    private float recoilUpward = 0.04f;

    @Export(label = "Recoil Pitch", min = 0.0f, max = 1.0f, step = 0.01f)
    private float recoilPitchRadians = 0.35f;

    @Export(label = "Recoil Recovery", min = 0.1f, max = 30.0f, step = 0.5f)
    private float recoilRecoveryPerSecond = 7.0f;

    private float recoilStrength;
    private float bobPhase;
    private boolean anchorTracked;
    private final Vector3f previousAnchorPosition = new Vector3f();

    public void kick() {
        recoilStrength = 1.0f;
    }

    public float recoilStrength() {
        return recoilStrength;
    }

    public float bobPhase() {
        return bobPhase;
    }

    public Vector3fc restOffset() {
        return restOffset;
    }

    public float bobAmplitude() {
        return bobAmplitude;
    }

    public float recoilBackward() {
        return recoilBackward;
    }

    public float recoilUpward() {
        return recoilUpward;
    }

    public float recoilPitchRadians() {
        return recoilPitchRadians;
    }

    public void advance(float deltaTimeSeconds, boolean moving) {
        bobPhase += deltaTimeSeconds * (moving ? bobFrequency : bobFrequency * 0.25f);
        recoilStrength = Math.max(0.0f, recoilStrength - deltaTimeSeconds * recoilRecoveryPerSecond);
    }

    public boolean trackAnchorMovement(Vector3fc anchorWorldPosition, float deltaTimeSeconds) {
        if (!anchorTracked) {
            previousAnchorPosition.set(anchorWorldPosition);
            anchorTracked = true;
            return false;
        }
        float deltaX = anchorWorldPosition.x() - previousAnchorPosition.x;
        float deltaZ = anchorWorldPosition.z() - previousAnchorPosition.z;
        previousAnchorPosition.set(anchorWorldPosition);
        float horizontalDistance = (float) Math.sqrt(deltaX * deltaX + deltaZ * deltaZ);
        return horizontalDistance > deltaTimeSeconds * 0.5f;
    }
}
