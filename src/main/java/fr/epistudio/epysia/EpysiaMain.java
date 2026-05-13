package fr.epistudio.epysia;


import fr.epistudio.epysia.components.CountComponent;
import fr.epistudio.epysia.components.transforms.Transform2D;
import fr.epistudio.epysia.gameobjects.GameObject;
import fr.epistudio.epysia.scene.Scene;

/**
 * Main class of the engine, it will be used to launch the engine and create the window.
 */
public class EpysiaMain {

    /**
     * Main Methode
     */
    static void main() {
        EpysiaEngine engine = new EpysiaEngine();


        Scene exemple = new Scene("exemple");
        GameObject counter = new GameObject("exempleGO", new Transform2D());

        CountComponent countComponent = new CountComponent();
        counter.addComponent(countComponent);

        exemple.addGameObject(counter);

        engine.addScene(exemple);

        engine.init();

    }

}
