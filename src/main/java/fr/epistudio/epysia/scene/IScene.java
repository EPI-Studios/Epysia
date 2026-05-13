package fr.epistudio.epysia.scene;

import fr.epistudio.epysia.gameobjects.GameObject;

import java.util.List;

public interface IScene {

    String getName();
    List<GameObject> getGameObjects();

    void onInit();
    void onUpdate(float dt);
    void onDestroy();

    GameObject removeGameObject(GameObject gameObject);

}
