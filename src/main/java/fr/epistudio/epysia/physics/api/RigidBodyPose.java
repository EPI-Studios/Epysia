package fr.epistudio.epysia.physics.api;

import org.joml.Quaternionfc;
import org.joml.Vector3fc;

public record RigidBodyPose(Vector3fc position, Quaternionfc rotation) {
}
