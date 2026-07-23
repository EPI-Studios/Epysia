package fr.epistudio.epysia.scene;

import fr.epistudio.epysia.components.transforms.Transform3D;
import fr.epistudio.epysia.gameobjects.GameObject;
import fr.epistudio.epysia.render.postfx.PostEffectStack;
import org.joml.Vector3f;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public final class Scene implements IScene {

    private static final float DEFAULT_CLEAR_RED = 0.10f;
    private static final float DEFAULT_CLEAR_GREEN = 0.12f;
    private static final float DEFAULT_CLEAR_BLUE = 0.18f;

    private final String name;
    private final List<GameObject> gameObjects = new ArrayList<>();
    private final List<GameObject> gameObjectsView = Collections.unmodifiableList(gameObjects);
    private final Map<UUID, GameObject> gameObjectsById = new HashMap<>();
    private final Deque<GameObject> pendingAdditions = new ArrayDeque<>();
    private final Deque<GameObject> pendingRemovals = new ArrayDeque<>();
    private final List<GameObject> recentlyActivated = new ArrayList<>();
    private final List<GameObject> recentlyDeactivated = new ArrayList<>();
    private final PostEffectStack postEffects = new PostEffectStack();
    private final Vector3f clearColor = defaultClearColor();
    private long modificationCount;

    public Scene(String name) {
        this.name = name;
    }

    @Override
    public String name() {
        return name;
    }

    @Override
    public List<GameObject> gameObjects() {
        return gameObjectsView;
    }

    @Override
    public void addGameObject(GameObject gameObject) {
        pendingAdditions.add(gameObject);
    }

    @Override
    public void removeGameObject(GameObject gameObject) {
        pendingRemovals.add(gameObject);
    }

    public void advanceTick() {
        applyPendingRemovals();
        applyPendingAdditions();
    }

    private void applyPendingRemovals() {
        while (!pendingRemovals.isEmpty()) {
            removeSubtree(pendingRemovals.poll());
        }
    }

    private void removeSubtree(GameObject root) {
        if (!gameObjects.contains(root)) {
            return;
        }
        detachFromParent(root);
        for (GameObject member : subtreeOf(root)) {
            removeSingle(member);
        }
    }

    public List<GameObject> subtreeOf(GameObject root) {
        List<GameObject> subtree = new ArrayList<>();
        collectSubtree(root, subtree);
        return subtree;
    }

    private void collectSubtree(GameObject gameObject, List<GameObject> collected) {
        collected.add(gameObject);
        Transform3D transform = gameObject.getComponentOrNull(Transform3D.class);
        if (transform == null) {
            return;
        }
        for (Transform3D child : new ArrayList<>(transform.children())) {
            child.owner().ifPresent(childOwner -> collectSubtree(childOwner, collected));
        }
    }

    private void detachFromParent(GameObject gameObject) {
        Transform3D transform = gameObject.getComponentOrNull(Transform3D.class);
        if (transform != null) {
            transform.detachFromParent();
        }
    }

    private void removeSingle(GameObject removed) {
        if (gameObjects.remove(removed)) {
            gameObjectsById.remove(removed.id());
            removed.clearStructuralChangeListener();
            recentlyDeactivated.add(removed);
            modificationCount++;
        }
    }

    private void applyPendingAdditions() {
        while (!pendingAdditions.isEmpty()) {
            GameObject added = pendingAdditions.poll();
            gameObjects.add(added);
            gameObjectsById.put(added.id(), added);
            added.setStructuralChangeListener(this::recordStructuralChange);
            recentlyActivated.add(added);
            modificationCount++;
        }
    }

    public List<GameObject> drainRecentlyActivated() {
        if (recentlyActivated.isEmpty()) {
            return List.of();
        }
        List<GameObject> drained = List.copyOf(recentlyActivated);
        recentlyActivated.clear();
        return drained;
    }

    public List<GameObject> drainRecentlyDeactivated() {
        if (recentlyDeactivated.isEmpty()) {
            return List.of();
        }
        List<GameObject> drained = List.copyOf(recentlyDeactivated);
        recentlyDeactivated.clear();
        return drained;
    }

    private void recordStructuralChange() {
        modificationCount++;
    }

    public long modificationCount() {
        return modificationCount;
    }

    public PostEffectStack postEffects() {
        return postEffects;
    }

    public static Vector3f defaultClearColor() {
        return new Vector3f(DEFAULT_CLEAR_RED, DEFAULT_CLEAR_GREEN, DEFAULT_CLEAR_BLUE);
    }

    public Vector3f clearColor() {
        return clearColor;
    }

    public Scene setClearColor(float red, float green, float blue) {
        clearColor.set(red, green, blue);
        return this;
    }

    public Optional<GameObject> findById(UUID id) {
        return Optional.ofNullable(gameObjectsById.get(id));
    }

    public Optional<GameObject> findByName(String name) {
        if (name == null) {
            return Optional.empty();
        }
        for (GameObject gameObject : gameObjects) {
            if (name.equals(gameObject.name())) {
                return Optional.of(gameObject);
            }
        }
        return Optional.empty();
    }

    public List<GameObject> findByTag(String tag) {
        if (tag == null || tag.isEmpty()) {
            return List.of();
        }
        List<GameObject> matches = new ArrayList<>();
        for (GameObject gameObject : gameObjects) {
            if (tag.equals(gameObject.tag())) {
                matches.add(gameObject);
            }
        }
        return matches;
    }
}
