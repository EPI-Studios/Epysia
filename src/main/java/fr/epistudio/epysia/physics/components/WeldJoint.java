package fr.epistudio.epysia.physics.components;

import fr.epistudio.epysia.components.EpysiaComponent;
import fr.epistudio.epysia.components.Export;
import fr.epistudio.epysia.components.RequiresComponent;
import fr.epistudio.epysia.components.transforms.Transform3D;
import fr.epistudio.epysia.physics.api.JointDescriptor;
import org.joml.Vector3fc;

import java.util.Objects;

@EpysiaComponent(name = "Weld Joint", category = "Physics",
        description = "Locks two bodies together, rigidly or with some give.")
@RequiresComponent(Transform3D.class)
public final class WeldJoint extends JointComponent {
    @Export(label = "Linear Hertz", min = 0.0f, step = 0.1f)
    private float linearHertz = 0.0f;

    @Export(label = "Linear Damping Ratio", min = 0.0f, step = 0.05f)
    private float linearDampingRatio = 0.0f;

    @Export(label = "Angular Hertz", min = 0.0f, step = 0.1f)
    private float angularHertz = 0.0f;

    @Export(label = "Angular Damping Ratio", min = 0.0f, step = 0.05f)
    private float angularDampingRatio = 0.0f;

    @Override
    public JointDescriptor describe(Vector3fc worldAnchor, Vector3fc connectedWorldAnchor) {
        return new JointDescriptor.Weld(worldAnchor, linearHertz, linearDampingRatio,
                angularHertz, angularDampingRatio);
    }

    @Override
    protected int settingsSignature() {
        return Objects.hash(linearHertz, linearDampingRatio, angularHertz, angularDampingRatio);
    }
}
