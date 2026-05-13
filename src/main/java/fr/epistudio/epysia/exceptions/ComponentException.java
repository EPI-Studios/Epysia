package fr.epistudio.epysia.exceptions;

public class ComponentException extends EpysiaException {

    private final String componentClassName;

    public ComponentException(String componentClassName, String message) {
        super(message);
        this.componentClassName = componentClassName;
    }

    public String getComponentClassName() {
        return componentClassName;
    }
}
