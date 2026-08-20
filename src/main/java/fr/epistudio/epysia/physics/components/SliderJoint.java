package fr.epistudio.epysia.physics.components;

import fr.epistudio.epysia.components.EpysiaComponent;
import fr.epistudio.epysia.components.Export;
import fr.epistudio.epysia.components.RequiresComponent;
import fr.epistudio.epysia.components.transforms.Transform3D;
import fr.epistudio.epysia.physics.api.JointDescriptor;
import fr.epistudio.epysia.physics.api.JointLimits;
import org.joml.Vector3f;
import org.joml.Vector3fc;

import java.util.Objects;

@EpysiaComponent(name = "Slider Joint", category = "Physics",
        description = "Movement locked to one axis, with optional limits and a motor.")
@RequiresComponent(Transform3D.class)
public final class SliderJoint extends JointComponent {
    @Export(label = "Axis")
    private final Vector3f axis = new Vector3f(1.0f, 0.0f, 0.0f);

    @Export(label = "Use Limit")
    private boolean useLimit = false;

    @Export(label = "Min Translation", step = 0.1f)
    private float minTranslation = -1.0f;

    @Export(label = "Max Translation", step = 0.1f)
    private float maxTranslation = 1.0f;

    @Export(label = "Use Motor")
    private boolean useMotor = false;

    @Export(label = "Motor Speed", step = 0.1f)
    private float motorSpeed = 0.0f;

    @Export(label = "Max Motor Force", min = 0.0f, step = 1.0f)
    private float maxMotorForce = 0.0f;

    @Override
    public JointDescriptor describe(Vector3fc worldAnchor, Vector3fc connectedWorldAnchor) {
        JointLimits limits = useLimit
                ? JointLimits.between(minTranslation, maxTranslation) : JointLimits.DISABLED;
        return new JointDescriptor.Slider(worldAnchor, new Vector3f(axis), limits,
                useMotor, motorSpeed, maxMotorForce);
    }

    @Override
    protected int settingsSignature() {
        return Objects.hash(axis.x(), axis.y(), axis.z(), useLimit, minTranslation, maxTranslation,
                useMotor, motorSpeed, maxMotorForce);
    }
}
