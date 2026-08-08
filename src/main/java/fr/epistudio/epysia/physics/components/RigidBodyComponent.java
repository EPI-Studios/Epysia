package fr.epistudio.epysia.physics.components;

import fr.epistudio.epysia.components.Component;
import fr.epistudio.epysia.components.EpysiaComponent;
import fr.epistudio.epysia.components.Export;
import fr.epistudio.epysia.physics.api.BodyHandle;
import fr.epistudio.epysia.physics.api.DynamicProperties;
import fr.epistudio.epysia.physics.api.MotionLocks;
import fr.epistudio.epysia.physics.api.PhysicsWorld;
import fr.epistudio.epysia.physics.api.RigidBodyKind;
import org.joml.Vector3f;
import org.joml.Vector3fc;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;

@EpysiaComponent(name = "Rigid Body", category = "Physics")
public final class RigidBodyComponent extends Component {
    @Export(label = "Kind")
    private RigidBodyKind kind = RigidBodyKind.DYNAMIC;

    @Export(label = "Mass", min = 0.0f, step = 0.1f)
    private float mass = 1.0f;

    @Export(label = "Gravity Scale", step = 0.1f)
    private float gravityScale = 1.0f;

    @Export(label = "Linear Damping", min = 0.0f, step = 0.05f)
    private float linearDamping = 0.0f;

    @Export(label = "Angular Damping", min = 0.0f, step = 0.05f)
    private float angularDamping = 0.0f;

    @Export(label = "Continuous Collision")
    private boolean continuousCollisionDetection = false;

    @Export(label = "Can Sleep")
    private boolean canSleep = true;

    @Export(label = "Sleep Threshold", min = 0.0f, step = 0.05f)
    private float sleepThreshold = 0.5f;

    @Export(label = "Interpolate")
    private boolean interpolate = false;

    @Export(label = "Freeze Position X")
    private boolean freezePositionX = false;

    @Export(label = "Freeze Position Y")
    private boolean freezePositionY = false;

    @Export(label = "Freeze Position Z")
    private boolean freezePositionZ = false;

    @Export(label = "Freeze Rotation X")
    private boolean freezeRotationX = false;

    @Export(label = "Freeze Rotation Y")
    private boolean freezeRotationY = false;

    @Export(label = "Freeze Rotation Z")
    private boolean freezeRotationZ = false;

    @Export(label = "Override Center Of Mass")
    private boolean overrideCenterOfMass = false;

    @Export(label = "Center Of Mass")
    private final Vector3f centerOfMass = new Vector3f();

    private final List<Consumer<PhysicsWorld>> pendingOperations = new ArrayList<>();
    private BodyHandle handle = BodyHandle.NONE;
    private boolean registered;
    private PhysicsWorld world;

    public RigidBodyKind kind() {
        return kind;
    }

    public boolean canSleep() {
        return canSleep;
    }

    public float sleepThreshold() {
        return sleepThreshold;
    }

    public boolean interpolate() {
        return interpolate;
    }

    public boolean overrideCenterOfMass() {
        return overrideCenterOfMass;
    }

    public Vector3f centerOfMass() {
        return centerOfMass;
    }

    public MotionLocks motionLocks() {
        return new MotionLocks(freezePositionX, freezePositionY, freezePositionZ,
                freezeRotationX, freezeRotationY, freezeRotationZ);
    }

    public RigidBodyComponent setMotionLocks(MotionLocks locks) {
        freezePositionX = locks.linearX();
        freezePositionY = locks.linearY();
        freezePositionZ = locks.linearZ();
        freezeRotationX = locks.angularX();
        freezeRotationY = locks.angularY();
        freezeRotationZ = locks.angularZ();
        submit(target -> target.setMotionLocks(handle, locks));
        return this;
    }

    public DynamicProperties dynamicProperties() {
        return new DynamicProperties(mass, gravityScale, linearDamping, angularDamping, continuousCollisionDetection);
    }

    public BodyHandle handle() {
        return handle;
    }

    public boolean isRegistered() {
        return registered;
    }

    public void markRegistered(BodyHandle assignedHandle) {
        this.handle = assignedHandle;
        this.registered = true;
        replayPendingOperations();
    }

    public void attachWorld(PhysicsWorld attachedWorld) {
        this.world = attachedWorld;
        replayPendingOperations();
    }

    private void replayPendingOperations() {
        if (world == null || !registered || pendingOperations.isEmpty()) {
            return;
        }
        List<Consumer<PhysicsWorld>> replayed = List.copyOf(pendingOperations);
        pendingOperations.clear();
        replayed.forEach(operation -> operation.accept(world));
    }

    public void clearRegistered() {
        this.handle = BodyHandle.NONE;
        this.registered = false;
    }

    public void addForce(Vector3fc force) {
        Vector3f copy = new Vector3f(force);
        submit(target -> target.applyForce(handle, copy));
    }

    public void addImpulse(Vector3fc impulse) {
        Vector3f copy = new Vector3f(impulse);
        submit(target -> target.applyImpulse(handle, copy));
    }

    public void addImpulseAt(Vector3fc impulse, Vector3fc worldPoint) {
        Vector3f impulseCopy = new Vector3f(impulse);
        Vector3f pointCopy = new Vector3f(worldPoint);
        submit(target -> target.applyImpulseAt(handle, impulseCopy, pointCopy));
    }

    public void addTorque(Vector3fc torque) {
        Vector3f copy = new Vector3f(torque);
        submit(target -> target.applyTorque(handle, copy));
    }

    public void addAngularImpulse(Vector3fc impulse) {
        Vector3f copy = new Vector3f(impulse);
        submit(target -> target.applyAngularImpulse(handle, copy));
    }

    public void setVelocity(Vector3fc velocity) {
        Vector3f copy = new Vector3f(velocity);
        submit(target -> target.setLinearVelocity(handle, copy));
    }

    public void setAngularVelocity(Vector3fc velocity) {
        Vector3f copy = new Vector3f(velocity);
        submit(target -> target.setAngularVelocity(handle, copy));
    }

    public void setKind(RigidBodyKind newKind) {
        this.kind = newKind;
        submit(target -> target.setBodyKind(handle, newKind));
    }

    public void wake() {
        submit(target -> target.wakeBody(handle));
    }

    public void sleep() {
        submit(target -> target.sleepBody(handle));
    }

    public Optional<Vector3fc> velocity() {
        return live().map(target -> target.getLinearVelocity(handle));
    }

    public Optional<Vector3fc> angularVelocity() {
        return live().map(target -> target.getAngularVelocity(handle));
    }

    public Optional<Vector3fc> worldCenterOfMass() {
        return live().map(target -> target.getCenterOfMass(handle));
    }

    public Optional<Float> computedMass() {
        return live().map(target -> target.getMass(handle));
    }

    public boolean isAwake() {
        return live().map(target -> target.isBodyAwake(handle)).orElse(false);
    }

    private Optional<PhysicsWorld> live() {
        return registered && handle.isValid() ? Optional.ofNullable(world) : Optional.empty();
    }

    private void submit(Consumer<PhysicsWorld> operation) {
        if (live().isPresent()) {
            operation.accept(world);
            return;
        }
        pendingOperations.add(operation);
    }
}
