package fr.epistudio.epysia.components;

import fr.epistudio.epysia.components.transforms.Transform3D;
import fr.epistudio.epysia.exceptions.EpysiaException;
import org.joml.Quaternionf;
import org.joml.Vector3f;

@EpysiaComponent(name = "Spot Light", category = "Lighting")
public final class SpotLight extends Light {

    private static final Vector3f LOCAL_FORWARD = new Vector3f(0.0f, 0.0f, -1.0f);

    private final Quaternionf scratchWorldRotation = new Quaternionf();
    private float range = 10.0f;
    private float innerConeRadians = (float) Math.toRadians(15.0);
    private float outerConeRadians = (float) Math.toRadians(25.0);

    @Override
    public SpotLight setColor(float red, float green, float blue) {
        super.setColor(red, green, blue);
        return this;
    }

    @Override
    public SpotLight setIntensity(float intensity) {
        super.setIntensity(intensity);
        return this;
    }

    public SpotLight setRange(float range) {
        this.range = range;
        return this;
    }

    public SpotLight setConeDegrees(float innerDegrees, float outerDegrees) {
        this.innerConeRadians = (float) Math.toRadians(innerDegrees);
        this.outerConeRadians = (float) Math.toRadians(outerDegrees);
        return this;
    }

    public float range() {
        return range;
    }

    public float innerConeCosine() {
        return (float) Math.cos(innerConeRadians);
    }

    public float outerConeCosine() {
        return (float) Math.cos(outerConeRadians);
    }

    public Vector3f position(Vector3f destination) {
        return requireOwnerTransform().worldPosition(destination);
    }

    public Vector3f direction(Vector3f destination) {
        return requireOwnerTransform().worldRotation(scratchWorldRotation).transform(LOCAL_FORWARD, destination);
    }

    private Transform3D requireOwnerTransform() {
        return owner()
                .orElseThrow(() -> new EpysiaException("SpotLight is not attached to a GameObject."))
                .getComponent(Transform3D.class)
                .orElseThrow(() -> new EpysiaException("SpotLight requires a Transform3D on the same GameObject."));
    }
}
