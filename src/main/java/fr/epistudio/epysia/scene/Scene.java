package fr.epistudio.epysia.scene;

import fr.epistudio.epysia.gameobjects.GameObject;
import fr.epistudio.epysia.render.postfx.PostEffectStack;

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

    private final String name;
    private final List<GameObject> gameObjects = new ArrayList<>();
    private final List<GameObject> gameObjectsView = Collections.unmodifiableList(gameObjects);
    private final Map<UUID, GameObject> gameObjectsById = new HashMap<>();
    private final Deque<GameObject> pendingAdditions = new ArrayDeque<>();
    private final Deque<GameObject> pendingRemovals = new ArrayDeque<>();
    private final PostEffectStack postEffects = new PostEffectStack();
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
            GameObject removed = pendingRemovals.poll();
            if (gameObjects.remove(removed)) {
                gameObjectsById.remove(removed.id());
                removed.clearStructuralChangeListener();
                modificationCount++;
            }
        }
    }

    private void applyPendingAdditions() {
        while (!pendingAdditions.isEmpty()) {
            GameObject added = pendingAdditions.poll();
            gameObjects.add(added);
            gameObjectsById.put(added.id(), added);
            added.setStructuralChangeListener(this::recordStructuralChange);
            modificationCount++;
        }
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
