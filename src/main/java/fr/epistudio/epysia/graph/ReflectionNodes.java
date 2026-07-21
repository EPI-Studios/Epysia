package fr.epistudio.epysia.graph;

import fr.epistudio.epysia.GameSystem;
import fr.epistudio.epysia.audio.AudioSystem;
import fr.epistudio.epysia.components.IComponent;
import fr.epistudio.epysia.components.transforms.Transform3D;
import fr.epistudio.epysia.gameobjects.GameObject;
import fr.epistudio.epysia.physics.PhysicsSystem;
import fr.epistudio.epysia.physics.components.CharacterControllerComponent;
import fr.epistudio.epysia.physics.components.RigidBodyComponent;
import fr.epistudio.epysia.reflection.ComponentRegistry;
import fr.epistudio.epysia.scene.Scene;
import org.joml.Vector3f;
import org.joml.Vector3fc;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.Parameter;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.Optional;

public final class ReflectionNodes {

    public static final String TYPE_KEY_PREFIX = "reflect:";
    private static final String TARGET_PIN = "Target";
    private static final String RESULT_PIN = "Result";
    private static final String ENGINE_PACKAGE_PREFIX = "fr.epistudio.epysia";

    public static final List<Class<?>> CURATED_ENGINE_CLASSES = List.of(
            Transform3D.class,
            GameObject.class,
            Scene.class,
            CharacterControllerComponent.class,
            RigidBodyComponent.class,
            PhysicsSystem.class,
            AudioSystem.class);

    private ReflectionNodes() {
    }

    public static List<NodeDefinition> catalog(ComponentRegistry componentRegistry) {
        List<NodeDefinition> definitions = new ArrayList<>();
        for (Class<?> curated : CURATED_ENGINE_CLASSES) {
            definitions.addAll(scanClass(curated));
        }
        for (ComponentRegistry.Entry entry : componentRegistry.entries()) {
            if (!entry.componentClass().getName().startsWith(ENGINE_PACKAGE_PREFIX)) {
                definitions.addAll(scanClass(entry.componentClass()));
            }
        }
        return definitions;
    }

    public static List<NodeDefinition> scanClass(Class<?> scanned) {
        List<NodeDefinition> definitions = new ArrayList<>();
        for (Method method : scanned.getDeclaredMethods()) {
            if (isEligible(method)) {
                definitions.add(definitionForMethod(method));
            }
        }
        definitions.sort((left, right) -> left.displayName().compareToIgnoreCase(right.displayName()));
        return definitions;
    }

    private static final Set<String> INTERNAL_METHOD_NAMES = Set.of(
            "markDirty", "captureInterpolationSnapshot", "localMatrix", "worldMatrix",
            "attachTo", "onLoad", "onPlayStart", "onPlayStop", "registerUnderHierarchy",
            "replaceComponent", "advanceTick", "modificationCount", "hashCode", "equals",
            "toString", "getClass", "wait", "notify", "notifyAll");

    private static boolean isEligible(Method method) {
        if (!Modifier.isPublic(method.getModifiers()) || Modifier.isStatic(method.getModifiers())) {
            return false;
        }
        if (method.isSynthetic() || method.isBridge()) {
            return false;
        }
        if (INTERNAL_METHOD_NAMES.contains(method.getName())) {
            return false;
        }
        for (Class<?> parameterType : method.getParameterTypes()) {
            if (pinTypeForParameter(parameterType).isEmpty()) {
                return false;
            }
        }
        return true;
    }

    public static String typeKeyFor(Method method) {
        StringBuilder key = new StringBuilder(TYPE_KEY_PREFIX);
        key.append(method.getDeclaringClass().getName()).append('#').append(method.getName()).append('(');
        Class<?>[] parameterTypes = method.getParameterTypes();
        for (int index = 0; index < parameterTypes.length; index++) {
            if (index > 0) {
                key.append(',');
            }
            key.append(parameterTypes[index].getName());
        }
        return key.append(')').toString();
    }

    public static Optional<NodeDefinition> definitionFor(String typeKey,
                                                         GraphNodeRegistry.ClassResolver resolver) {
        if (!typeKey.startsWith(TYPE_KEY_PREFIX)) {
            return Optional.empty();
        }
        int hashIndex = typeKey.indexOf('#');
        int openIndex = typeKey.indexOf('(');
        if (hashIndex < 0 || openIndex < hashIndex || !typeKey.endsWith(")")) {
            return Optional.empty();
        }
        String className = typeKey.substring(TYPE_KEY_PREFIX.length(), hashIndex);
        String methodName = typeKey.substring(hashIndex + 1, openIndex);
        String signature = typeKey.substring(openIndex + 1, typeKey.length() - 1);
        return resolver.resolve(className)
                .flatMap(declaring -> findMethod(declaring, methodName, signature))
                .map(ReflectionNodes::definitionForMethod);
    }

    private static Optional<Method> findMethod(Class<?> declaring, String methodName, String signature) {
        for (Method method : declaring.getDeclaredMethods()) {
            if (method.getName().equals(methodName) && signatureOf(method).equals(signature)
                    && isEligible(method)) {
                return Optional.of(method);
            }
        }
        return Optional.empty();
    }

    private static String signatureOf(Method method) {
        StringBuilder signature = new StringBuilder();
        Class<?>[] parameterTypes = method.getParameterTypes();
        for (int index = 0; index < parameterTypes.length; index++) {
            if (index > 0) {
                signature.append(',');
            }
            signature.append(parameterTypes[index].getName());
        }
        return signature.toString();
    }

    private static NodeDefinition definitionForMethod(Method method) {
        method.setAccessible(true);
        Class<?> declaring = method.getDeclaringClass();
        return new NodeDefinition(
                typeKeyFor(method),
                humanizedLabel(method),
                declaring.getSimpleName(),
                false, false,
                inputPinsFor(method),
                outputPinsFor(method),
                List.of(),
                context -> invoke(context, method));
    }

    private static String humanizedLabel(Method method) {
        String name = method.getName();
        if (name.startsWith("set") && name.length() > 3) {
            return "Set " + splitCamelCase(name.substring(3));
        }
        if (name.startsWith("get") && name.length() > 3) {
            return "Get " + splitCamelCase(name.substring(3));
        }
        if (name.startsWith("is") && name.length() > 2 && Character.isUpperCase(name.charAt(2))) {
            return "Is " + splitCamelCase(name.substring(2));
        }
        if (method.getParameterCount() == 0 && method.getReturnType() != void.class) {
            return "Get " + splitCamelCase(capitalize(name));
        }
        return splitCamelCase(capitalize(name));
    }

    private static String splitCamelCase(String text) {
        return text.replaceAll("(?<=[a-z0-9])(?=[A-Z])", " ");
    }

    private static String capitalize(String text) {
        return Character.toUpperCase(text.charAt(0)) + text.substring(1);
    }

    private static List<PinDefinition> inputPinsFor(Method method) {
        List<PinDefinition> inputs = new ArrayList<>();
        inputs.add(PinDefinition.exec(BuiltinNodes.IN_PIN));
        inputs.add(new PinDefinition(TARGET_PIN, targetPinType(method.getDeclaringClass())));
        Parameter[] parameters = method.getParameters();
        for (int index = 0; index < parameters.length; index++) {
            inputs.add(new PinDefinition(parameterPinName(parameters[index], index),
                    pinTypeForParameter(parameters[index].getType()).orElse(PinType.OBJECT)));
        }
        return inputs;
    }

    private static List<PinDefinition> outputPinsFor(Method method) {
        List<PinDefinition> outputs = new ArrayList<>();
        outputs.add(PinDefinition.exec(BuiltinNodes.OUT_PIN));
        if (method.getReturnType() != void.class) {
            outputs.add(new PinDefinition(RESULT_PIN, pinTypeForReturn(method.getReturnType())));
        }
        return outputs;
    }

    private static String parameterPinName(Parameter parameter, int index) {
        if (parameter.isNamePresent()) {
            return prettify(parameter.getName());
        }
        return prettify(parameter.getType().getSimpleName()) + " " + (index + 1);
    }

    private static String prettify(String name) {
        StringBuilder result = new StringBuilder(name.length() + 4);
        for (int index = 0; index < name.length(); index++) {
            char character = name.charAt(index);
            if (index == 0) {
                result.append(Character.toUpperCase(character));
            } else {
                if (Character.isUpperCase(character)) {
                    result.append(' ');
                }
                result.append(character);
            }
        }
        return result.toString();
    }

    private static PinType targetPinType(Class<?> declaring) {
        if (GameObject.class.isAssignableFrom(declaring) || IComponent.class.isAssignableFrom(declaring)) {
            return PinType.GAME_OBJECT;
        }
        return PinType.OBJECT;
    }

    private static Optional<PinType> pinTypeForParameter(Class<?> type) {
        if (type == float.class || type == Float.class) {
            return Optional.of(PinType.FLOAT);
        }
        if (type == int.class || type == Integer.class) {
            return Optional.of(PinType.INT);
        }
        if (type == boolean.class || type == Boolean.class) {
            return Optional.of(PinType.BOOLEAN);
        }
        if (type == String.class) {
            return Optional.of(PinType.STRING);
        }
        if (type == Vector3f.class || type == Vector3fc.class) {
            return Optional.of(PinType.VECTOR3);
        }
        if (type == GameObject.class) {
            return Optional.of(PinType.GAME_OBJECT);
        }
        return Optional.empty();
    }

    private static PinType pinTypeForReturn(Class<?> type) {
        return pinTypeForParameter(type).orElse(PinType.OBJECT);
    }

    private static void invoke(NodeContext context, Method method) {
        Object target = resolveTarget(context, method.getDeclaringClass());
        if (target == GraphValues.ABSENT) {
            warnMissingTarget(context, method);
            context.triggerExec(BuiltinNodes.OUT_PIN);
            return;
        }
        Object[] argumentValues = pullArguments(context, method);
        invokeResolved(context, method, target, argumentValues);
        context.triggerExec(BuiltinNodes.OUT_PIN);
    }

    private static void warnMissingTarget(NodeContext context, Method method) {
        if (context.instance().warnOnceFor("target:" + typeKeyFor(method))) {
            context.services().logger().warn("[Graph] No target available for "
                    + method.getDeclaringClass().getSimpleName() + "." + method.getName()
                    + " in " + context.instance().sourcePath());
        }
    }

    private static Object[] pullArguments(NodeContext context, Method method) {
        Parameter[] parameters = method.getParameters();
        Object[] argumentValues = new Object[parameters.length];
        for (int index = 0; index < parameters.length; index++) {
            String pinName = parameterPinName(parameters[index], index);
            PinType pinType = pinTypeForParameter(parameters[index].getType()).orElse(PinType.OBJECT);
            argumentValues[index] = argumentFor(context, pinName, pinType);
        }
        return argumentValues;
    }

    private static Object argumentFor(NodeContext context, String pinName, PinType pinType) {
        if (pinType == PinType.GAME_OBJECT) {
            return context.gameObjectInput(pinName);
        }
        Object value = context.input(pinName, pinType);
        return value == GraphValues.ABSENT ? GraphValues.defaultFor(pinType) : value;
    }

    private static void invokeResolved(NodeContext context, Method method,
                                       Object target, Object[] argumentValues) {
        try {
            Object result = method.invoke(target, argumentValues);
            storeResult(context, method, result);
        } catch (InvocationTargetException error) {
            throw asRuntimeFailure(method, error.getCause());
        } catch (ReflectiveOperationException error) {
            throw new IllegalStateException("Reflected node invocation failed for "
                    + typeKeyFor(method), error);
        }
    }

    private static RuntimeException asRuntimeFailure(Method method, Throwable cause) {
        if (cause instanceof RuntimeException runtimeCause) {
            return runtimeCause;
        }
        return new IllegalStateException("Reflected node invocation failed for "
                + typeKeyFor(method), cause);
    }

    private static void storeResult(NodeContext context, Method method, Object result) {
        if (method.getReturnType() == void.class) {
            return;
        }
        Object unwrapped = result instanceof Optional<?> optional
                ? optional.map(value -> (Object) value).orElse(GraphValues.ABSENT)
                : result;
        context.setOutput(RESULT_PIN, unwrapped == null ? GraphValues.ABSENT : unwrapped);
    }

    private static Object resolveTarget(NodeContext context, Class<?> declaring) {
        Object connected = context.objectInput(TARGET_PIN);
        if (declaring.isInstance(connected)) {
            return connected;
        }
        if (connected instanceof GameObject gameObject && IComponent.class.isAssignableFrom(declaring)) {
            return componentTarget(gameObject, declaring);
        }
        return defaultTarget(context, declaring);
    }

    private static Object componentTarget(GameObject gameObject, Class<?> declaring) {
        @SuppressWarnings("unchecked")
        Class<? extends IComponent> componentClass = (Class<? extends IComponent>) declaring;
        return gameObject.getComponent(componentClass).map(component -> (Object) component)
                .orElse(GraphValues.ABSENT);
    }

    private static Object defaultTarget(NodeContext context, Class<?> declaring) {
        if (GameObject.class.isAssignableFrom(declaring)) {
            return context.self();
        }
        if (IComponent.class.isAssignableFrom(declaring)) {
            return componentTarget(context.self(), declaring);
        }
        if (Scene.class.isAssignableFrom(declaring)) {
            return context.scene();
        }
        if (GameSystem.class.isAssignableFrom(declaring)) {
            return systemTarget(context, declaring);
        }
        return GraphValues.ABSENT;
    }

    private static Object systemTarget(NodeContext context, Class<?> declaring) {
        @SuppressWarnings("unchecked")
        Class<? extends GameSystem> systemClass = (Class<? extends GameSystem>) declaring;
        GameSystem system = context.services().systems().get(systemClass);
        return system == null ? GraphValues.ABSENT : system;
    }
}
