package fr.epistudio.epysia.scripting;

import fr.epistudio.epysia.gameobjects.GameObject;
import org.joml.Vector3fc;

public interface PhysicsEventListener {

    default void onCollision(GameObject other, Vector3fc point, Vector3fc normal, float approachSpeed) {
    }

    default void onCollisionStay(GameObject other, Vector3fc point, Vector3fc normal, float approachSpeed) {
    }

    default void onCollisionExit(GameObject other) {
    }

    default void onTriggerEnter(GameObject other) {
    }

    default void onTriggerStay(GameObject other) {
    }

    default void onTriggerExit(GameObject other) {
    }
}
