package fr.epistudio.epysia.physics.api;

import org.joml.Vector3fc;

public record CharacterContact(BodyHandle body, Vector3fc point, Vector3fc normal) {
}
