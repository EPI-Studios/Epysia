package fr.epistudio.epysia.editor.export;

import fr.epistudio.epysia.project.Project;
import fr.epistudio.epysia.project.ProjectLibraries;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class ExportLibrariesTest {

    @Test
    void librariesDirectoryIsNotExcludedFromExport(@TempDir Path root) throws IOException {
        Path libraries = Files.createDirectories(root.resolve(Project.LIBRARIES_DIRECTORY_NAME));
        Files.createFile(libraries.resolve("alpha.jar"));
        Project project = new Project("game", root, "test", 0L);

        assertEquals(1, ProjectLibraries.in(project.librariesDirectory()).archives().size());
        assertFalse(GameExporter.excludesDirectory(Project.LIBRARIES_DIRECTORY_NAME));
        assertTrue(GameExporter.excludesDirectory(Project.SCRIPTS_DIRECTORY_NAME));
    }
}
