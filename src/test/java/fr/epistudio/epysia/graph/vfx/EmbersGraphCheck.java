package fr.epistudio.epysia.graph.vfx;

import fr.epistudio.epysia.graph.GraphAsset;
import fr.epistudio.epysia.graph.GraphJsonCodec;
import fr.epistudio.epysia.graph.vfx.VfxGraphCompiler.VfxCompiledSources;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EmbersGraphCheck {

    @Test
    void embersExampleCompiles() throws Exception {
        String json;
        try (InputStream graph = EmbersGraphCheck.class.getResourceAsStream("/vfx/Embers.epygraph")) {
            json = new String(graph.readAllBytes(), StandardCharsets.UTF_8);
        }
        GraphAsset asset = new GraphJsonCodec().read(json);
        String common;
        try (InputStream stream = EmbersGraphCheck.class
                .getResourceAsStream("/shaders/vfx/particle_common.glsl")) {
            common = new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        }
        VfxCompiledSources sources = new VfxGraphCompiler(common).compile(asset, "Embers.epygraph");
        assertEquals(180.0f, sources.spawnRatePerSecond());
        assertTrue(sources.spawnCompute().contains("32.000000"));
        assertTrue(sources.spawnCompute().contains("* 3.200000"));
        assertTrue(sources.spawnCompute().contains("randomRange(1.200000, 3.000000"));
        assertTrue(sources.spawnCompute().contains("vec4(1.000000, 0.420000, 0.080000, 1.000000)"));
        assertTrue(sources.fragmentBody().contains("0.900000"));
        assertTrue(sources.fragmentBody().contains("3.200000"));
    }
}
