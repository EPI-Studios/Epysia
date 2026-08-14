package fr.epistudio.epysia.editor.scripteditor;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.lang.reflect.TypeVariable;
import java.util.Optional;

public record MemberType(TypeReference outer, TypeReference element) {

    public static MemberType ofMethod(Method method) {
        return typeOf(method.getGenericReturnType(), Optional.of(method));
    }

    public static MemberType ofField(Field field) {
        return typeOf(field.getGenericType(), Optional.empty());
    }

    private static MemberType typeOf(Type declared, Optional<Method> call) {
        return new MemberType(referenceTo(declared, call), elementOf(declared, call));
    }

    private static TypeReference referenceTo(Type declared, Optional<Method> call) {
        if (declared instanceof TypeVariable<?> variable) {
            return variableReference(variable, call);
        }
        if (declared instanceof ParameterizedType parameterized) {
            return referenceTo(parameterized.getRawType(), call);
        }
        return declared instanceof Class<?> raw
                ? TypeReference.concrete(raw.getSimpleName())
                : TypeReference.unknown();
    }

    private static TypeReference variableReference(TypeVariable<?> variable, Optional<Method> call) {
        if (call.filter(method -> boundByClassParameter(variable, method)).isPresent()) {
            return TypeReference.callClassArgument();
        }
        return variable.getGenericDeclaration() instanceof Class<?>
                ? TypeReference.receiverElement()
                : TypeReference.unknown();
    }

    private static TypeReference elementOf(Type declared, Optional<Method> call) {
        if (!(declared instanceof ParameterizedType parameterized)
                || parameterized.getActualTypeArguments().length != 1) {
            return TypeReference.unknown();
        }
        return referenceTo(parameterized.getActualTypeArguments()[0], call);
    }

    private static boolean boundByClassParameter(TypeVariable<?> variable, Method method) {
        for (Type parameter : method.getGenericParameterTypes()) {
            if (parameter instanceof ParameterizedType parameterized
                    && parameterized.getRawType().equals(Class.class)
                    && parameterized.getActualTypeArguments()[0].equals(variable)) {
                return true;
            }
        }
        return false;
    }
}
