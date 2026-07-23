package fr.epistudio.epysia.physics.components;

import fr.epistudio.epysia.components.Component;
import fr.epistudio.epysia.components.EpysiaComponent;
import fr.epistudio.epysia.components.Export;
import fr.epistudio.epysia.components.RequiresComponent;
import fr.epistudio.epysia.components.transforms.Transform2D;
import fr.epistudio.epysia.physics.api.BodyHandle;
import fr.epistudio.epysia.physics.api.ShapeDescriptor;
import fr.epistudio.epysia.physics.box3d.Box3dCharacterController;

@EpysiaComponent(name = "Character Controller 2D", category = "Physics 2D")
@RequiresComponent(Transform2D.class)
public final class CharacterController2D extends Component {

    private static final float BASE_GRAVITY = -9.81f;

    @Export(label = "Capsule Radius", min = 0.05f, max = 2.0f, step = 0.05f)
    private float capsuleRadius = 0.3f;

    @Export(label = "Capsule Height", min = 0.1f, max = 8.0f, step = 0.05f)
    private float capsuleHeight = 1.2f;

    @Export(label = "Jump Speed", min = 0.0f, max = 30.0f, step = 0.1f)
    private float jumpSpeedMetersPerSecond = 6.0f;

    @Export(label = "Gravity Scale", step = 0.1f)
    private float gravityScale = 1.0f;

    @Export(label = "Max Slope", min = 5.0f, max = 85.0f, step = 1.0f)
    private float maxSlopeDegrees = 50.0f;

    @Export(label = "Step Height", min = 0.0f, max = 1.0f, step = 0.01f)
    private float stepHeight = 0.3f;

    @Export(label = "Snap To Ground")
    private boolean snapToGround = true;

    private float desiredMove;
    private float verticalVelocity;
    private boolean grounded;
    private boolean jumpRequested;
    private float jumpRequestSpeed;
    private BodyHandle bodyHandle = BodyHandle.NONE;
    private Box3dCharacterController nativeController;

    public CharacterController2D setCapsule(float radius, float height) {
        this.capsuleRadius = radius;
        this.capsuleHeight = height;
        return this;
    }

    public CharacterController2D setJumpSpeed(float metersPerSecond) {
        this.jumpSpeedMetersPerSecond = metersPerSecond;
        return this;
    }

    public CharacterController2D setGravityScale(float scale) {
        this.gravityScale = scale;
        return this;
    }

    public CharacterController2D setStepHeight(float height) {
        this.stepHeight = height;
        return this;
    }

    public CharacterController2D setSnapToGround(boolean value) {
        this.snapToGround = value;
        return this;
    }

    public void setDesiredMove(float horizontalVelocity) {
        this.desiredMove = horizontalVelocity;
    }

    public void move(float horizontalVelocity) {
        setDesiredMove(horizontalVelocity);
    }

    public void jump() {
        jump(jumpSpeedMetersPerSecond);
    }

    public void jump(float impulseSpeed) {
        this.jumpRequested = true;
        this.jumpRequestSpeed = impulseSpeed;
    }

    public boolean grounded() {
        return grounded;
    }

    public boolean isGrounded() {
        return grounded;
    }

    public float gravityScale() {
        return gravityScale;
    }

    public float gravity() {
        return BASE_GRAVITY * gravityScale;
    }

    public float jumpSpeed() {
        return jumpSpeedMetersPerSecond;
    }

    public float maxSlopeDegrees() {
        return maxSlopeDegrees;
    }

    public float stepHeight() {
        return stepHeight;
    }

    public boolean snapToGround() {
        return snapToGround;
    }

    public float capsuleRadius() {
        return capsuleRadius;
    }

    public float capsuleHalfHeight() {
        return Math.max(0.0f, capsuleHeight * 0.5f - capsuleRadius);
    }

    public ShapeDescriptor.Capsule shape() {
        return new ShapeDescriptor.Capsule(capsuleRadius, capsuleHalfHeight());
    }

    public float verticalVelocity() {
        return verticalVelocity;
    }

    public void setVerticalVelocity(float velocity) {
        this.verticalVelocity = velocity;
    }

    public void setGrounded(boolean grounded) {
        this.grounded = grounded;
    }

    public float consumeDesiredMove() {
        float move = desiredMove;
        desiredMove = 0.0f;
        return move;
    }

    public float consumeJumpRequest() {
        if (!jumpRequested) {
            return 0.0f;
        }
        jumpRequested = false;
        return jumpRequestSpeed;
    }

    public BodyHandle bodyHandle() {
        return bodyHandle;
    }

    public Box3dCharacterController nativeController() {
        return nativeController;
    }

    public boolean hasNativeController() {
        return nativeController != null;
    }

    public void attachNative(BodyHandle handle, Box3dCharacterController controller) {
        this.bodyHandle = handle;
        this.nativeController = controller;
    }
}
