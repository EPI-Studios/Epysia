package fr.epistudio.epysia.render.volumetric;

import org.joml.Vector3fc;

public interface DensitySource {
    void advance(float deltaTimeSeconds);

    boolean consumeSeedRequest();

    Vector3fc seedPoint();

    Vector3fc growthRadius();

    int propagationDistance();

    boolean growing();
}
