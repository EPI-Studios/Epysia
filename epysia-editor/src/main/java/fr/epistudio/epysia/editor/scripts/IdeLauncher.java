package fr.epistudio.epysia.editor.scripts;

import fr.epistudio.epysia.editor.assets.FileManagerReveal;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

public final class IdeLauncher {

    private static final List<String> COMMANDS = List.of("idea", "idea-ultimate", "idea-community");

    private IdeLauncher() {
    }

    public static Optional<String> open(Path projectRoot) {
        String target = projectRoot.toAbsolutePath().toString();
        for (String command : COMMANDS) {
            if (started(command, target)) {
                return Optional.empty();
            }
        }
        return FileManagerReveal.reveal(projectRoot);
    }

    private static boolean started(String command, String target) {
        try {
            new ProcessBuilder(command, target).start();
            return true;
        } catch (IOException unavailable) {
            return false;
        }
    }
}
