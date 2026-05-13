package fr.epistudio.epysia.exceptions;

import fr.epistudio.epysia.components.IComponent;

public class ComponentNullException extends ComponentException {
    public ComponentNullException(IComponent component, String message) {
        super(
                component.getClass().getName(),
                "Component of type " + component.getClass().getName() + " is null. " + message
        );
    }
}
