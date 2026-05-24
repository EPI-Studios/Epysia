package fr.epistudio.epysia.physics.api;

import org.joml.Quaternionfc;
import org.joml.Vector3fc;

import java.util.List;
import java.util.Optional;

public interface PhysicsWorld extends AutoCloseable {

    BodyHandle addStaticBody(ShapeDescriptor shape, RigidBodyPose pose, CollisionMask mask);

    BodyHandle addDynamicBody(ShapeDescriptor shape, RigidBodyPose pose, DynamicProperties properties, CollisionMask mask);

    BodyHandle addKinematicBody(ShapeDescriptor shape, RigidBodyPose pose, CollisionMask mask);

    BodyHandle addAreaBody(ShapeDescriptor shape, RigidBodyPose pose, CollisionMask mask);

    void removeBody(BodyHandle body);

    void setBodyPose(BodyHandle body, RigidBodyPose pose);

    RigidBodyPose getBodyPose(BodyHandle body);

    void applyForce(BodyHandle body, Vector3fc force);

    void applyImpulse(BodyHandle body, Vector3fc impulse);

    void setLinearVelocity(BodyHandle body, Vector3fc velocity);

    Vector3fc getLinearVelocity(BodyHandle body);

    void sleepBody(BodyHandle body);

    void wakeBody(BodyHandle body);

    JointHandle addFixedJoint(BodyHandle first, BodyHandle second, RigidBodyPose localPoseFirst, RigidBodyPose localPoseSecond, boolean contactsEnabled);

    JointHandle addSphericalJoint(BodyHandle first, BodyHandle second, Vector3fc localAnchorFirst, Vector3fc localAnchorSecond, boolean contactsEnabled);

    void removeJoint(JointHandle joint);

    Optional<RaycastHit> raycast(Vector3fc origin, Vector3fc direction, float maxDistance, QueryFilter filter);

    Optional<ShapeCastHit> shapeCast(ShapeDescriptor shape, RigidBodyPose from, Vector3fc direction, float maxDistance, QueryFilter filter);

    long[] overlap(ShapeDescriptor shape, RigidBodyPose pose, QueryFilter filter);

    List<ContactEvent> drainContactEvents();

    List<AreaEvent> drainAreaEvents();

    void setGravity(Vector3fc gravity);

    void step(float stepSeconds);

    @Override
    void close();

    static RigidBodyPose poseOf(Vector3fc position, Quaternionfc rotation) {
        return new RigidBodyPose(position, rotation);
    }
}
