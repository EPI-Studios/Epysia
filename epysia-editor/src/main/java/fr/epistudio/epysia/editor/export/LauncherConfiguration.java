package fr.epistudio.epysia.editor.export;

import fr.epistudio.epysia.project.Project;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public final class LauncherConfiguration {

    private static final String CONTENT_DIRECTORY = "$APPDIR/content";
    private static final String SCRIPTS_DIRECTORY = "$APPDIR/content/.epysia/scripts-out";

    private final List<String> arguments;

    private LauncherConfiguration(List<String> arguments) {
        this.arguments = arguments;
    }

    public static LauncherConfiguration forGame(String sceneFileName, String title) {
        List<String> arguments = new ArrayList<>();
        arguments.add("--scene");
        arguments.add(CONTENT_DIRECTORY + "/" + Project.SCENES_DIRECTORY_NAME + "/" + sceneFileName);
        arguments.add("--project");
        arguments.add(CONTENT_DIRECTORY);
        arguments.add("--precompiled-scripts");
        arguments.add(SCRIPTS_DIRECTORY);
        arguments.add("--gpu");
        arguments.add("high");
        arguments.add("--title");
        arguments.add(title);
        return new LauncherConfiguration(arguments);
    }

    public void writeTo(Path templateConfig, Path targetConfig) throws IOException {
        String base = Files.readString(templateConfig);
        StringBuilder builder = new StringBuilder(base);
        if (!base.endsWith("\n")) {
            builder.append('\n');
        }
        builder.append("\n[ArgOptions]\n");
        for (String argument : arguments) {
            builder.append("arguments=").append(argument).append('\n');
        }
        Files.writeString(targetConfig, builder.toString());
        if (!targetConfig.equals(templateConfig)) {
            Files.deleteIfExists(templateConfig);
        }
    }
}
