package fr.epistudio.epysia.gameobjects;

import fr.epistudio.epysia.components.IComponent;

import java.util.Optional;

public interface IGameObject {

    String name();

    <T extends IComponent> Optional<T> getComponent(Class<T> componentClass);

    <T extends IComponent> T addComponent(T component);

    <T extends IComponent> Optional<T> removeComponent(Class<T> componentClass);
}
