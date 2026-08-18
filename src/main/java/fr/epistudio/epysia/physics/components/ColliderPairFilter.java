package fr.epistudio.epysia.physics.components;

import fr.epistudio.epysia.components.EpysiaComponent;
import fr.epistudio.epysia.components.RequiresComponent;
import fr.epistudio.epysia.components.transforms.Transform3D;
import fr.epistudio.epysia.physics.api.JointDescriptor;
import org.joml.Vector3fc;

@EpysiaComponent(name = "Collider Pair Filter", category = "Physics",
        description = "Stops two bodies from colliding with each other while both stay solid.")
@RequiresComponent(Transform3D.class)
public final class ColliderPairFilter extends JointComponent {
    @Override
    public JointDescriptor describe(Vector3fc worldAnchor, Vector3fc connectedWorldAnchor) {
        return new JointDescriptor.CollisionFilter();
    }

    @Override
    protected int settingsSignature() {
        return 0;
    }
}
