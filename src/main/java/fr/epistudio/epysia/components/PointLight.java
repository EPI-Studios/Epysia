package fr.epistudio.epysia.components;

import fr.epistudio.epysia.components.transforms.Transform3D;
import fr.epistudio.epysia.exceptions.EpysiaException;
import org.joml.Vector3f;

@EpysiaComponent(name = "Point Light", category = "Lighting")
public final class PointLight extends Light {

    @Export(label = "Range", min = 0.0f, max = 200.0f, step = 0.1f)
    private float range = 10.0f;

    @Override
    public PointLight setColor(float red, float green, float blue) {
        super.setColor(red, green, blue);
        return this;
    }

    @Override
    public PointLight setIntensity(float intensity) {
        super.setIntensity(intensity);
        return this;
    }

    public PointLight setRange(float range) {
        this.range = range;
        return this;
    }

    public float range() {
        return range;
    }

    public Vector3f position(Vector3f destination) {
        Transform3D transform = owner()
                .orElseThrow(() -> new EpysiaException("PointLight is not attached to a GameObject."))
                .getComponent(Transform3D.class)
                .orElseThrow(() -> new EpysiaException("PointLight requires a Transform3D on the same GameObject."));
        return destination.set(transform.position());
    }
}
