package fr.epistudio.epysia.physics.api;

import org.joml.Vector2fc;

public record RaycastHit2D(BodyHandle body, Vector2fc point, Vector2fc normal, float distance) {
}
