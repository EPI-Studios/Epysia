package fr.epistudio.epysia.physics.components;

import fr.epistudio.epysia.components.Component;
import fr.epistudio.epysia.components.EpysiaComponent;
import fr.epistudio.epysia.components.Export;
import fr.epistudio.epysia.components.RequiresComponent;
import fr.epistudio.epysia.components.transforms.Transform3D;
import fr.epistudio.epysia.physics.api.BodyHandle;
import fr.epistudio.epysia.physics.api.ShapeDescriptor;
import fr.epistudio.epysia.physics.box3d.Box3dCharacterController;
import fr.epistudio.epysia.physics.api.CharacterContact;
import org.joml.Vector3f;
import org.joml.Vector3fc;

import java.util.List;

@EpysiaComponent(name = "Character Controller", category = "Physics")
@RequiresComponent(Transform3D.class)
public class CharacterControllerComponent extends Component {

    private static final float WALL_NORMAL_LIMIT = 0.5f;
    private static final float CEILING_NORMAL_LIMIT = 0.5f;

    @Export(label = "Capsule Radius", min = 0.05f, max = 2.0f, step = 0.05f)
    private float capsuleRadius = 0.5f;
    @Export(label = "Capsule Half Height", min = 0.0f, max = 4.0f, step = 0.05f)
    private float capsuleHalfHeight = 1.0f;
    @Export(label = "Move Speed", min = 0.0f, max = 50.0f, step = 0.1f)
    private float moveSpeedMetersPerSecond = 5.0f;
    @Export(label = "Jump Speed", min = 0.0f, max = 30.0f, step = 0.1f)
    private float jumpSpeedMetersPerSecond = 6.0f;
    @Export(label = "Gravity", min = -50.0f, max = 0.0f, step = 0.1f)
    private float gravityAcceleration = -18.0f;
    @Export(label = "Max Slope", min = 5.0f, max = 85.0f, step = 1.0f)
    private float maxSlopeDegrees = 50.0f;
    @Export(label = "Step Height", min = 0.0f, max = 1.0f, step = 0.01f)
    private float stepHeight = 0.3f;
    @Export(label = "Snap To Ground")
    private boolean snapToGround = true;
    @Export(label = "Apply Gravity")
    private boolean applyGravity = true;
    private float verticalVelocity;
    private boolean grounded;
    private BodyHandle bodyHandle = BodyHandle.NONE;
    private final Vector3f pendingTeleport = new Vector3f();
    private boolean teleportRequested;
    private Box3dCharacterController nativeController;
    private final Vector3f desiredHorizontalMovement = new Vector3f();
    private final Vector3f groundNormal = new Vector3f(0.0f, 1.0f, 0.0f);
    private final Vector3f clippedDelta = new Vector3f();
    private List<CharacterContact> contacts = List.of();
    private boolean hitWall;
    private boolean hitCeiling;
    private boolean jumpRequested;

    private boolean simulated = true;

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

    public float maxSlopeDegrees() {
        return maxSlopeDegrees;
    }

    public CharacterControllerComponent setMaxSlopeDegrees(float degrees) {
        this.maxSlopeDegrees = degrees;
        return this;
    }

    public float stepHeight() {
        return stepHeight;
    }

    public CharacterControllerComponent setStepHeight(float height) {
        this.stepHeight = height;
        return this;
    }

    public boolean snapToGround() {
        return snapToGround;
    }

    public CharacterControllerComponent setSnapToGround(boolean value) {
        this.snapToGround = value;
        return this;
    }

    public boolean simulated() {
        return simulated;
    }

    public CharacterControllerComponent setSimulated(boolean value) {
        simulated = value;
        return this;
    }

    public boolean applyGravity() {
        return applyGravity;
    }

    public CharacterControllerComponent setApplyGravity(boolean value) {
        this.applyGravity = value;
        return this;
    }

    public Vector3fc groundNormal() {
        return groundNormal;
    }

    public Vector3fc clippedDelta() {
        return clippedDelta;
    }

    public List<CharacterContact> contacts() {
        return contacts;
    }

    public boolean hitWall() {
        return hitWall;
    }

    public boolean hitCeiling() {
        return hitCeiling;
    }

    public boolean groundBelow(float depth) {
        return nativeController != null && bodyHandle.isValid()
                && nativeController.groundBelow(bodyHandle, depth);
    }

    public void setMoveResult(Vector3fc newGroundNormal, Vector3fc newClippedDelta,
                              List<CharacterContact> newContacts) {
        groundNormal.set(newGroundNormal);
        clippedDelta.set(newClippedDelta);
        contacts = newContacts;
        hitWall = anyContactMatches(newContacts, normalY -> Math.abs(normalY) < WALL_NORMAL_LIMIT);
        hitCeiling = anyContactMatches(newContacts, normalY -> normalY < -CEILING_NORMAL_LIMIT);
    }

    private static boolean anyContactMatches(List<CharacterContact> candidates, FloatPredicate test) {
        for (CharacterContact contact : candidates) {
            if (test.matches(contact.normal().y())) {
                return true;
            }
        }
        return false;
    }

    private interface FloatPredicate {
        boolean matches(float value);
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

    public void teleportTo(Vector3fc worldPosition) {
        pendingTeleport.set(worldPosition);
        teleportRequested = true;
    }

    public boolean consumeTeleport(Vector3f destination) {
        if (!teleportRequested) {
            return false;
        }
        destination.set(pendingTeleport);
        teleportRequested = false;
        return true;
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
