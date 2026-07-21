package fr.epistudio.epysia.editor.importer;

import fr.epistudio.epysia.assets.epymesh.EpyMesh;
import fr.epistudio.epysia.assets.epymesh.EpyMeshReader;
import fr.epistudio.epysia.reflection.ComponentRegistry;
import fr.epistudio.epysia.reflection.ComponentScanner;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ExternalSampleDiagnostic {

    private static final Path SAMPLE = Path.of("/home/meek/gfzgezfez/Sample 1.glb");

    static boolean sampleExists() {
        return Files.exists(SAMPLE);
    }

    @Test
    @EnabledIf("sampleExists")
    void importsRealSampleWithPlaceholderTexture() throws Exception {
        Path output = Files.createTempDirectory("externalsamplediag");
        ComponentRegistry registry = new ComponentRegistry();
        registry.populateFromScan(ComponentScanner.scan());
        GltfImportResult result = GltfImporter.importFile(SAMPLE, output, registry);
        System.out.println("DIAG meshCount=" + result.meshFiles().size());
        System.out.println("DIAG materialCount=" + result.materialFiles().size());
        System.out.println("DIAG warnings=" + result.warnings());
        printUvRanges(result.meshFiles());
        assertFalse(result.meshFiles().isEmpty());
        assertTrue(result.warnings().stream().anyMatch(warning -> warning.contains("Missing texture")));
    }

    private static void printUvRanges(List<Path> meshFiles) {
        for (Path meshFile : meshFiles) {
            EpyMesh decoded = EpyMeshReader.readFile(meshFile);
            float[] uvs = decoded.mesh().uvs();
            System.out.println("DIAG mesh " + meshFile.getFileName() + " " + describeUvRange(uvs));
        }
    }

    private static String describeUvRange(float[] uvs) {
        if (uvs.length == 0) {
            return "uvMin=[none] uvMax=[none]";
        }
        float minU = Float.POSITIVE_INFINITY;
        float minV = Float.POSITIVE_INFINITY;
        float maxU = Float.NEGATIVE_INFINITY;
        float maxV = Float.NEGATIVE_INFINITY;
        for (int index = 0; index + 1 < uvs.length; index += 2) {
            minU = Math.min(minU, uvs[index]);
            maxU = Math.max(maxU, uvs[index]);
            minV = Math.min(minV, uvs[index + 1]);
            maxV = Math.max(maxV, uvs[index + 1]);
        }
        return "uvMin=[" + minU + ", " + minV + "] uvMax=[" + maxU + ", " + maxV + "]";
    }
}
