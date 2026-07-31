package fr.epistudio.epysia.physics.components;

import fr.epistudio.epysia.components.Component;
import fr.epistudio.epysia.components.EpysiaComponent;
import fr.epistudio.epysia.components.Export;
import fr.epistudio.epysia.physics.api.BodyHandle;
import fr.epistudio.epysia.physics.api.DynamicProperties;
import fr.epistudio.epysia.physics.api.RigidBodyKind;

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

    private BodyHandle handle = BodyHandle.NONE;
    private boolean registered;

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
    }

    public void clearRegistered() {
        this.handle = BodyHandle.NONE;
        this.registered = false;
    }
}
