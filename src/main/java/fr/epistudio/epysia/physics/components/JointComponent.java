package fr.epistudio.epysia.physics.components;

import fr.epistudio.epysia.components.Component;
import fr.epistudio.epysia.components.Export;
import fr.epistudio.epysia.gameobjects.GameObject;
import fr.epistudio.epysia.physics.api.JointDescriptor;
import fr.epistudio.epysia.physics.api.JointHandle;
import org.joml.Vector3f;
import org.joml.Vector3fc;

import java.util.Optional;
import java.util.Objects;

public abstract class JointComponent extends Component {
    @Export(label = "Connected Body")
    private GameObject connectedBody;

    @Export(label = "Anchor")
    private final Vector3f anchor = new Vector3f();

    private JointHandle handle = JointHandle.NONE;
    private boolean registered;
    private int builtSignature;

    public Optional<GameObject> connectedBody() {
        return Optional.ofNullable(connectedBody);
    }

    public JointComponent setConnectedBody(GameObject body) {
        this.connectedBody = body;
        return this;
    }

    public Vector3f anchor() {
        return anchor;
    }

    public JointHandle handle() {
        return handle;
    }

    public boolean isRegistered() {
        return registered;
    }

    public void markRegistered(JointHandle assignedHandle) {
        this.handle = assignedHandle;
        this.registered = true;
        this.builtSignature = signature();
    }

    public void clearRegistered() {
        this.handle = JointHandle.NONE;
        this.registered = false;
    }

    public boolean requiresRebuild() {
        return registered && signature() != builtSignature;
    }

    public abstract JointDescriptor describe(Vector3fc worldAnchor, Vector3fc connectedWorldAnchor);

    protected abstract int settingsSignature();

    private int signature() {
        return Objects.hash(anchor.x(), anchor.y(), anchor.z(),
                System.identityHashCode(connectedBody), settingsSignature());
    }
}
