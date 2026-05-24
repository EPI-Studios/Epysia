package fr.epistudio.epysia.scene;

import fr.epistudio.epysia.gameobjects.GameObject;

import java.util.List;

public interface IScene {

    String name();

    List<GameObject> gameObjects();

    void addGameObject(GameObject gameObject);

    void removeGameObject(GameObject gameObject);
}
