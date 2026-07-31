package fr.epistudio.epysia.physics.components;

import fr.epistudio.epysia.EngineServices;
import fr.epistudio.epysia.components.Component;
import fr.epistudio.epysia.components.Export;
import fr.epistudio.epysia.physics.api.ShapeDescriptor;
import org.joml.Vector3f;
import org.joml.Vector3fc;

import java.util.Optional;

public abstract class Collider extends Component {

    @Export(label = "Offset", step = 0.05f)
    private final Vector3f offset = new Vector3f(0.0f, 0.0f, 0.0f);

    @Export(label = "Is Trigger")
    private boolean isTrigger = false;

    @Export(label = "Layer", min = 0, max = 15, step = 1)
    private int collisionLayer = 0;

    @Export(label = "Material")
    private String material = "";

    private PhysicsMaterial resolvedMaterial = PhysicsMaterial.DEFAULT;
    private boolean registered;

    public abstract ShapeDescriptor shape();

    public boolean isRegistered() {
        return registered;
    }

    public void markRegistered() {
        this.registered = true;
    }

    public void clearRegistered() {
        this.registered = false;
    }

    public boolean requiresRebuild() {
        return false;
    }

    public Vector3fc offset() {
        return offset;
    }

    public boolean isTrigger() {
        return isTrigger;
    }

    public int collisionLayer() {
        return collisionLayer;
    }

    public String materialName() {
        return material;
    }

    public PhysicsMaterial resolvedMaterial() {
        return resolvedMaterial;
    }

    @Override
    public void onLoad(EngineServices services) {
        resolveMaterial(services);
    }

    protected final void resolveMaterial(EngineServices services) {
        if (material.isEmpty()) {
            resolvedMaterial = PhysicsMaterial.DEFAULT;
            return;
        }
        Optional<PhysicsMaterial> loaded = services.assets().resolve(PhysicsMaterial.class, material);
        resolvedMaterial = loaded.orElse(PhysicsMaterial.DEFAULT);
    }
}
