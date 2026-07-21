package fr.epistudio.epysia.render.mesh;

import org.junit.jupiter.api.Test;

import java.nio.ByteBuffer;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SkinnedVertexLayoutTest {

    private static MeshData skinnedTriangle() {
        return new MeshData(
                new float[]{0, 0, 0, 1, 0, 0, 0, 1, 0},
                new float[]{0, 0, 1, 0, 0, 1, 0, 0, 1},
                new float[]{0, 0, 1, 0, 0, 1},
                new float[]{1, 0, 0, 1, 0, 0, 1, 0, 0},
                new short[]{0, 1, 0, 0, 1, 0, 0, 0, 0, 0, 0, 0},
                new float[]{1, 0, 0, 0, 0.5f, 0.5f, 0, 0, 1, 0, 0, 0},
                new int[]{0, 1, 2},
                List.of());
    }

    @Test
    void skinnedVertexIsSixtyEightBytes() {
        ByteBuffer interleaved = MeshUploader.interleaveVertices(skinnedTriangle());
        assertEquals(3 * MeshShaderBindings.SKINNED_VERTEX_STRIDE, interleaved.remaining());
    }

    @Test
    void jointIndicesLandAfterTheElevenFloats() {
        ByteBuffer interleaved = MeshUploader.interleaveVertices(skinnedTriangle());
        assertEquals(0, interleaved.getShort(44));
        assertEquals(1, interleaved.getShort(46));
        assertEquals(1.0f, interleaved.getFloat(52));
        int secondVertex = MeshShaderBindings.SKINNED_VERTEX_STRIDE;
        assertEquals(1, interleaved.getShort(secondVertex + 44));
        assertEquals(0.5f, interleaved.getFloat(secondVertex + 52));
    }
}
