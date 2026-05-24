package fr.epistudio.epysia.components;

public final class FlyCameraComponent extends Component {

    private static final float MAX_PITCH = (float) Math.toRadians(89.0);

    private float moveSpeed = 5.0f;
    private float boostMultiplier = 3.0f;
    private float lookSensitivity = 0.0025f;
    private float yawRadians;
    private float pitchRadians;

    public FlyCameraComponent setMoveSpeed(float metersPerSecond) {
        this.moveSpeed = metersPerSecond;
        return this;
    }

    public FlyCameraComponent setBoostMultiplier(float multiplier) {
        this.boostMultiplier = multiplier;
        return this;
    }

    public FlyCameraComponent setLookSensitivity(float radiansPerPixel) {
        this.lookSensitivity = radiansPerPixel;
        return this;
    }

    public FlyCameraComponent setInitialOrientation(float yawDegrees, float pitchDegrees) {
        this.yawRadians = (float) Math.toRadians(yawDegrees);
        this.pitchRadians = clampPitch((float) Math.toRadians(pitchDegrees));
        return this;
    }

    public float moveSpeed() {
        return moveSpeed;
    }

    public float boostMultiplier() {
        return boostMultiplier;
    }

    public float lookSensitivity() {
        return lookSensitivity;
    }

    public float yawRadians() {
        return yawRadians;
    }

    public float pitchRadians() {
        return pitchRadians;
    }

    public void addYaw(float deltaRadians) {
        yawRadians += deltaRadians;
    }

    public void addPitch(float deltaRadians) {
        pitchRadians = clampPitch(pitchRadians + deltaRadians);
    }

    private static float clampPitch(float pitch) {
        if (pitch > MAX_PITCH) {
            return MAX_PITCH;
        }
        if (pitch < -MAX_PITCH) {
            return -MAX_PITCH;
        }
        return pitch;
    }
}
