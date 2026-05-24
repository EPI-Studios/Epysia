package fr.epistudio.epysia.physics.api;

import org.joml.Vector3fc;

public record RaycastHit(BodyHandle body, Vector3fc point, Vector3fc normal, float distance) {
}
