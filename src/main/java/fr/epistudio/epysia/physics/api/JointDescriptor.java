package fr.epistudio.epysia.physics.api;

import org.joml.Vector3fc;

public sealed interface JointDescriptor {
    record Hinge(
            Vector3fc worldPivot,
            Vector3fc worldAxis,
            JointLimits angleLimits,
            boolean motorEnabled,
            float motorSpeed,
            float maxMotorTorque
    ) implements JointDescriptor {}

    record Ball(
            Vector3fc worldPivot,
            boolean coneLimitEnabled,
            float coneLimitRadians,
            JointLimits twistLimits,
            boolean springEnabled,
            float springHertz,
            float springDampingRatio
    ) implements JointDescriptor {}

    record Weld(
            Vector3fc worldPivot,
            float linearHertz,
            float linearDampingRatio,
            float angularHertz,
            float angularDampingRatio
    ) implements JointDescriptor {}

    record Distance(
            Vector3fc worldAnchorFirst,
            Vector3fc worldAnchorSecond,
            float length,
            JointLimits lengthLimits,
            boolean springEnabled,
            float springHertz,
            float springDampingRatio
    ) implements JointDescriptor {}

    record Slider(
            Vector3fc worldPivot,
            Vector3fc worldAxis,
            JointLimits translationLimits,
            boolean motorEnabled,
            float motorSpeed,
            float maxMotorForce
    ) implements JointDescriptor {}

    record CollisionFilter() implements JointDescriptor {}
}
