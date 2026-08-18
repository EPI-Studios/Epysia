package fr.epistudio.epysia.editor.scripts;

import fr.epistudio.epysia.project.Project;
import fr.epistudio.epysia.scripting.compile.ScriptLanguage;
import fr.epistudio.epysia.scripting.compile.ScriptLanguages;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

public final class ScriptRuntimeInstaller {

    private ScriptRuntimeInstaller() {
    }

    public static List<String> ensureRuntimes(Project project, ScriptLanguages languages) {
        List<String> messages = new ArrayList<>();
        for (ScriptLanguage language : languages.languages()) {
            if (hasSources(project.scriptsDirectory(), language)) {
                language.runtimeArchives().forEach(archive -> install(project, archive, messages));
            }
        }
        return messages;
    }

    private static boolean hasSources(Path scriptsDirectory, ScriptLanguage language) {
        if (!Files.isDirectory(scriptsDirectory)) {
            return false;
        }
        try (Stream<Path> walk = Files.walk(scriptsDirectory)) {
            return walk.anyMatch(path -> matches(path, language));
        } catch (IOException unreadable) {
            return false;
        }
    }

    private static boolean matches(Path path, ScriptLanguage language) {
        String name = path.getFileName().toString();
        return language.sourceExtensions().stream().anyMatch(name::endsWith);
    }

    private static void install(Project project, Path archive, List<String> messages) {
        Path target = project.librariesDirectory().resolve(archive.getFileName().toString());
        if (Files.isRegularFile(target)) {
            return;
        }
        try {
            Files.createDirectories(project.librariesDirectory());
            Files.copy(archive, target, StandardCopyOption.REPLACE_EXISTING);
            messages.add("Installed " + target.getFileName() + " into "
                    + Project.LIBRARIES_DIRECTORY_NAME + "/ so the exported game can run these scripts.");
        } catch (IOException error) {
            messages.add("Could not install " + archive.getFileName() + ": " + error.getMessage());
        }
    }
}
