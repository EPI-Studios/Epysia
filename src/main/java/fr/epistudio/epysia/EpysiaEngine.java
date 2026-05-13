package fr.epistudio.epysia;

import fr.epistudio.epysia.gameobjects.GameObject;
import fr.epistudio.epysia.scene.Scene;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Engine Class
 * Contain initialization and main loop of the engine
 * It will be used to update the game logic and render the game
 */
public class EpysiaEngine {

    private volatile AtomicLong time = new AtomicLong(0);
    private volatile AtomicBoolean running = new AtomicBoolean(true);

    private List<Scene> scenes = new ArrayList<>();

    public void init(){

        update();
    }

    private void update(){
        time.set(System.currentTimeMillis());
        while (running.get()) {
            long currentTime = System.currentTimeMillis();
            long deltaTime = currentTime - time.get();
            time.set(currentTime);

            for (Scene scene : scenes) {
                scene.update(deltaTime);
            }
        }
        stop();
    }

    private void stop(){
        System.exit(0);
    }

    public void addScene(Scene scene){
        scenes.add(scene);
    }

}