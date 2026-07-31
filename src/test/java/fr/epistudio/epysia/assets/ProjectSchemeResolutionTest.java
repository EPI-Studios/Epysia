package fr.epistudio.epysia.assets;

import fr.epistudio.epysia.assets.source.AssetResolvers;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class ProjectSchemeResolutionTest {

    @AfterEach
    void detachProject() {
        AssetResolvers.useProjectRoot(null);
    }

    @Test
    void aProjectUriResolvesToTheFileOnDisk(@TempDir Path root) throws IOException {
        Path meshes = Files.createDirectories(root.resolve("meshes"));
        Files.writeString(meshes.resolve("quad.obj"), "v 0 0 0\n");
        AssetResolvers.useProjectRoot(root);

        AssetResolvers.ResolvedLocation location =
                AssetResolvers.forPath("res://meshes/quad.obj", "models/");

        assertEquals("quad.obj", location.leafName());
        assertTrue(location.source().orElseThrow().open().isPresent(),
                "res:// must reach the project file instead of being treated as a classpath path");
    }

    @Test
    void aProjectUriPointingAtAMissingFileResolvesToNothing(@TempDir Path root) {
        AssetResolvers.useProjectRoot(root);

        AssetResolvers.ResolvedLocation location =
                AssetResolvers.forPath("res://meshes/absent.obj", "models/");

        assertTrue(location.source().orElseThrow().open().isEmpty());
    }

    @Test
    void aProjectUriWithoutAnAttachedProjectFallsBackToTheClasspath() {
        AssetResolvers.ResolvedLocation location =
                AssetResolvers.forPath("res://meshes/quad.obj", "models/");

        assertTrue(location.source().orElseThrow().path().startsWith("models/"),
                "with no project open the old classpath behaviour must be preserved");
    }

    @Test
    void plainRelativePathsStillGoThroughTheClasspathRoot(@TempDir Path root) {
        AssetResolvers.useProjectRoot(root);

        AssetResolvers.ResolvedLocation location = AssetResolvers.forPath("cube.obj", "models/");

        assertEquals("cube.obj", location.leafName());
    }
}
