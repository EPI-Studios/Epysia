package fr.epistudio.epysia.scene;

import fr.epistudio.epysia.gameobjects.GameObject;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.List;

public final class Scene implements IScene {

    private final String name;
    private final List<GameObject> gameObjects = new ArrayList<>();
    private final List<GameObject> gameObjectsView = Collections.unmodifiableList(gameObjects);
    private final Deque<GameObject> pendingAdditions = new ArrayDeque<>();
    private final Deque<GameObject> pendingRemovals = new ArrayDeque<>();

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
        while (!pendingRemovals.isEmpty()) {
            gameObjects.remove(pendingRemovals.poll());
        }
        while (!pendingAdditions.isEmpty()) {
            gameObjects.add(pendingAdditions.poll());
        }
    }
}
