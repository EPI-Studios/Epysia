package fr.epistudio.epysia.exceptions;

import fr.epistudio.epysia.components.IComponent;
import fr.epistudio.epysia.gameobjects.IGameObject;

public class ComponentPresentException extends ComponentException {
    public ComponentPresentException(IComponent component, IGameObject gameObject, String message) {
        String fullMessage = component.getClass().getName() + " is already present in " + gameObject.getClass().getName() + ". " + message;
        super(component.getClass().getName(), fullMessage);
    }

    public ComponentPresentException(IComponent component, IGameObject gameObject){
        this(component, gameObject, "");
    }
}
