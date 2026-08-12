package fr.epistudio.epysia.navigation;

import fr.epistudio.epysia.components.Component;
import fr.epistudio.epysia.components.EpysiaComponent;
import fr.epistudio.epysia.components.Export;
import fr.epistudio.epysia.components.RequiresComponent;
import fr.epistudio.epysia.components.transforms.Transform3D;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.List;

@EpysiaComponent(name = "Nav Mesh Agent", category = "Navigation")
@RequiresComponent(Transform3D.class)
public final class NavMeshAgent extends Component {

    @Export(label = "Speed", min = 0.0f, max = 40.0f, step = 0.1f)
    private float speed = 3.5f;

    @Export(label = "Turn Speed", min = 0.0f, max = 40.0f, step = 0.1f)
    private float turnSpeed = 8.0f;

    @Export(label = "Arrival Distance", min = 0.01f, max = 5.0f, step = 0.05f)
    private float arrivalDistance = 0.35f;

    @Export(label = "Repath Interval", min = 0.0f, max = 10.0f, step = 0.1f)
    private float repathInterval = 0.5f;

    @Export(label = "Steer Transform")
    private boolean steerTransform = true;

    private final Vector3f destination = new Vector3f();
    private final Vector3f desiredVelocity = new Vector3f();
    private final List<Vector3f> corners = new ArrayList<>();
    private boolean hasDestination;
    private int corner;
    private float repathTimer;

    public NavMeshAgent setDestination(Vector3f target) {
        destination.set(target);
        hasDestination = true;
        repathTimer = 0.0f;
        return this;
    }

    public void stop() {
        hasDestination = false;
        corners.clear();
        desiredVelocity.zero();
    }

    public boolean hasDestination() {
        return hasDestination;
    }

    public Vector3f destination() {
        return destination;
    }

    public Vector3f desiredVelocity() {
        return desiredVelocity;
    }

    public boolean arrived() {
        return hasDestination && corners.isEmpty();
    }

    public float speed() {
        return speed;
    }

    public float turnSpeed() {
        return turnSpeed;
    }

    public float arrivalDistance() {
        return arrivalDistance;
    }

    public float repathInterval() {
        return repathInterval;
    }

    public boolean steerTransform() {
        return steerTransform;
    }

    List<Vector3f> corners() {
        return corners;
    }

    int corner() {
        return corner;
    }

    void setCorner(int index) {
        corner = index;
    }

    float repathTimer() {
        return repathTimer;
    }

    void setRepathTimer(float seconds) {
        repathTimer = seconds;
    }
}
