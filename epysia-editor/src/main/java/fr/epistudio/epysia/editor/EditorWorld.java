package fr.epistudio.epysia.editor;

import fr.epistudio.epysia.editor.command.CommandContext;
import fr.epistudio.epysia.editor.command.EditorHistory;
import fr.epistudio.epysia.editor.play.PlayController;
import fr.epistudio.epysia.editor.selection.EditorSelectionBus;
import fr.epistudio.epysia.editor.selection.Selection;
import fr.epistudio.epysia.gameobjects.GameObject;
import fr.epistudio.epysia.scene.Scene;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;

public final class EditorWorld {

    private final Scene scene;
    private final EditorPlayRuntime playRuntime;
    private final GameObject hiddenEditorCamera;
    private final EditorSelectionBus selectionBus = new EditorSelectionBus();
    private final CommandContext commandContext;
    private final EditorHistory history;
    private PlayController playController;
    private float playElapsedSeconds;

    public EditorWorld(Scene scene, java.util.List<fr.epistudio.epysia.GameSystem> playModeSystems,
                       fr.epistudio.epysia.EngineServices engineServices, GameObject hiddenEditorCamera) {
        this.scene = scene;
        this.playRuntime = new EditorPlayRuntime(scene, playModeSystems, engineServices);
        this.hiddenEditorCamera = hiddenEditorCamera;
        this.commandContext = new CommandContext(this, selectionBus);
        this.history = new EditorHistory(commandContext);
    }

    public EditorPlayRuntime playRuntime() {
        return playRuntime;
    }

    public void setPlayController(PlayController controller) {
        this.playController = controller;
    }

    public Scene scene() {
        return scene;
    }

    public EditorSelectionBus selectionBus() {
        return selectionBus;
    }

    public EditorHistory history() {
        return history;
    }

    public CommandContext commandContext() {
        return commandContext;
    }

    public List<GameObject> objects() {
        List<GameObject> all = scene.gameObjects();
        List<GameObject> visible = new ArrayList<>(all.size());
        for (GameObject gameObject : all) {
            if (gameObject != hiddenEditorCamera) {
                visible.add(gameObject);
            }
        }
        return visible;
    }

    public int selectedIndex() {
        Optional<GameObject> primary = selectionBus.current().primary();
        if (primary.isEmpty()) {
            return -1;
        }
        List<GameObject> visible = objects();
        return visible.indexOf(primary.get());
    }

    public Optional<GameObject> selected() {
        return selectionBus.current().primary();
    }

    public List<GameObject> selectedAll() {
        return new ArrayList<>(selectionBus.current().all());
    }

    public List<Integer> selectedIndicesView() {
        List<GameObject> visible = objects();
        List<Integer> indices = new ArrayList<>();
        for (GameObject gameObject : selectionBus.current().all()) {
            int index = visible.indexOf(gameObject);
            if (index >= 0) {
                indices.add(index);
            }
        }
        return Collections.unmodifiableList(indices);
    }

    public void selectIndex(int index) {
        List<GameObject> visible = objects();
        if (index < 0 || index >= visible.size()) {
            selectionBus.clear();
            return;
        }
        selectionBus.setSingle(visible.get(index));
    }

    public void addToSelection(int index) {
        List<GameObject> visible = objects();
        if (index < 0 || index >= visible.size()) {
            return;
        }
        selectionBus.add(visible.get(index));
    }

    public void toggleSelection(int index) {
        List<GameObject> visible = objects();
        if (index < 0 || index >= visible.size()) {
            return;
        }
        selectionBus.toggle(visible.get(index));
    }

    public void selectRange(int fromIndex, int toIndex) {
        List<GameObject> visible = objects();
        int size = visible.size();
        if (size == 0) {
            return;
        }
        int anchor = fromIndex < 0 ? toIndex : fromIndex;
        int lo = Math.max(0, Math.min(anchor, toIndex));
        int hi = Math.min(size - 1, Math.max(anchor, toIndex));
        if (lo > hi) {
            return;
        }
        LinkedHashSet<GameObject> set = new LinkedHashSet<>();
        GameObject head = visible.get(Math.min(toIndex, size - 1));
        set.add(head);
        for (int index = lo; index <= hi; index++) {
            set.add(visible.get(index));
        }
        selectionBus.setMultiple(head, set);
    }

    public void clearSelection() {
        selectionBus.clear();
    }

    public void addGameObject(GameObject gameObject) {
        scene.addGameObject(gameObject);
        scene.advanceTick();
        selectionBus.setSingle(gameObject);
    }

    public void removeSelected() {
        List<GameObject> toRemove = selectedAll();
        if (toRemove.isEmpty()) {
            return;
        }
        for (GameObject gameObject : toRemove) {
            scene.removeGameObject(gameObject);
        }
        selectionBus.clear();
        scene.advanceTick();
    }

    public boolean isPlaying() {
        if (playController != null) {
            return playController.isPlaying();
        }
        return playRuntime.isPlaying();
    }

    public void togglePlay() {
        if (playController != null) {
            if (playController.isPlaying()) {
                playController.stop();
                playElapsedSeconds = 0.0f;
            } else {
                playController.play();
            }
            return;
        }
        if (playRuntime.isPlaying()) {
            playRuntime.stop();
            playElapsedSeconds = 0.0f;
        } else {
            playRuntime.play();
        }
    }

    public float playElapsedSeconds() {
        return playElapsedSeconds;
    }

    public void advancePlayClock(float deltaTimeSeconds) {
        if (playRuntime.isPlaying()) {
            playElapsedSeconds += deltaTimeSeconds;
        }
    }
}
