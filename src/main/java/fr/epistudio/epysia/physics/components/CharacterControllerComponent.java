package fr.epistudio.epysia.physics.components;

import fr.epistudio.epysia.components.Component;
import fr.epistudio.epysia.components.EpysiaComponent;
import fr.epistudio.epysia.components.Export;
import fr.epistudio.epysia.components.RequiresComponent;
import fr.epistudio.epysia.components.transforms.Transform3D;
import fr.epistudio.epysia.physics.api.BodyHandle;
import fr.epistudio.epysia.physics.api.ShapeDescriptor;
import fr.epistudio.epysia.physics.box3d.Box3dCharacterController;
import org.joml.Vector3f;
import org.joml.Vector3fc;

@EpysiaComponent(name = "Character Controller", category = "Physics")
@RequiresComponent(Transform3D.class)
public final class CharacterControllerComponent extends Component {

    @Export(label = "Capsule Radius", min = 0.05f, max = 2.0f, step = 0.05f)
    private float capsuleRadius = 0.3f;
    @Export(label = "Capsule Half Height", min = 0.0f, max = 4.0f, step = 0.05f)
    private float capsuleHalfHeight = 0.2f;
    @Export(label = "Move Speed", min = 0.0f, max = 50.0f, step = 0.1f)
    private float moveSpeedMetersPerSecond = 5.0f;
    @Export(label = "Jump Speed", min = 0.0f, max = 30.0f, step = 0.1f)
    private float jumpSpeedMetersPerSecond = 6.0f;
    @Export(label = "Gravity", min = -50.0f, max = 0.0f, step = 0.1f)
    private float gravityAcceleration = -18.0f;
    @Export(label = "Look Sensitivity", min = 0.0001f, max = 0.05f, step = 0.0001f)
    private float lookSensitivity = 0.0025f;
    private float yawRadians;
    private float pitchRadians;
    private float verticalVelocity;
    private boolean grounded;
    private BodyHandle bodyHandle = BodyHandle.NONE;
    private Box3dCharacterController nativeController;
    private final Vector3f desiredHorizontalMovement = new Vector3f();
    private boolean jumpRequested;

    public CharacterControllerComponent setCapsule(float radius, float halfHeight) {
        this.capsuleRadius = radius;
        this.capsuleHalfHeight = halfHeight;
        return this;
    }

    public CharacterControllerComponent setMoveSpeed(float metersPerSecond) {
        this.moveSpeedMetersPerSecond = metersPerSecond;
        return this;
    }

    public CharacterControllerComponent setJumpSpeed(float metersPerSecond) {
        this.jumpSpeedMetersPerSecond = metersPerSecond;
        return this;
    }

    public CharacterControllerComponent setGravity(float metersPerSecondSquared) {
        this.gravityAcceleration = metersPerSecondSquared;
        return this;
    }

    public CharacterControllerComponent setLookSensitivity(float radiansPerPixel) {
        this.lookSensitivity = radiansPerPixel;
        return this;
    }

    public CharacterControllerComponent setInitialOrientation(float yawDegrees, float pitchDegrees) {
        this.yawRadians = (float) Math.toRadians(yawDegrees);
        this.pitchRadians = (float) Math.toRadians(pitchDegrees);
        return this;
    }

    public ShapeDescriptor shape() {
        return ColliderShape.capsule(capsuleRadius, capsuleHalfHeight);
    }

    public float moveSpeed() {
        return moveSpeedMetersPerSecond;
    }

    public float jumpSpeed() {
        return jumpSpeedMetersPerSecond;
    }

    public float gravity() {
        return gravityAcceleration;
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

    public float verticalVelocity() {
        return verticalVelocity;
    }

    public boolean grounded() {
        return grounded;
    }

    public BodyHandle bodyHandle() {
        return bodyHandle;
    }

    public Box3dCharacterController nativeController() {
        return nativeController;
    }

    public void addYaw(float deltaRadians) {
        yawRadians += deltaRadians;
    }

    public void addPitch(float deltaRadians) {
        float maxPitch = (float) Math.toRadians(89.0);
        pitchRadians = Math.max(-maxPitch, Math.min(maxPitch, pitchRadians + deltaRadians));
    }

    public void setVerticalVelocity(float velocity) {
        this.verticalVelocity = velocity;
    }

    public void setGrounded(boolean grounded) {
        this.grounded = grounded;
    }

    public void attachNative(BodyHandle handle, Box3dCharacterController controller) {
        this.bodyHandle = handle;
        this.nativeController = controller;
    }

    public void move(Vector3fc velocityMetersPerSecond) {
        this.desiredHorizontalMovement.set(velocityMetersPerSecond.x(), 0.0f, velocityMetersPerSecond.z());
    }

    public boolean isGrounded() {
        return grounded;
    }

    public void jump() {
        this.jumpRequested = true;
    }

    public void setDesiredHorizontalMove(Vector3fc movement) {
        this.desiredHorizontalMovement.set(movement);
    }

    public Vector3f consumeDesiredHorizontalMove(Vector3f destination) {
        destination.set(desiredHorizontalMovement);
        desiredHorizontalMovement.set(0.0f);
        return destination;
    }

    public void requestJump() {
        this.jumpRequested = true;
    }

    public boolean consumeJumpRequest() {
        boolean was = jumpRequested;
        jumpRequested = false;
        return was;
    }
}
