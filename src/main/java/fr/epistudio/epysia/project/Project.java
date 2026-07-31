package fr.epistudio.epysia.project;

import fr.epistudio.epysia.assets.AssetLocator;

import java.nio.file.Path;

public record Project(String name, Path rootDirectory, String engineVersion, long lastOpenedMillis) {

    public static final String MARKER_FILENAME = "epysia.project";
    public static final String SCENES_DIRECTORY_NAME = "scenes";
    public static final String SCRIPTS_DIRECTORY_NAME = "scripts";
    public static final String LIBRARIES_DIRECTORY_NAME = "libs";
    public static final String COMPILED_SCRIPTS_DIRECTORY_NAME = ".epysia/scripts-out";
    public static final String LIBRARIES_CACHE_DIRECTORY_NAME = ".epysia/libs-cache";
    public static final String DEPENDENCIES_FILENAME = "dependencies.txt";
    public static final String DEFAULT_SCENE_NAME = "main";
    public static final String SCENE_EXTENSION = ".epyscene";

    public AssetLocator locator() {
        return AssetLocator.forProject(rootDirectory);
    }

    public Path markerFile() {
        return rootDirectory.resolve(MARKER_FILENAME);
    }

    public Path scenesDirectory() {
        return rootDirectory.resolve(SCENES_DIRECTORY_NAME);
    }

    public Path scriptsDirectory() {
        return rootDirectory.resolve(SCRIPTS_DIRECTORY_NAME);
    }

    public Path librariesDirectory() {
        return rootDirectory.resolve(LIBRARIES_DIRECTORY_NAME);
    }

    public Path compiledScriptsDirectory() {
        return rootDirectory.resolve(COMPILED_SCRIPTS_DIRECTORY_NAME);
    }

    public Path librariesCacheDirectory() {
        return rootDirectory.resolve(LIBRARIES_CACHE_DIRECTORY_NAME);
    }

    public Path dependenciesFile() {
        return librariesDirectory().resolve(DEPENDENCIES_FILENAME);
    }

    public ProjectLibraries libraries() {
        return ProjectLibraries.forProjectRoot(rootDirectory);
    }

    public ProjectDependencies dependencies() {
        return ProjectDependencies.read(dependenciesFile());
    }

    public Path defaultScenePath() {
        return scenesDirectory().resolve(DEFAULT_SCENE_NAME + SCENE_EXTENSION);
    }

    public Project withLastOpenedNow() {
        return new Project(name, rootDirectory, engineVersion, System.currentTimeMillis());
    }
}
