package fr.epistudio.epysia.components;

import fr.epistudio.epysia.components.transforms.Transform2D;
import fr.epistudio.epysia.gameobjects.GameObject;
import org.joml.Vector2f;
import org.joml.Vector2fc;

import java.util.Optional;

@EpysiaComponent(name = "Camera 2D", category = "Rendering",
        description = "Views the 2D scene from this object, with zoom, follow and limits.")
@RequiresComponent(Transform2D.class)
public final class Camera2D extends Component {

    @Export(label = "Active")
    private boolean active = true;
    @Export(label = "Size", min = 0.1f, step = 0.1f)
    private float size = 5.0f;
    @Export(label = "Zoom", min = 0.05f, max = 20.0f, step = 0.05f)
    private float zoom = 1.0f;
    @Export(label = "Follow target")
    private GameObject followTarget;
    @Export(label = "Follow smoothing", min = 0.0f, max = 0.99f, step = 0.01f)
    private float followSmoothing;
    @Export(label = "Limited")
    private boolean limited;
    @Export(label = "Limit min", step = 0.5f)
    private final Vector2f limitMin = new Vector2f(-50.0f, -50.0f);
    @Export(label = "Limit max", step = 0.5f)
    private final Vector2f limitMax = new Vector2f(50.0f, 50.0f);

    public boolean active() {
        return active;
    }

    public Camera2D setActive(boolean value) {
        active = value;
        return this;
    }

    public float size() {
        return size;
    }

    public Camera2D setSize(float value) {
        size = Math.max(0.1f, value);
        return this;
    }

    public float zoom() {
        return zoom;
    }

    public Camera2D setZoom(float value) {
        zoom = Math.max(0.05f, value);
        return this;
    }

    public float visibleHalfHeight() {
        return size / zoom;
    }

    public Optional<GameObject> followTarget() {
        return Optional.ofNullable(followTarget);
    }

    public Camera2D setFollowTarget(GameObject target) {
        followTarget = target;
        return this;
    }

    public float followSmoothing() {
        return followSmoothing;
    }

    public Camera2D setFollowSmoothing(float value) {
        followSmoothing = Math.clamp(value, 0.0f, 0.99f);
        return this;
    }

    public boolean limited() {
        return limited;
    }

    public Vector2fc limitMin() {
        return limitMin;
    }

    public Vector2fc limitMax() {
        return limitMax;
    }

    public Camera2D setLimits(float minimumX, float minimumY, float maximumX, float maximumY) {
        limitMin.set(minimumX, minimumY);
        limitMax.set(maximumX, maximumY);
        limited = true;
        return this;
    }

    public Camera2D clearLimits() {
        limited = false;
        return this;
    }
}
