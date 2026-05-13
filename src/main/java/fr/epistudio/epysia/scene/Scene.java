package fr.epistudio.epysia.scene;

import fr.epistudio.epysia.gameobjects.GameObject;

import java.util.ArrayList;
import java.util.List;

public final class Scene implements IScene {

    private final String name;
    private final List<GameObject> gameObjects;

    public Scene(String name){
        this.name = name;
        this.gameObjects = new ArrayList<>();
    }

    @Override
    public String getName() {
        return this.name;
    }

    @Override
    public List<GameObject> getGameObjects() {
        return this.gameObjects;
    }


    public void init(){

        onInit();
    }
    @Override
    public void onInit() {

    }

    public void update(float dt){

        onUpdate(dt);
    }
    @Override
    public void onUpdate(float dt) {

    }

    public void destroy(){
        for (GameObject gameObject : gameObjects){
            gameObject.destroy();
        }
        onDestroy();
    }
    @Override
    public void onDestroy() {

    }

    @Override
    public GameObject removeGameObject(GameObject gameObject) {
        if (gameObjects.remove(gameObject)) {
            gameObject.destroy();
            return gameObject;
        }
        return null;
    }

    public void addGameObject(GameObject gameObject) {
        gameObjects.add(gameObject);
    }
}
