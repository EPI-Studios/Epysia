package fr.epistudio.epysia.editor.scripts;

import fr.epistudio.epysia.project.Project;
import fr.epistudio.epysia.project.ProjectLibraries;
import fr.epistudio.epysia.scripting.compile.ScriptLanguages;

import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.CodeSource;
import java.util.Optional;
import java.util.stream.Stream;

public final class KotlinRuntimeInstaller {

    private static final String KOTLIN_EXTENSION = ".kt";

    private KotlinRuntimeInstaller() {
    }

    public static Optional<String> ensureStandardLibrary(Project project, ScriptLanguages languages) {
        if (!languages.sourceExtensions().contains(KOTLIN_EXTENSION)
                || !hasKotlinSources(project.scriptsDirectory())) {
            return Optional.empty();
        }
        Optional<Path> stdlib = standardLibraryArchive();
        if (stdlib.isEmpty()) {
            return Optional.of("Kotlin sources found but the Kotlin standard library jar could not be located.");
        }
        return installIfAbsent(project, stdlib.get());
    }

    private static Optional<String> installIfAbsent(Project project, Path stdlib) {
        Path target = project.librariesDirectory().resolve(stdlib.getFileName().toString());
        if (Files.isRegularFile(target)) {
            return Optional.empty();
        }
        try {
            Files.createDirectories(project.librariesDirectory());
            Files.copy(stdlib, target, StandardCopyOption.REPLACE_EXISTING);
            return Optional.of("Installed " + target.getFileName() + " into "
                    + Project.LIBRARIES_DIRECTORY_NAME + "/ so the exported game can run Kotlin scripts.");
        } catch (IOException error) {
            return Optional.of("Could not install the Kotlin standard library: " + error.getMessage());
        }
    }

    public static boolean hasKotlinSources(Path scriptsDirectory) {
        if (!Files.isDirectory(scriptsDirectory)) {
            return false;
        }
        try (Stream<Path> walk = Files.walk(scriptsDirectory)) {
            return walk.anyMatch(path -> path.getFileName().toString().endsWith(KOTLIN_EXTENSION));
        } catch (IOException error) {
            return false;
        }
    }

    private static Optional<Path> standardLibraryArchive() {
        CodeSource source = kotlin.Unit.class.getProtectionDomain().getCodeSource();
        if (source == null || source.getLocation() == null) {
            return Optional.empty();
        }
        try {
            Path path = Path.of(source.getLocation().toURI());
            return ProjectLibraries.isArchive(path) ? Optional.of(path) : Optional.empty();
        } catch (URISyntaxException error) {
            return Optional.empty();
        }
    }
}
