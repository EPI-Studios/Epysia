package fr.epistudio.epysia.components;

import fr.epistudio.epysia.components.transforms.Transform3D;
import fr.epistudio.epysia.exceptions.EpysiaException;
import org.joml.Quaternionf;
import org.joml.Vector3f;

@EpysiaComponent(name = "Directional Light", category = "Lighting")
public final class DirectionalLight extends Light {
    private static final Vector3f LOCAL_FORWARD = new Vector3f(0.0f, 0.0f, -1.0f);

    @Export(label = "Ambient", color = true)
    private final Vector3f ambient = new Vector3f(0.18f, 0.20f, 0.26f);
    private final Quaternionf scratchWorldRotation = new Quaternionf();

    @Override
    public DirectionalLight setColor(float red, float green, float blue) {
        super.setColor(red, green, blue);
        return this;
    }

    @Override
    public DirectionalLight setIntensity(float intensity) {
        super.setIntensity(intensity);
        return this;
    }

    public DirectionalLight setAmbient(float red, float green, float blue) {
        ambient.set(red, green, blue);
        return this;
    }

    public Vector3f ambient() {
        return ambient;
    }

    public Vector3f direction(Vector3f destination) {
        Transform3D transform = requireOwnerTransform();
        return transform.worldRotation(scratchWorldRotation).transform(LOCAL_FORWARD, destination);
    }

    private Transform3D requireOwnerTransform() {
        return owner()
                .orElseThrow(() -> new EpysiaException("DirectionalLight is not attached to a GameObject."))
                .getComponent(Transform3D.class)
                .orElseThrow(() -> new EpysiaException("DirectionalLight requires a Transform3D on the same GameObject."));
    }
}
