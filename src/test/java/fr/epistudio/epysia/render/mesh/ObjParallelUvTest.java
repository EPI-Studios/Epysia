package fr.epistudio.epysia.render.mesh;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class ObjParallelUvTest {

    private static final float TOLERANCE = 1.0e-4f;

    private static float maximumU(float[] uvs) {
        float maximum = 0.0f;
        for (int index = 0; index < uvs.length; index += 2) {
            maximum = Math.max(maximum, uvs[index]);
        }
        return maximum;
    }

    @Test
    void facesWithoutSlashesTakeTheTextureIndexFromTheVertexIndex() {
        String source = """
                v 1 0 1
                v -1 0 1
                v -1 0 -1
                v 1 0 -1
                vt 20 20
                vt 0 20
                vt 0 0
                vt 20 0
                f 1 2 3 4
                """;

        float[] uvs = ObjMesh.parseDetailedFromSource(source).mesh().uvs();

        assertEquals(20.0f, maximumU(uvs), TOLERANCE,
                "a vt list parallel to the v list must be used when faces carry no slash");
    }

    @Test
    void explicitTextureIndicesStillWin() {
        String source = """
                v 1 0 1
                v -1 0 1
                v -1 0 -1
                vt 5 5
                vt 0 5
                vt 0 0
                f 1/3 2/3 3/3
                """;

        float[] uvs = ObjMesh.parseDetailedFromSource(source).mesh().uvs();

        assertEquals(0.0f, maximumU(uvs), TOLERANCE);
    }

    @Test
    void aMismatchedTextureListIsIgnoredRatherThanMisassigned() {
        String source = """
                v 1 0 1
                v -1 0 1
                v -1 0 -1
                vt 7 7
                f 1 2 3
                """;

        float[] uvs = ObjMesh.parseDetailedFromSource(source).mesh().uvs();

        assertEquals(0.0f, maximumU(uvs), TOLERANCE,
                "with fewer vt than v the parallel convention does not hold");
    }

    @Test
    void meshesWithoutTextureCoordinatesStillLoad() {
        String source = """
                v 1 0 1
                v -1 0 1
                v -1 0 -1
                f 1 2 3
                """;

        assertTrue(ObjMesh.parseDetailedFromSource(source).mesh().positions().length > 0);
    }
}
