package fr.epistudio.epysia.editor.assets;

import fr.epistudio.epysia.animation.Clip;
import fr.epistudio.epysia.assets.epyclip.EpyClipWriter;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ClipCatalogTest {

    @Test
    void filtersBySkeletonChecksum(@TempDir Path directory) throws IOException {
        writeClip(directory.resolve("walk.epyclip"), "walk", 100L);
        writeClip(directory.resolve("idle.epyclip"), "idle", 200L);
        ClipCatalog catalog = new ClipCatalog(directory);
        List<ClipCatalog.ClipEntry> matching = catalog.matching(100L);
        assertEquals(1, matching.size());
        assertEquals("walk", matching.get(0).name());
    }

    @Test
    void staleModificationTimeInvalidatesCache(@TempDir Path directory) throws IOException {
        Path clipPath = directory.resolve("clip.epyclip");
        writeClip(clipPath, "first", 100L);
        ClipCatalog catalog = new ClipCatalog(directory);
        assertEquals(1, catalog.matching(100L).size());
        writeClip(clipPath, "second", 300L);
        Files.setLastModifiedTime(clipPath, FileTime.fromMillis(System.currentTimeMillis() + 10_000L));
        assertEquals(0, catalog.matching(100L).size());
        assertEquals(1, catalog.matching(300L).size());
    }

    private static void writeClip(Path path, String name, long skeletonChecksum) {
        EpyClipWriter.writeToFile(path, new Clip(name, 1.0f, skeletonChecksum, List.of()));
    }
}
