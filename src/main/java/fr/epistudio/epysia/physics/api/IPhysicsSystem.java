package fr.epistudio.epysia.physics.api;

import fr.epistudio.epysia.GameSystem;
import fr.epistudio.epysia.gameobjects.GameObject;
import org.joml.Vector3fc;

import java.util.Optional;

public interface IPhysicsSystem extends GameSystem {

    Optional<RaycastHit> raycast(Vector3fc origin, Vector3fc direction, float maxDistance);

    Optional<GameObject> ownerOf(BodyHandle body);

    PhysicsWorld world();
}
