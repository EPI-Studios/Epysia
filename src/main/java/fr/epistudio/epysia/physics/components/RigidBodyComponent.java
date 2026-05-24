package fr.epistudio.epysia.physics.components;

import fr.epistudio.epysia.components.Component;
import fr.epistudio.epysia.components.EpysiaComponent;
import fr.epistudio.epysia.physics.api.BodyHandle;
import fr.epistudio.epysia.physics.api.CollisionMask;
import fr.epistudio.epysia.physics.api.DynamicProperties;
import fr.epistudio.epysia.physics.api.RigidBodyKind;
import fr.epistudio.epysia.physics.api.ShapeDescriptor;

@EpysiaComponent(name = "Rigid Body", category = "Physics")
public final class RigidBodyComponent extends Component {

    private RigidBodyKind kind = RigidBodyKind.DYNAMIC;
    private ShapeDescriptor shape;
    private DynamicProperties dynamicProperties = DynamicProperties.defaults();
    private CollisionMask collisionMask = CollisionMask.DEFAULT;
    private BodyHandle handle = BodyHandle.NONE;
    private boolean registered;

    public RigidBodyComponent setKind(RigidBodyKind kind) {
        this.kind = kind;
        return this;
    }

    public RigidBodyComponent setShape(ShapeDescriptor shape) {
        this.shape = shape;
        return this;
    }

    public RigidBodyComponent setDynamicProperties(DynamicProperties properties) {
        this.dynamicProperties = properties;
        return this;
    }

    public RigidBodyComponent setCollisionMask(CollisionMask mask) {
        this.collisionMask = mask;
        return this;
    }

    public RigidBodyKind kind() {
        return kind;
    }

    public ShapeDescriptor shape() {
        return shape;
    }

    public DynamicProperties dynamicProperties() {
        return dynamicProperties;
    }

    public CollisionMask collisionMask() {
        return collisionMask;
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
}
