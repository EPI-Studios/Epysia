package fr.epistudio.epysia.render.mesh;

import fr.epistudio.epysia.exceptions.EpysiaException;
import org.junit.jupiter.api.Test;

import java.nio.ByteBuffer;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ColoredVertexLayoutTest {

    private static final float[] POSITIONS = {0, 0, 0, 1, 0, 0, 0, 1, 0};
    private static final float[] NORMALS = {0, 0, 1, 0, 0, 1, 0, 0, 1};
    private static final float[] UVS = {0, 0, 1, 0, 0, 1};
    private static final float[] TANGENTS = {1, 0, 0, 1, 0, 0, 1, 0, 0};
    private static final float[] COLORS = {1, 0, 0, 1, 0, 1, 0, 1, 0, 0, 1, 0.5f};
    private static final int[] INDICES = {0, 1, 2};

    private static MeshData coloredTriangle() {
        return new MeshData(POSITIONS, NORMALS, UVS, TANGENTS, COLORS,
                new short[0], new float[0], INDICES, List.of());
    }

    private static MeshData skinnedColoredTriangle() {
        short[] jointIndices = {0, 1, 0, 0, 1, 0, 0, 0, 0, 0, 0, 0};
        float[] jointWeights = {1, 0, 0, 0, 0.5f, 0.5f, 0, 0, 1, 0, 0, 0};
        return new MeshData(POSITIONS, NORMALS, UVS, TANGENTS, COLORS,
                jointIndices, jointWeights, INDICES, List.of());
    }

    @Test
    void coloredVertexIsSixtyBytes() {
        ByteBuffer interleaved = MeshUploader.interleaveVertices(coloredTriangle());
        assertEquals(3 * 60, interleaved.remaining());
        assertEquals(60, MeshShaderBindings.COLORED_VERTEX_STRIDE);
    }

    @Test
    void colorsLandAfterTheElevenFloats() {
        ByteBuffer interleaved = MeshUploader.interleaveVertices(coloredTriangle());
        assertEquals(1.0f, interleaved.getFloat(44));
        assertEquals(0.0f, interleaved.getFloat(48));
        assertEquals(0.0f, interleaved.getFloat(52));
        assertEquals(1.0f, interleaved.getFloat(56));
        int secondVertex = MeshShaderBindings.COLORED_VERTEX_STRIDE;
        assertEquals(0.0f, interleaved.getFloat(secondVertex + 44));
        assertEquals(1.0f, interleaved.getFloat(secondVertex + 48));
    }

    @Test
    void skinnedColoredVertexIsEightyFourBytes() {
        ByteBuffer interleaved = MeshUploader.interleaveVertices(skinnedColoredTriangle());
        assertEquals(3 * 84, interleaved.remaining());
        assertEquals(84, MeshShaderBindings.SKINNED_COLORED_VERTEX_STRIDE);
    }

    @Test
    void colorsPrecedeSkinInfluencesWhenBothPresent() {
        ByteBuffer interleaved = MeshUploader.interleaveVertices(skinnedColoredTriangle());
        assertEquals(1.0f, interleaved.getFloat(44));
        assertEquals(0, interleaved.getShort(60));
        assertEquals(1, interleaved.getShort(62));
        assertEquals(1.0f, interleaved.getFloat(68));
        int secondVertex = MeshShaderBindings.SKINNED_COLORED_VERTEX_STRIDE;
        assertEquals(1, interleaved.getShort(secondVertex + 60));
        assertEquals(0.5f, interleaved.getFloat(secondVertex + 68));
    }

    @Test
    void rejectsVertexColorsOfWrongLength() {
        assertThrows(EpysiaException.class, () -> new MeshData(POSITIONS, NORMALS, UVS, TANGENTS,
                new float[]{1, 1, 1}, new short[0], new float[0], INDICES, List.of()));
    }

    @Test
    void reportsPresenceOfVertexColors() {
        assertTrue(coloredTriangle().hasVertexColors());
        assertFalse(new MeshData(POSITIONS, NORMALS, UVS, TANGENTS,
                new short[0], new float[0], INDICES, List.of()).hasVertexColors());
    }
}
