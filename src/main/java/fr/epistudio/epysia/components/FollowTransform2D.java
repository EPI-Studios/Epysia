package fr.epistudio.epysia.components;

import fr.epistudio.epysia.components.transforms.Transform2D;
import fr.epistudio.epysia.gameobjects.GameObject;
import org.joml.Vector2f;

import java.util.Optional;

@EpysiaComponent(name = "Follow Transform 2D", category = "Transform")
@RequiresComponent(Transform2D.class)
public final class FollowTransform2D extends Component {

    @Export(label = "Target")
    private GameObject target;
    @Export(label = "Weight X", min = -4.0f, max = 4.0f, step = 0.01f)
    private float weightX = 1.0f;
    @Export(label = "Weight Y", min = -4.0f, max = 4.0f, step = 0.01f)
    private float weightY = 1.0f;
    @Export(label = "Smoothing", min = 0.0f, max = 1.0f, step = 0.01f)
    private float smoothing;

    private final transient Vector2f restPosition = new Vector2f();
    private transient boolean restCaptured;

    public Optional<GameObject> target() {
        return Optional.ofNullable(target);
    }

    public FollowTransform2D setTarget(GameObject target) {
        this.target = target;
        return this;
    }

    public float weightX() {
        return weightX;
    }

    public float weightY() {
        return weightY;
    }

    public FollowTransform2D setWeights(float x, float y) {
        weightX = x;
        weightY = y;
        return this;
    }

    public float smoothing() {
        return smoothing;
    }

    public FollowTransform2D setSmoothing(float smoothing) {
        this.smoothing = Math.clamp(smoothing, 0.0f, 1.0f);
        return this;
    }

    public void captureRest(Vector2f position) {
        if (!restCaptured) {
            restPosition.set(position);
            restCaptured = true;
        }
    }

    public Vector2f restPosition() {
        return restPosition;
    }

    public void releaseRest() {
        restCaptured = false;
    }
}
