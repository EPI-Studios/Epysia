package fr.epistudio.epysia.project;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class ProjectLibrariesTest {

    @Test
    void missingDirectoryYieldsNoLibraries(@TempDir Path root) {
        assertTrue(ProjectLibraries.in(root.resolve("libs")).isEmpty());
    }

    @Test
    void archivesAreSortedByFileNameAndNonArchivesIgnored(@TempDir Path root) throws IOException {
        Path libraries = Files.createDirectory(root.resolve("libs"));
        Files.createFile(libraries.resolve("zebra.jar"));
        Files.createFile(libraries.resolve("alpha.jar"));
        Files.createFile(libraries.resolve("notes.txt"));
        Files.createDirectory(libraries.resolve("nested.jar"));

        ProjectLibraries resolved = ProjectLibraries.in(libraries);

        assertEquals(2, resolved.archives().size());
        assertEquals("alpha.jar", resolved.archives().get(0).getFileName().toString());
        assertEquals("zebra.jar", resolved.archives().get(1).getFileName().toString());
    }

    @Test
    void classpathSuffixStartsWithSeparatorSoItAppendsCleanly(@TempDir Path root) throws IOException {
        Path libraries = Files.createDirectory(root.resolve("libs"));
        Files.createFile(libraries.resolve("alpha.jar"));

        String suffix = ProjectLibraries.in(libraries).classpathSuffix();

        assertTrue(suffix.startsWith(File.pathSeparator));
        assertTrue(suffix.endsWith("alpha.jar"));
    }
}
