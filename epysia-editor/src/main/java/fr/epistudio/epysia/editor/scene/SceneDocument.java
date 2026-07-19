package fr.epistudio.epysia.editor.scene;

import fr.epistudio.epysia.editor.EditorSelection;
import fr.epistudio.epysia.editor.command.EditorHistory;
import fr.epistudio.epysia.scene.Scene;

import java.nio.file.Path;

public final class SceneDocument {

    private final Scene scene;
    private final EditorSelection selection;
    private final EditorHistory history;
    private Path filePath;
    private String name;
    private boolean dirty;

    public SceneDocument(Scene scene, EditorSelection selection, EditorHistory history, Path filePath, String name) {
        this.scene = scene;
        this.selection = selection;
        this.history = history;
        this.filePath = filePath;
        this.name = name;
        history.setOnChange(this::markDirty);
    }

    public Scene scene() {
        return scene;
    }

    public EditorSelection selection() {
        return selection;
    }

    public EditorHistory history() {
        return history;
    }

    public Path filePath() {
        return filePath;
    }

    public String name() {
        return name;
    }

    public boolean isDirty() {
        return dirty;
    }

    public void markDirty() {
        dirty = true;
    }

    public void markClean() {
        dirty = false;
    }

    public void renameTo(Path filePath, String name) {
        this.filePath = filePath;
        this.name = name;
    }
}
