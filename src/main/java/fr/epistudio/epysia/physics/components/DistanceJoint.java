package fr.epistudio.epysia.physics.components;

import fr.epistudio.epysia.components.EpysiaComponent;
import fr.epistudio.epysia.components.Export;
import fr.epistudio.epysia.components.RequiresComponent;
import fr.epistudio.epysia.components.transforms.Transform3D;
import fr.epistudio.epysia.physics.api.JointDescriptor;
import fr.epistudio.epysia.physics.api.JointLimits;
import org.joml.Vector3fc;

import java.util.Objects;

@EpysiaComponent(name = "Distance Joint", category = "Physics",
        description = "Holds two bodies a set distance apart, rigidly or on a spring.")
@RequiresComponent(Transform3D.class)
public final class DistanceJoint extends JointComponent {
    @Export(label = "Length", min = 0.0f, step = 0.1f)
    private float length = 1.0f;

    @Export(label = "Use Limit")
    private boolean useLimit = false;

    @Export(label = "Min Length", min = 0.0f, step = 0.1f)
    private float minLength = 0.5f;

    @Export(label = "Max Length", min = 0.0f, step = 0.1f)
    private float maxLength = 2.0f;

    @Export(label = "Use Spring")
    private boolean useSpring = false;

    @Export(label = "Spring Hertz", min = 0.0f, step = 0.1f)
    private float springHertz = 0.0f;

    @Export(label = "Spring Damping Ratio", min = 0.0f, step = 0.05f)
    private float springDampingRatio = 0.0f;

    @Override
    public JointDescriptor describe(Vector3fc worldAnchor, Vector3fc connectedWorldAnchor) {
        JointLimits limits = useLimit ? JointLimits.between(minLength, maxLength) : JointLimits.DISABLED;
        return new JointDescriptor.Distance(worldAnchor, connectedWorldAnchor, length, limits,
                useSpring, springHertz, springDampingRatio);
    }

    @Override
    protected int settingsSignature() {
        return Objects.hash(length, useLimit, minLength, maxLength, useSpring,
                springHertz, springDampingRatio);
    }
}
