package fr.epistudio.epysia.render.mesh;

import fr.epistudio.epysia.components.Light;
import fr.epistudio.epysia.components.PointLight;
import fr.epistudio.epysia.components.SpotLight;
import org.joml.FrustumIntersection;
import org.joml.Vector3f;

import java.util.List;

final class SceneRelevance implements BoundsHierarchy.BoxTest {
    private static final int FLOATS_PER_BOX = 6;

    private final FrustumIntersection cameraFrustum = new FrustumIntersection();
    private final FrustumIntersection[] cascadeFrusta =
            new FrustumIntersection[CascadedShadowMaps.CASCADE_COUNT];
    private final Vector3f scratchPosition = new Vector3f();

    private float[] lightBoxes = new float[0];
    private int lightBoxCount;
    private int activeCascades;

    SceneRelevance() {
        for (int cascade = 0; cascade < cascadeFrusta.length; cascade++) {
            cascadeFrusta[cascade] = new FrustumIntersection();
        }
    }

    void beginFrame(org.joml.Matrix4f cameraViewProjection, CascadedShadowMaps cascades,
                    List<Light> lights, boolean localShadowsActive) {
        cameraFrustum.set(cameraViewProjection);
        activeCascades = 0;
        if (cascades.cascadesActive()) {
            for (int cascade = 0; cascade < cascadeFrusta.length; cascade++) {
                cascadeFrusta[cascade].set(cascades.cascadeMatrix(cascade));
            }
            activeCascades = cascadeFrusta.length;
        }
        collectLightBoxes(lights, localShadowsActive);
    }

    private void collectLightBoxes(List<Light> lights, boolean localShadowsActive) {
        lightBoxCount = 0;
        if (!localShadowsActive) {
            return;
        }
        ensureLightCapacity(lights.size());
        for (Light light : lights) {
            if (!light.castShadows()) {
                continue;
            }
            appendLightBox(light);
        }
    }

    private void appendLightBox(Light light) {
        float range;
        if (light instanceof PointLight point) {
            range = point.range();
            point.position(scratchPosition);
        } else if (light instanceof SpotLight spot) {
            range = spot.range();
            spot.position(scratchPosition);
        } else {
            return;
        }
        int base = lightBoxCount * FLOATS_PER_BOX;
        lightBoxes[base] = scratchPosition.x - range;
        lightBoxes[base + 1] = scratchPosition.y - range;
        lightBoxes[base + 2] = scratchPosition.z - range;
        lightBoxes[base + 3] = scratchPosition.x + range;
        lightBoxes[base + 4] = scratchPosition.y + range;
        lightBoxes[base + 5] = scratchPosition.z + range;
        lightBoxCount++;
    }

    private void ensureLightCapacity(int lights) {
        if (lightBoxes.length < lights * FLOATS_PER_BOX) {
            lightBoxes = new float[lights * FLOATS_PER_BOX];
        }
    }

    @Override
    public boolean overlaps(float minX, float minY, float minZ, float maxX, float maxY, float maxZ) {
        return overlaps(true, minX, minY, minZ, maxX, maxY, maxZ);
    }

    boolean overlaps(boolean castsShadows, float minX, float minY, float minZ,
                     float maxX, float maxY, float maxZ) {
        if (cameraFrustum.testAab(minX, minY, minZ, maxX, maxY, maxZ)) {
            return true;
        }
        if (!castsShadows) {
            return false;
        }
        for (int cascade = 0; cascade < activeCascades; cascade++) {
            if (cascadeFrusta[cascade].testAab(minX, minY, minZ, maxX, maxY, maxZ)) {
                return true;
            }
        }
        return overlapsAnyLight(minX, minY, minZ, maxX, maxY, maxZ);
    }

    private boolean overlapsAnyLight(float minX, float minY, float minZ,
                                     float maxX, float maxY, float maxZ) {
        for (int box = 0; box < lightBoxCount; box++) {
            int base = box * FLOATS_PER_BOX;
            if (minX <= lightBoxes[base + 3] && maxX >= lightBoxes[base]
                    && minY <= lightBoxes[base + 4] && maxY >= lightBoxes[base + 1]
                    && minZ <= lightBoxes[base + 5] && maxZ >= lightBoxes[base + 2]) {
                return true;
            }
        }
        return false;
    }
}
