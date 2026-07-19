package fr.epistudio.epysia.editor;

import fr.epistudio.epysia.gameobjects.GameObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;

public final class EditorSelection {

    private final List<GameObject> selected = new ArrayList<>();
    private final List<GameObject> selectedView = Collections.unmodifiableList(selected);
    private final List<Consumer<Optional<GameObject>>> listeners = new ArrayList<>();
    private GameObject primary;

    public void select(GameObject gameObject) {
        if (primary == gameObject && selected.size() == 1) {
            return;
        }
        selected.clear();
        selected.add(gameObject);
        primary = gameObject;
        notifyListeners();
    }

    public void toggle(GameObject gameObject) {
        if (selected.remove(gameObject)) {
            if (primary == gameObject) {
                primary = selected.isEmpty() ? null : selected.get(selected.size() - 1);
            }
        } else {
            selected.add(gameObject);
            primary = gameObject;
        }
        notifyListeners();
    }

    public void selectAll(List<GameObject> range, GameObject newPrimary) {
        selected.clear();
        selected.addAll(range);
        primary = selected.contains(newPrimary) ? newPrimary
                : (selected.isEmpty() ? null : selected.get(selected.size() - 1));
        notifyListeners();
    }

    public void deselect(GameObject gameObject) {
        if (!selected.remove(gameObject)) {
            return;
        }
        if (primary == gameObject) {
            primary = selected.isEmpty() ? null : selected.get(selected.size() - 1);
        }
        notifyListeners();
    }

    public void clear() {
        if (selected.isEmpty()) {
            return;
        }
        selected.clear();
        primary = null;
        notifyListeners();
    }

    public Optional<GameObject> get() {
        return Optional.ofNullable(primary);
    }

    public List<GameObject> all() {
        return selectedView;
    }

    public int count() {
        return selected.size();
    }

    public boolean isSelected(GameObject gameObject) {
        return selected.contains(gameObject);
    }

    public boolean isPrimary(GameObject gameObject) {
        return primary != null && primary == gameObject;
    }

    public void addListener(Consumer<Optional<GameObject>> listener) {
        listeners.add(listener);
    }

    private void notifyListeners() {
        Optional<GameObject> current = get();
        for (Consumer<Optional<GameObject>> listener : listeners) {
            listener.accept(current);
        }
    }
}
