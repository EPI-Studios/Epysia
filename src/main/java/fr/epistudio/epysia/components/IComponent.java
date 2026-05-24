package fr.epistudio.epysia.components;

import fr.epistudio.epysia.gameobjects.GameObject;

import java.util.Optional;

public interface IComponent {

    void attachTo(GameObject gameObject);

    Optional<GameObject> owner();
}
