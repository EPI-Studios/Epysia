package fr.epistudio.epysia.editor.selection;

import fr.epistudio.epysia.gameobjects.GameObject;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Optional;
import java.util.Set;

public final class Selection {

    public static final Selection EMPTY = new Selection(null, Collections.emptySet());

    private final GameObject primary;
    private final Set<GameObject> all;

    public Selection(GameObject primary, Set<GameObject> all) {
        this.primary = primary;
        this.all = Collections.unmodifiableSet(new LinkedHashSet<>(all));
    }

    public Optional<GameObject> primary() {
        return Optional.ofNullable(primary);
    }

    public Set<GameObject> all() {
        return all;
    }

    public boolean isEmpty() {
        return all.isEmpty();
    }

    public int size() {
        return all.size();
    }

    public boolean contains(GameObject gameObject) {
        return all.contains(gameObject);
    }
}
