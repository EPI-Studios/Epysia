package fr.epistudio.epysia.net.replication;

import fr.epistudio.epysia.EngineServices;
import fr.epistudio.epysia.components.Component;
import fr.epistudio.epysia.components.EpysiaComponent;
import fr.epistudio.epysia.components.Export;
import fr.epistudio.epysia.components.RequiresComponent;
import fr.epistudio.epysia.gameobjects.GameObject;
import fr.epistudio.epysia.physics.PhysicsSystem;
import fr.epistudio.epysia.physics.api.BodyHandle;
import fr.epistudio.epysia.physics.api.PhysicsWorld;
import fr.epistudio.epysia.physics.api.RigidBodyPose;
import fr.epistudio.epysia.physics.components.RigidBodyComponent;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.Optional;

@EpysiaComponent(name = "Network Rigid Body", category = "Networking",
        description = "Replicates a rigid body's motion and smooths it out on the clients.")
@RequiresComponent(NetworkObject.class)
public final class NetworkRigidBody extends Component {
    private static final float POSITION_PRECISION = 0.001f;
    private static final float ROTATION_PRECISION = 1.0f;
    private static final float VELOCITY_PRECISION = 0.01f;

    @Export(label = "Replicate Velocity")
    private boolean replicateVelocity = true;

    @Replicated(interpolate = true, precision = POSITION_PRECISION)
    private final Vector3f position = new Vector3f();
    @Replicated(interpolate = true, precision = ROTATION_PRECISION)
    private final Quaternionf rotation = new Quaternionf();
    @Replicated(interpolate = true, precision = VELOCITY_PRECISION)
    private final Vector3f linearVelocity = new Vector3f();
    @Replicated(interpolate = true, precision = VELOCITY_PRECISION)
    private final Vector3f angularVelocity = new Vector3f();

    private PhysicsSystem physics;

    @Override
    public void onPlayStart(EngineServices services) {
        physics = services.systems().get(PhysicsSystem.class);
    }

    public boolean replicateVelocity() {
        return replicateVelocity;
    }

    public NetworkRigidBody setReplicateVelocity(boolean value) {
        this.replicateVelocity = value;
        return this;
    }

    @Override
    public void onReplicatedStateCapture() {
        body().ifPresent(handle -> {
            PhysicsWorld world = physics.world();
            RigidBodyPose pose = world.getBodyPose(handle);
            position.set(pose.position());
            rotation.set(pose.rotation());
            linearVelocity.set(world.getLinearVelocity(handle));
            angularVelocity.set(world.getAngularVelocity(handle));
        });
    }

    @Override
    public void onReplicatedStateApplied() {
        body().ifPresent(handle -> {
            PhysicsWorld world = physics.world();
            world.setBodyPose(handle, new RigidBodyPose(position, rotation));
            if (replicateVelocity) {
                world.setLinearVelocity(handle, linearVelocity);
                world.setAngularVelocity(handle, angularVelocity);
            }
        });
    }

    private Optional<BodyHandle> body() {
        GameObject owner = ownerOrNull();
        if (physics == null || owner == null || owner.getComponentOrNull(RigidBodyComponent.class) == null) {
            return Optional.empty();
        }
        return physics.bodyOf(owner);
    }
}
