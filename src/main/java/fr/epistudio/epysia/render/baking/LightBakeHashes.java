package fr.epistudio.epysia.render.baking;

import fr.epistudio.epysia.components.DirectionalLight;
import fr.epistudio.epysia.components.Light;
import fr.epistudio.epysia.components.LightProbeVolume;
import fr.epistudio.epysia.components.MeshRenderer;
import fr.epistudio.epysia.components.transforms.Transform3D;
import fr.epistudio.epysia.gameobjects.GameObject;
import fr.epistudio.epysia.render.material.LitMaterial;
import fr.epistudio.epysia.render.material.Material;
import fr.epistudio.epysia.scene.Scene;
import org.joml.Matrix4f;
import org.joml.Vector3f;

public final class LightBakeHashes {

    private static final long SEED = 0xC0FFEE5EEDCAFE01L;

    private LightBakeHashes() {
    }

    public static long hashScene(Scene scene) {
        long hash = SEED;
        float[] matrixScratch = new float[16];
        Vector3f vectorScratch = new Vector3f();
        for (GameObject gameObject : scene.gameObjects()) {
            hash = mixGameObject(hash, gameObject, matrixScratch, vectorScratch);
        }
        return hash;
    }

    private static long mixGameObject(long hash, GameObject gameObject,
                                      float[] matrixScratch, Vector3f vectorScratch) {
        Transform3D transform = gameObject.getComponentOrNull(Transform3D.class);
        if (transform != null) {
            hash = mixMatrix(hash, transform.worldMatrix(), matrixScratch);
        }
        MeshRenderer renderer = gameObject.getComponentOrNull(MeshRenderer.class);
        if (renderer != null) {
            hash = mixRenderer(hash, renderer);
        }
        Light light = gameObject.getComponentOrNull(Light.class);
        if (light != null) {
            hash = mixLight(hash, light);
        }
        LightProbeVolume volume = gameObject.getComponentOrNull(LightProbeVolume.class);
        if (volume != null) {
            hash = mixVolume(hash, volume, vectorScratch);
        }
        return hash;
    }

    private static long mixRenderer(long hash, MeshRenderer renderer) {
        hash = mix(hash, renderer.mesh().map(mesh -> (long) mesh.submeshes().size()).orElse(0L));
        for (Material material : renderer.materials()) {
            hash = mixMaterial(hash, material);
        }
        return hash;
    }

    private static long mixMaterial(long hash, Material material) {
        if (!(material instanceof LitMaterial lit)) {
            return mix(hash, material.getClass().getName().hashCode());
        }
        hash = mixVector(hash, lit.baseColor);
        hash = mix(hash, Float.floatToRawIntBits(lit.emissiveStrength));
        hash = mix(hash, Float.floatToRawIntBits(lit.metallic));
        return mix(hash, Float.floatToRawIntBits(lit.roughness));
    }

    private static long mixLight(long hash, Light light) {
        hash = mixVector(hash, light.color());
        hash = mix(hash, Float.floatToRawIntBits(light.intensity()));
        if (light instanceof DirectionalLight directional) {
            hash = mixVector(hash, directional.ambient());
        }
        return hash;
    }

    private static long mixVolume(long hash, LightProbeVolume volume, Vector3f vectorScratch) {
        hash = mixVector(hash, volume.extents(vectorScratch));
        hash = mix(hash, volume.resolutionX());
        hash = mix(hash, volume.resolutionY());
        return mix(hash, volume.resolutionZ());
    }

    private static long mixMatrix(long hash, Matrix4f matrix, float[] scratch) {
        matrix.get(scratch);
        for (float value : scratch) {
            hash = mix(hash, Float.floatToRawIntBits(value));
        }
        return hash;
    }

    private static long mixVector(long hash, Vector3f vector) {
        hash = mix(hash, Float.floatToRawIntBits(vector.x));
        hash = mix(hash, Float.floatToRawIntBits(vector.y));
        return mix(hash, Float.floatToRawIntBits(vector.z));
    }

    private static long mix(long hash, long value) {
        long mixed = hash ^ (value + 0x9E3779B97F4A7C15L + (hash << 6) + (hash >>> 2));
        return Long.rotateLeft(mixed, 13) * 0x100000001B3L;
    }
}
