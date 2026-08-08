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

@EpysiaComponent(name = "Hinge Joint", category = "Physics")
@RequiresComponent(Transform3D.class)
public final class HingeJoint extends JointComponent {
    @Export(label = "Axis")
    private final Vector3f axis = new Vector3f(0.0f, 1.0f, 0.0f);

    @Export(label = "Use Limit")
    private boolean useLimit = false;

    @Export(label = "Min Angle Degrees", step = 1.0f)
    private float minAngleDegrees = -90.0f;

    @Export(label = "Max Angle Degrees", step = 1.0f)
    private float maxAngleDegrees = 90.0f;

    @Export(label = "Use Motor")
    private boolean useMotor = false;

    @Export(label = "Motor Speed", step = 0.1f)
    private float motorSpeed = 0.0f;

    @Export(label = "Max Motor Torque", min = 0.0f, step = 1.0f)
    private float maxMotorTorque = 0.0f;

    @Override
    public JointDescriptor describe(Vector3fc worldAnchor, Vector3fc connectedWorldAnchor) {
        JointLimits limits = useLimit
                ? JointLimits.between((float) Math.toRadians(minAngleDegrees),
                        (float) Math.toRadians(maxAngleDegrees))
                : JointLimits.DISABLED;
        return new JointDescriptor.Hinge(worldAnchor, new Vector3f(axis), limits,
                useMotor, motorSpeed, maxMotorTorque);
    }

    @Override
    protected int settingsSignature() {
        return Objects.hash(axis.x(), axis.y(), axis.z(), useLimit, minAngleDegrees, maxAngleDegrees,
                useMotor, motorSpeed, maxMotorTorque);
    }
}
