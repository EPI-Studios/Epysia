package fr.epistudio.epysia.assets.loaders;

import fr.epistudio.epysia.render.material.LitMaterial;
import fr.epistudio.epysia.render.material.Material;
import fr.epistudio.epysia.scene.serialization.MaterialJsonCodec;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

class MaterialAssetLoaderTest {

    @Test
    void samePathReturnsSameInstance(@TempDir Path directory) throws Exception {
        LitMaterial material = new LitMaterial();
        material.setSurfaceShaderPath("shaders/dissolve.surf.glsl");
        Path file = directory.resolve("shared.epymaterial");
        Files.writeString(file, new MaterialJsonCodec().writeSingle(material));
        MaterialAssetLoader loader = new MaterialAssetLoader();
        Material first = loader.loadFromFile(file);
        Material second = loader.loadFromFile(file);
        assertSame(first, second);
        assertEquals("shaders/dissolve.surf.glsl", ((LitMaterial) first).surfaceShaderPath());
    }

    @Test
    void clampPrefixedRelativeTexturePathRebasesWithThePrefixPreserved(@TempDir Path directory) throws Exception {
        LitMaterial material = new LitMaterial();
        material.setTexturePath("albedo", "clamp:tex.png");
        Path file = directory.resolve("prefixed.epymaterial");
        Files.writeString(file, new MaterialJsonCodec().writeSingle(material));
        MaterialAssetLoader loader = new MaterialAssetLoader();
        Material loaded = loader.loadFromFile(file);
        String albedoPath = loaded.texturePath("albedo").orElseThrow();
        assertEquals("clamp:" + directory.toAbsolutePath().normalize().resolve("tex.png"), albedoPath);
    }
}
