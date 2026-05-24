package fr.epistudio.epysia.components;

import org.joml.Vector3f;

public final class SpinComponent extends Component {

    private final Vector3f axis = new Vector3f(0.0f, 1.0f, 0.0f);
    private float radiansPerSecond = (float) Math.toRadians(60.0);

    public SpinComponent setAxis(float x, float y, float z) {
        axis.set(x, y, z).normalize();
        return this;
    }

    public SpinComponent setRadiansPerSecond(float speed) {
        this.radiansPerSecond = speed;
        return this;
    }

    public Vector3f axis() {
        return axis;
    }

    public float radiansPerSecond() {
        return radiansPerSecond;
    }
}
