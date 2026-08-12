package fr.epistudio.epysia.scripting;

import fr.epistudio.epysia.EngineServices;
import fr.epistudio.epysia.components.Component;
import fr.epistudio.epysia.gameobjects.GameObject;
import fr.epistudio.epysia.input.InputState;
import fr.epistudio.epysia.net.session.NetworkRole;
import org.joml.Vector3fc;

public abstract class Behaviour extends Component implements PhysicsEventListener {
    public void onStart(EngineServices services) {
    }

    public void onEnable() {
    }

    public void onUpdate(InputState input, float deltaTimeSeconds) {
    }

    public void onFixedUpdate(float fixedStepSeconds) {
    }

    public void onLateUpdate(InputState input, float deltaTimeSeconds) {
    }

    public void onDisable() {
    }

    public void onDestroy() {
    }

    public void onNetworkStart(NetworkRole role) {
    }

    public void onOwnershipChanged(int ownerPeer) {
    }

    public void onNetworkStop() {
    }

    public void onCollision(GameObject other, Vector3fc point, Vector3fc normal, float approachSpeed) {
    }

    public void onCollisionStay(GameObject other, Vector3fc point, Vector3fc normal, float approachSpeed) {
    }

    public void onCollisionExit(GameObject other) {
    }

    public void onTriggerEnter(GameObject other) {
    }

    public void onTriggerStay(GameObject other) {
    }

    public void onTriggerExit(GameObject other) {
    }
}
