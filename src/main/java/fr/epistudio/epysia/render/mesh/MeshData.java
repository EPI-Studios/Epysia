package fr.epistudio.epysia.render.mesh;

import fr.epistudio.epysia.exceptions.EpysiaException;

import java.util.List;

public record MeshData(
        float[] positions,
        float[] normals,
        float[] uvs,
        float[] tangents,
        short[] jointIndices,
        float[] jointWeights,
        int[] indices,
        List<Submesh> submeshes
) {

    public static final int POSITION_COMPONENTS = 3;
    public static final int NORMAL_COMPONENTS = 3;
    public static final int UV_COMPONENTS = 2;
    public static final int TANGENT_COMPONENTS = 3;
    public static final int VERTEX_FLOAT_COUNT = POSITION_COMPONENTS + NORMAL_COMPONENTS + UV_COMPONENTS + TANGENT_COMPONENTS;
    public static final int INFLUENCES_PER_VERTEX = 4;

    public MeshData {
        if (positions.length % POSITION_COMPONENTS != 0) {
            throw new EpysiaException("MeshData positions array length must be a multiple of 3.");
        }
        if (normals.length != positions.length) {
            throw new EpysiaException("MeshData normals must match positions count.");
        }
        int vertexCount = positions.length / POSITION_COMPONENTS;
        if (uvs.length != 0 && uvs.length != vertexCount * UV_COMPONENTS) {
            throw new EpysiaException("MeshData uvs must be empty or match positions count.");
        }
        if (tangents.length != 0 && tangents.length != vertexCount * TANGENT_COMPONENTS) {
            throw new EpysiaException("MeshData tangents must be empty or match positions count.");
        }
        if (indices.length == 0) {
            throw new EpysiaException("MeshData must have at least one index.");
        }
        if (tangents.length == 0) {
            tangents = TangentCalculator.compute(positions, normals, uvs, indices);
        }
        if (jointIndices.length != jointWeights.length) {
            throw new EpysiaException("MeshData joint indices and weights must have equal length.");
        }
        if (jointIndices.length != 0 && jointIndices.length != vertexCount * INFLUENCES_PER_VERTEX) {
            throw new EpysiaException("MeshData skin arrays must be empty or vertexCount * 4.");
        }
        submeshes = submeshes.isEmpty()
                ? List.of(new Submesh(0, indices.length, 0))
                : List.copyOf(submeshes);
    }

    public int vertexCount() {
        return positions.length / POSITION_COMPONENTS;
    }

    public boolean hasSkin() {
        return jointIndices.length > 0;
    }
}
