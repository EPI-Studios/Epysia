package fr.epistudio.epysia.render.mesh;

import fr.epistudio.epysia.logging.Logger;
import fr.epistudio.epysia.render.shader.ShaderLoader;
import fr.epistudio.epysia.render.shader.SurfaceShaderComposer;

import java.util.HashMap;
import java.util.Map;

final class SurfaceTimeDependence {

    private final ShaderLoader shaderLoader;
    private final Logger logger;
    private final Map<String, Boolean> animatedBySurfacePath = new HashMap<>();

    SurfaceTimeDependence(ShaderLoader shaderLoader, Logger logger) {
        this.shaderLoader = shaderLoader;
        this.logger = logger;
    }

    boolean animatesShadow(String surfacePath) {
        if (surfacePath.isEmpty()) {
            return false;
        }
        return animatedBySurfacePath.computeIfAbsent(surfacePath, this::inspect);
    }

    void clear() {
        animatedBySurfacePath.clear();
    }

    private boolean inspect(String surfacePath) {
        try {
            boolean animated = SurfaceShaderComposer.shadowVertexUsesTime(shaderLoader.load(surfacePath));
            logger.info("Shadow caching: surface shader '" + surfacePath + "' is "
                    + (animated ? "time animated, its shadow targets always re-render" : "time independent"));
            return animated;
        } catch (RuntimeException failure) {
            logger.error("Shadow caching: cannot inspect '" + surfacePath
                    + "', assuming it is time animated", failure);
            return true;
        }
    }
}
