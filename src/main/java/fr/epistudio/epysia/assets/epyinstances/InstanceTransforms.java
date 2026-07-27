package fr.epistudio.epysia.assets.epyinstances;

import fr.epistudio.epysia.exceptions.EpysiaException;

public final class InstanceTransforms {

    private final float[] models;

    public InstanceTransforms(float[] models) {
        if (models.length % EpyInstancesFormat.FLOATS_PER_INSTANCE != 0) {
            throw new EpysiaException("Instance transforms must hold "
                    + EpyInstancesFormat.FLOATS_PER_INSTANCE + " floats per instance, got " + models.length);
        }
        this.models = models;
    }

    public float[] models() {
        return models;
    }

    public int count() {
        return models.length / EpyInstancesFormat.FLOATS_PER_INSTANCE;
    }
}
