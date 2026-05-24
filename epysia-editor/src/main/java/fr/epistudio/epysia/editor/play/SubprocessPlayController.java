package fr.epistudio.epysia.editor.play;

import fr.epistudio.epysia.editor.EditorSceneHost;
import fr.epistudio.epysia.editor.project.Project;
import fr.epistudio.epysia.editor.serialization.SceneSerializer;
import fr.epistudio.epysia.gameobjects.GameObject;
import fr.epistudio.epysia.logging.Logger;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.function.Predicate;

public final class SubprocessPlayController implements PlayController {

    private final PlayProcessLauncher launcher;
    private final EditorSceneHost sceneHost;
    private final Project project;
    private final SceneSerializer serializer;
    private final Logger logger;

    public SubprocessPlayController(EditorSceneHost sceneHost, Project project, Logger logger) {
        this.sceneHost = sceneHost;
        this.project = project;
        this.logger = logger;
        this.launcher = new PlayProcessLauncher(logger);
        this.serializer = new SceneSerializer(sceneHost.components());
    }

    public PlayProcessLauncher launcher() {
        return launcher;
    }

    @Override
    public boolean isPlaying() {
        return launcher.isRunning();
    }

    @Override
    public void play() {
        if (launcher.isRunning()) {
            return;
        }
        Path scenePath = playSceneFile();
        try {
            Files.createDirectories(scenePath.getParent());
            GameObject editorCamera = sceneHost.editorCameraObject();
            Predicate<GameObject> filter = gameObject -> gameObject != editorCamera;
            serializer.save(sceneHost.scene(), scenePath, filter);
        } catch (IOException error) {
            logger.error("Failed to write play-mode scene to " + scenePath + ": " + error.getMessage());
            return;
        }
        launcher.launch(scenePath, project.name() + " — Play");
    }

    @Override
    public void stop() {
        launcher.stop();
    }

    private Path playSceneFile() {
        return project.rootDirectory().resolve(".epysia").resolve("playmode.epyscene");
    }
}
