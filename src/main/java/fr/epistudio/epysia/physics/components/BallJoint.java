package fr.epistudio.epysia.physics.components;

import fr.epistudio.epysia.components.EpysiaComponent;
import fr.epistudio.epysia.components.Export;
import fr.epistudio.epysia.components.RequiresComponent;
import fr.epistudio.epysia.components.transforms.Transform3D;
import fr.epistudio.epysia.physics.api.JointDescriptor;
import fr.epistudio.epysia.physics.api.JointLimits;
import org.joml.Vector3fc;

import java.util.Objects;

@EpysiaComponent(name = "Ball Joint", category = "Physics")
@RequiresComponent(Transform3D.class)
public final class BallJoint extends JointComponent {
    @Export(label = "Use Cone Limit")
    private boolean useConeLimit = false;

    @Export(label = "Cone Limit Degrees", min = 0.0f, step = 1.0f)
    private float coneLimitDegrees = 45.0f;

    @Export(label = "Use Twist Limit")
    private boolean useTwistLimit = false;

    @Export(label = "Min Twist Degrees", step = 1.0f)
    private float minTwistDegrees = -45.0f;

    @Export(label = "Max Twist Degrees", step = 1.0f)
    private float maxTwistDegrees = 45.0f;

    @Export(label = "Use Spring")
    private boolean useSpring = false;

    @Export(label = "Spring Hertz", min = 0.0f, step = 0.1f)
    private float springHertz = 0.0f;

    @Export(label = "Spring Damping Ratio", min = 0.0f, step = 0.05f)
    private float springDampingRatio = 0.0f;

    @Override
    public JointDescriptor describe(Vector3fc worldAnchor, Vector3fc connectedWorldAnchor) {
        JointLimits twist = useTwistLimit
                ? JointLimits.between((float) Math.toRadians(minTwistDegrees),
                        (float) Math.toRadians(maxTwistDegrees))
                : JointLimits.DISABLED;
        return new JointDescriptor.Ball(worldAnchor, useConeLimit,
                (float) Math.toRadians(coneLimitDegrees), twist,
                useSpring, springHertz, springDampingRatio);
    }

    @Override
    protected int settingsSignature() {
        return Objects.hash(useConeLimit, coneLimitDegrees, useTwistLimit, minTwistDegrees,
                maxTwistDegrees, useSpring, springHertz, springDampingRatio);
    }
}
