package fr.epistudio.epysia.render.mesh;

import fr.epistudio.epysia.render.backend.TextureHandle;
import fr.epistudio.epysia.render.material.Material;

import java.util.IdentityHashMap;
import java.util.Map;

final class MaterialStateDigests {

    private final Map<Material, MaterialStateSnapshot> snapshots = new IdentityHashMap<>();
    private final Map<Material, Long> revisions = new IdentityHashMap<>();
    private Material lastMaterial;
    private long lastRevision;
    private MaterialStateSnapshot lastSnapshot;
    private boolean sharedRevision =
            Boolean.parseBoolean(System.getProperty("epysia.render.sharedMaterialDigest", "true"));

    void beginFrame() {
        lastMaterial = null;
        lastSnapshot = null;
    }

    void setSharedRevision(boolean value) {
        sharedRevision = value;
        lastMaterial = null;
        lastSnapshot = null;
    }

    MaterialStateSnapshot snapshotFor(PerSubmesh perSubmesh, MaterialPipelineCache materialCache,
                                      SurfaceUniformBinder surfaceUniforms) {
        Material material = perSubmesh.material();
        long revision = revisionOf(perSubmesh);
        if (material == lastMaterial && revision == lastRevision && lastSnapshot != null) {
            return lastSnapshot;
        }
        MaterialStateSnapshot resolved = resolve(perSubmesh, revision, materialCache, surfaceUniforms);
        lastMaterial = material;
        lastRevision = revision;
        lastSnapshot = resolved;
        return resolved;
    }

    private MaterialStateSnapshot resolve(PerSubmesh perSubmesh, long revision,
                                          MaterialPipelineCache materialCache,
                                          SurfaceUniformBinder surfaceUniforms) {
        Material material = perSubmesh.material();
        Long cachedRevision = revisions.get(material);
        MaterialStateSnapshot cached = snapshots.get(material);
        if (cached != null && cachedRevision != null && cachedRevision == revision) {
            return cached;
        }
        MaterialStateSnapshot rebuilt = build(perSubmesh, materialCache, surfaceUniforms);
        snapshots.put(material, rebuilt);
        revisions.put(material, revision);
        return rebuilt;
    }

    private long revisionOf(PerSubmesh perSubmesh) {
        long revision = SurfaceUniformBinder.valueRevisionOf(perSubmesh.material());
        revision = ShadowSignatures.mix(revision, SurfaceUniformBinder.structureRevisionOf(perSubmesh.material()));
        if (!sharedRevision) {
            return ShadowSignatures.mix(revision, perSubmesh.litBindings().id());
        }
        revision = ShadowSignatures.mix(revision, System.identityHashCode(perSubmesh.classResources()));
        revision = ShadowSignatures.mix(revision, perSubmesh.shadowMasked() ? 1L : 0L);
        for (TextureHandle texture : perSubmesh.capturedTextures()) {
            revision = ShadowSignatures.mix(revision, texture.id());
        }
        return revision;
    }

    private static MaterialStateSnapshot build(PerSubmesh perSubmesh, MaterialPipelineCache materialCache,
                                               SurfaceUniformBinder surfaceUniforms) {
        Material material = perSubmesh.material();
        byte[] uniformBytes = materialCache.uniformSnapshotOf(material);
        byte[] surfaceBytes = surfaceUniforms.uniformSnapshotOf(material,
                perSubmesh.classResources().surfaceUniforms());
        long[] handles = handlesOf(perSubmesh, surfaceUniforms);
        long digest = MaterialStateSnapshot.digestOf(uniformBytes, surfaceBytes, handles);
        return new MaterialStateSnapshot(digest, uniformBytes, surfaceBytes, handles);
    }

    private static long[] handlesOf(PerSubmesh perSubmesh, SurfaceUniformBinder surfaceUniforms) {
        TextureHandle[] textures = perSubmesh.capturedTextures();
        long[] samplers = surfaceUniforms.samplerHandlesOf(perSubmesh.material(),
                perSubmesh.classResources().surfaceUniforms());
        long[] handles = new long[textures.length + samplers.length + 2];
        for (int index = 0; index < textures.length; index++) {
            handles[index] = textures[index].id();
        }
        System.arraycopy(samplers, 0, handles, textures.length, samplers.length);
        handles[handles.length - 2] = System.identityHashCode(perSubmesh.classResources());
        handles[handles.length - 1] = perSubmesh.shadowMasked() ? 1L : 0L;
        return handles;
    }
}
