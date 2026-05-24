package fr.epistudio.epysia.editor.selection;

import fr.epistudio.epysia.gameobjects.GameObject;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;

public final class EditorSelectionBus {

    private final List<SelectionListener> listeners = new ArrayList<>();
    private Selection current = Selection.EMPTY;

    public Selection current() {
        return current;
    }

    public void setSingle(GameObject gameObject) {
        if (gameObject == null) {
            replace(Selection.EMPTY);
            return;
        }
        LinkedHashSet<GameObject> set = new LinkedHashSet<>();
        set.add(gameObject);
        replace(new Selection(gameObject, set));
    }

    public void setMultiple(GameObject primary, Collection<GameObject> all) {
        if (all == null || all.isEmpty()) {
            replace(Selection.EMPTY);
            return;
        }
        LinkedHashSet<GameObject> set = new LinkedHashSet<>(all);
        GameObject head = primary != null && set.contains(primary)
                ? primary
                : set.iterator().next();
        replace(new Selection(head, set));
    }

    public void add(GameObject gameObject) {
        if (gameObject == null || current.contains(gameObject)) {
            return;
        }
        LinkedHashSet<GameObject> set = new LinkedHashSet<>(current.all());
        set.add(gameObject);
        replace(new Selection(gameObject, set));
    }

    public void remove(GameObject gameObject) {
        if (gameObject == null || !current.contains(gameObject)) {
            return;
        }
        LinkedHashSet<GameObject> set = new LinkedHashSet<>(current.all());
        set.remove(gameObject);
        if (set.isEmpty()) {
            replace(Selection.EMPTY);
            return;
        }
        GameObject head = current.primary().orElse(null);
        if (head == null || !set.contains(head)) {
            head = set.iterator().next();
        }
        replace(new Selection(head, set));
    }

    public void toggle(GameObject gameObject) {
        if (gameObject == null) {
            return;
        }
        if (current.contains(gameObject)) {
            remove(gameObject);
        } else {
            add(gameObject);
        }
    }

    public void clear() {
        replace(Selection.EMPTY);
    }

    public void addListener(SelectionListener listener) {
        if (listener != null && !listeners.contains(listener)) {
            listeners.add(listener);
        }
    }

    public void removeListener(SelectionListener listener) {
        listeners.remove(listener);
    }

    private void replace(Selection next) {
        if (next.all().equals(current.all())
                && next.primary().orElse(null) == current.primary().orElse(null)) {
            return;
        }
        Selection previous = current;
        current = next;
        for (SelectionListener listener : new ArrayList<>(listeners)) {
            listener.onSelectionChanged(previous, current);
        }
    }
}
