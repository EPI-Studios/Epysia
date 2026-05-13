package fr.epistudio.epysia;

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

    public void init(){


        update();
    }

    private void update(){
        time.set(System.currentTimeMillis());
        while (running.get()) {
            long currentTime = System.currentTimeMillis();
            long deltaTime = currentTime - time.get();
            time.set(currentTime);

            // Update game logic here using deltaTime

            // For example, you can print the delta time
            System.out.println("Delta Time: " + deltaTime + " ms");
        }
        stop();
    }

    private void stop(){
        System.exit(0);
    }

}