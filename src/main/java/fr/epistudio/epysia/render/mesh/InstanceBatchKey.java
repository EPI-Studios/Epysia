package fr.epistudio.epysia.render.mesh;

final class InstanceBatchKey {

    private final long meshId;
    private final long materialDigest;

    InstanceBatchKey(long meshId, long materialDigest) {
        this.meshId = meshId;
        this.materialDigest = materialDigest;
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof InstanceBatchKey key
                && key.meshId == meshId
                && key.materialDigest == materialDigest;
    }

    @Override
    public int hashCode() {
        return Long.hashCode(meshId) * 31 + Long.hashCode(materialDigest);
    }
}
