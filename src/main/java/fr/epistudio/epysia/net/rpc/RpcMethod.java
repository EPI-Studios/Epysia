package fr.epistudio.epysia.net.rpc;

import fr.epistudio.epysia.components.IComponent;
import fr.epistudio.epysia.exceptions.EpysiaException;
import fr.epistudio.epysia.net.protocol.NetReader;
import fr.epistudio.epysia.net.protocol.NetWriter;
import fr.epistudio.epysia.net.protocol.ValueCodec;
import fr.epistudio.epysia.reflection.ExportedProperty;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public final class RpcMethod {
    private final Method method;
    private final RpcTarget target;
    private final boolean reliable;
    private final List<ExportedProperty.Kind> parameterKinds;

    private RpcMethod(Method method, RpcTarget target, boolean reliable,
                      List<ExportedProperty.Kind> parameterKinds) {
        this.method = method;
        this.target = target;
        this.reliable = reliable;
        this.parameterKinds = parameterKinds;
    }

    public static Optional<RpcMethod> of(Method method, Rpc annotation) {
        List<ExportedProperty.Kind> kinds = new ArrayList<>();
        for (Class<?> parameterType : method.getParameterTypes()) {
            ExportedProperty.Kind kind = ValueCodec.kindOf(parameterType);
            if (!ValueCodec.isSupported(kind)) {
                return Optional.empty();
            }
            kinds.add(kind);
        }
        method.setAccessible(true);
        return Optional.of(new RpcMethod(method, annotation.value(), annotation.reliable(), List.copyOf(kinds)));
    }

    public Class<?> declaringType() {
        return method.getDeclaringClass();
    }

    public String methodName() {
        return method.getName();
    }

    public RpcTarget target() {
        return target;
    }

    public boolean reliable() {
        return reliable;
    }

    public int parameterCount() {
        return parameterKinds.size();
    }

    public String identity() {
        StringBuilder identity = new StringBuilder(declaringType().getName()).append('#').append(methodName());
        for (Class<?> parameterType : method.getParameterTypes()) {
            identity.append(':').append(parameterType.getName());
        }
        return identity.toString();
    }

    public void writeArguments(NetWriter writer, Object[] arguments) {
        requireArgumentCount(arguments.length);
        for (int index = 0; index < parameterKinds.size(); index++) {
            ValueCodec.write(writer, parameterKinds.get(index), arguments[index]);
        }
    }

    public Object[] readArguments(NetReader reader) {
        Object[] arguments = new Object[parameterKinds.size()];
        Class<?>[] parameterTypes = method.getParameterTypes();
        for (int index = 0; index < arguments.length; index++) {
            arguments[index] = ValueCodec.read(reader, parameterKinds.get(index), parameterTypes[index]);
        }
        return arguments;
    }

    public void invokeOn(IComponent component, Object[] arguments) {
        try {
            method.invoke(component, arguments);
        } catch (IllegalAccessException | InvocationTargetException failure) {
            throw new EpysiaException("Remote procedure call " + identity() + " threw", failure);
        }
    }

    private void requireArgumentCount(int provided) {
        if (provided != parameterKinds.size()) {
            throw new EpysiaException("Remote procedure call " + identity() + " expects "
                    + parameterKinds.size() + " arguments but received " + provided);
        }
    }
}
