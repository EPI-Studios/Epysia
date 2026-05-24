package fr.epistudio.epysia.editor.project;

import java.nio.file.Path;

public record Project(String name, Path rootDirectory, String engineVersion, long lastOpenedMillis) {

    public static final String MARKER_FILENAME = "epysia.project";
    public static final String SCENES_DIRECTORY_NAME = "scenes";
    public static final String SCRIPTS_DIRECTORY_NAME = "scripts";
    public static final String DEFAULT_SCENE_NAME = "main";
    public static final String SCENE_EXTENSION = ".epyscene";

    public Path markerFile() {
        return rootDirectory.resolve(MARKER_FILENAME);
    }

    public Path scenesDirectory() {
        return rootDirectory.resolve(SCENES_DIRECTORY_NAME);
    }

    public Path scriptsDirectory() {
        return rootDirectory.resolve(SCRIPTS_DIRECTORY_NAME);
    }

    public Path defaultScenePath() {
        return scenesDirectory().resolve(DEFAULT_SCENE_NAME + SCENE_EXTENSION);
    }

    public Project withLastOpenedNow() {
        return new Project(name, rootDirectory, engineVersion, System.currentTimeMillis());
    }
}
