package fr.epistudio.epysia.reflection;

import fr.epistudio.epysia.EngineServices;
import fr.epistudio.epysia.exceptions.EpysiaException;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

public record ComponentAction(String label, String tooltip, Method method) {
    public void invoke(Object owner, EngineServices services) {
        try {
            method.setAccessible(true);
            if (method.getParameterCount() == 0) {
                method.invoke(owner);
                return;
            }
            method.invoke(owner, services);
        } catch (IllegalAccessException | InvocationTargetException error) {
            throw new EpysiaException("Editor action '" + label + "' failed: " + causeOf(error), error);
        }
    }

    private static String causeOf(ReflectiveOperationException error) {
        Throwable cause = error instanceof InvocationTargetException invocation
                ? invocation.getTargetException() : error;
        return cause.getMessage() == null ? cause.getClass().getSimpleName() : cause.getMessage();
    }
}
