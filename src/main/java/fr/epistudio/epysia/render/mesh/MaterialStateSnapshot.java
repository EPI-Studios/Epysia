package fr.epistudio.epysia.render.mesh;

import java.util.Arrays;

record MaterialStateSnapshot(long digest, byte[] uniformBytes, byte[] surfaceUniformBytes, long[] handles) {

    boolean matches(MaterialStateSnapshot other) {
        return digest == other.digest
                && Arrays.equals(uniformBytes, other.uniformBytes)
                && Arrays.equals(surfaceUniformBytes, other.surfaceUniformBytes)
                && Arrays.equals(handles, other.handles);
    }

    static long digestOf(byte[] uniformBytes, byte[] surfaceUniformBytes, long[] handles) {
        long digest = ShadowSignatures.seed();
        digest = ShadowSignatures.mix(digest, Arrays.hashCode(uniformBytes));
        digest = ShadowSignatures.mix(digest, Arrays.hashCode(surfaceUniformBytes));
        return ShadowSignatures.mix(digest, Arrays.hashCode(handles));
    }
}
