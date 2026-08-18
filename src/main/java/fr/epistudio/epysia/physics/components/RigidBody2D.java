package fr.epistudio.epysia.physics.components;

import fr.epistudio.epysia.components.Component;
import fr.epistudio.epysia.components.EpysiaComponent;
import fr.epistudio.epysia.components.Export;
import fr.epistudio.epysia.physics.api.BodyHandle;
import fr.epistudio.epysia.physics.api.DynamicProperties;
import fr.epistudio.epysia.physics.api.RigidBodyKind;

@EpysiaComponent(name = "Rigid Body 2D", category = "Physics",
        description = "Body driven by the 2D solver. Needs a Collider 2D on the same object.")
public final class RigidBody2D extends Component {
    @Export(label = "Kind")
    private RigidBodyKind kind = RigidBodyKind.DYNAMIC;

    @Export(label = "Mass", min = 0.0f, step = 0.1f)
    private float mass = 1.0f;

    @Export(label = "Gravity Scale", step = 0.1f)
    private float gravityScale = 1.0f;

    @Export(label = "Fixed Rotation")
    private boolean fixedRotation = false;

    @Export(label = "Interpolate")
    private boolean interpolate = false;

    private BodyHandle handle = BodyHandle.NONE;
    private boolean registered;

    public RigidBodyKind kind() {
        return kind;
    }

    public RigidBody2D setKind(RigidBodyKind kind) {
        this.kind = kind;
        return this;
    }

    public float mass() {
        return mass;
    }

    public RigidBody2D setMass(float mass) {
        this.mass = mass;
        return this;
    }

    public float gravityScale() {
        return gravityScale;
    }

    public RigidBody2D setGravityScale(float gravityScale) {
        this.gravityScale = gravityScale;
        return this;
    }

    public boolean fixedRotation() {
        return fixedRotation;
    }

    public boolean interpolate() {
        return interpolate;
    }

    public RigidBody2D setInterpolate(boolean value) {
        this.interpolate = value;
        return this;
    }

    public RigidBody2D setFixedRotation(boolean fixedRotation) {
        this.fixedRotation = fixedRotation;
        return this;
    }

    public DynamicProperties dynamicProperties() {
        return new DynamicProperties(mass, gravityScale, 0.0f, 0.0f, false);
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
    }

    public void clearRegistered() {
        this.handle = BodyHandle.NONE;
        this.registered = false;
    }
}
