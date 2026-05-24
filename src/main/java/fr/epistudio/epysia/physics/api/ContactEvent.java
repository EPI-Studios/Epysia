package fr.epistudio.epysia.physics.api;

import org.joml.Vector3fc;

public record ContactEvent(
        BodyHandle first,
        BodyHandle second,
        Vector3fc point,
        Vector3fc normal,
        float impulse
) {
}
