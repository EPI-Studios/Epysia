package fr.epistudio.epysia.components;

import org.joml.Vector3f;

@EpysiaComponent(name = "Global Light 2D", category = "Rendering")
public final class GlobalLight2D extends Light2D {

    @Export(label = "Direction X", min = -1.0f, max = 1.0f, step = 0.01f)
    private float directionX = -0.4f;
    @Export(label = "Direction Y", min = -1.0f, max = 1.0f, step = 0.01f)
    private float directionY = -0.6f;
    @Export(label = "Direction Z", min = -1.0f, max = 1.0f, step = 0.01f)
    private float directionZ = -1.0f;
    @Export(label = "Ambient", min = 0.0f, max = 1.0f, step = 0.01f)
    private float ambient = 0.15f;

    public Vector3f direction(Vector3f target) {
        target.set(directionX, directionY, directionZ);
        if (target.lengthSquared() < 1.0e-6f) {
            target.set(0.0f, 0.0f, -1.0f);
        }
        return target.normalize();
    }

    public GlobalLight2D setDirection(float x, float y, float z) {
        directionX = x;
        directionY = y;
        directionZ = z;
        return this;
    }

    public float ambient() {
        return ambient;
    }

    public GlobalLight2D setAmbient(float ambient) {
        this.ambient = Math.max(0.0f, ambient);
        return this;
    }
}
