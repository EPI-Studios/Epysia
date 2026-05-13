package fr.epistudio.epysia.exceptions;

import fr.epistudio.epysia.components.IComponent;

public class ComponentNullException extends ComponentException {
    public ComponentNullException(IComponent component, String message) {
        String fullMessage = "Component of type " + component.getClass().getName() + " is null. " + message;
        super(component.getClass().getName(), fullMessage);
    }
}
