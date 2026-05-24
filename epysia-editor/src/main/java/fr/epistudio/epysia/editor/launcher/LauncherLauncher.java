package fr.epistudio.epysia.editor.launcher;

import fr.epistudio.epysia.editor.EditorLauncher;
import fr.epistudio.epysia.editor.project.Project;

import java.util.Optional;

public final class LauncherLauncher {

    private LauncherLauncher() {
    }

    public static void main(String[] arguments) {
        Optional<Project> chosen = new LauncherApplication().runUntilChosen();
        if (chosen.isEmpty()) {
            return;
        }
        EditorLauncher.runForProject(chosen.get());
    }
}
