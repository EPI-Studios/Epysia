package fr.epistudio.epysia.assets;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;

class LegacyAssetReferencesTest {

    @Test
    void anAbsolutePathInsideTheProjectBecomesAProjectUri(@TempDir Path root) throws IOException {
        Path texture = root.resolve("sprites").resolve("hero.png");
        Files.createDirectories(texture.getParent());
        Files.writeString(texture, "");
        AssetLocator locator = AssetLocator.forProject(root);
        assertEquals("res://sprites/hero.png",
                LegacyAssetReferences.interpretWithoutMigration(texture.toString(), locator).toString());
    }

    @Test
    void anAbsolutePathOutsideTheProjectStaysASystemPath(@TempDir Path root, @TempDir Path elsewhere) {
        Path texture = elsewhere.resolve("hero.png");
        AssetLocator locator = AssetLocator.forProject(root);
        assertEquals(AssetScheme.SYSTEM,
                LegacyAssetReferences.interpretWithoutMigration(texture.toString(), locator).scheme());
    }

    @Test
    void filterPrefixesAreStrippedFromTheStoredPath(@TempDir Path root) throws IOException {
        Path texture = root.resolve("hero.png");
        Files.writeString(texture, "");
        AssetLocator locator = AssetLocator.forProject(root);
        assertEquals("res://hero.png",
                LegacyAssetReferences.interpretWithoutMigration("point:" + texture, locator).toString());
    }

    @Test
    void aRelativePathWithNoProjectFileFallsBackToTheEngineRoot(@TempDir Path root) {
        AssetLocator locator = AssetLocator.forProject(root);
        assertEquals("engine://shaders/pbr.glsl",
                LegacyAssetReferences.interpretWithoutMigration("shaders/pbr.glsl", locator).toString());
    }
}
