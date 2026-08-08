package fr.epistudio.epysia.net.prediction;

import fr.epistudio.epysia.physics.api.BodyHandle;
import fr.epistudio.epysia.physics.api.PhysicsWorld;
import fr.epistudio.epysia.physics.api.RigidBodyPose;
import fr.epistudio.epysia.physics.api.SleepState;
import org.joml.Quaternionf;
import org.joml.Vector3f;

public final class RigidBodyState {
    private final BodyHandle body;
    private final Vector3f position = new Vector3f();
    private final Quaternionf rotation = new Quaternionf();
    private final Vector3f linearVelocity = new Vector3f();
    private final Vector3f angularVelocity = new Vector3f();
    private SleepState sleepState = SleepState.AWAKE;

    public RigidBodyState(BodyHandle body) {
        this.body = body;
    }

    public BodyHandle body() {
        return body;
    }

    public void captureFrom(PhysicsWorld world) {
        RigidBodyPose pose = world.getBodyPose(body);
        position.set(pose.position());
        rotation.set(pose.rotation());
        linearVelocity.set(world.getLinearVelocity(body));
        angularVelocity.set(world.getAngularVelocity(body));
        sleepState = world.getSleepState(body);
    }

    public void restoreInto(PhysicsWorld world) {
        world.setBodyPose(body, new RigidBodyPose(position, rotation));
        world.setLinearVelocity(body, linearVelocity);
        world.setAngularVelocity(body, angularVelocity);
        world.setSleepState(body, sleepState);
    }
}
