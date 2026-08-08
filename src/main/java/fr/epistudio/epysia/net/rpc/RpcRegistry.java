package fr.epistudio.epysia.net.rpc;

import fr.epistudio.epysia.components.IComponent;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public final class RpcRegistry {
    private static final int FNV_OFFSET_BASIS = 0x811C9DC5;
    private static final int FNV_PRIME = 0x01000193;

    private final List<RpcMethod> ordered;
    private final Map<String, Integer> indexByLookupKey;
    private final List<String> warnings;
    private final int hash;

    private RpcRegistry(List<RpcMethod> ordered, List<String> warnings) {
        this.ordered = List.copyOf(ordered);
        this.warnings = List.copyOf(warnings);
        this.indexByLookupKey = buildLookup(this.ordered);
        this.hash = computeHash(this.ordered);
    }

    public static Builder builder() {
        return new Builder();
    }

    private static Map<String, Integer> buildLookup(List<RpcMethod> ordered) {
        Map<String, Integer> lookup = new LinkedHashMap<>();
        for (int index = 0; index < ordered.size(); index++) {
            RpcMethod method = ordered.get(index);
            lookup.putIfAbsent(lookupKey(method.declaringType(), method.methodName()), index);
        }
        return Map.copyOf(lookup);
    }

    private static String lookupKey(Class<?> declaringType, String methodName) {
        return declaringType.getName() + "#" + methodName;
    }

    private static int computeHash(List<RpcMethod> ordered) {
        int accumulator = FNV_OFFSET_BASIS;
        for (RpcMethod method : ordered) {
            String identity = method.identity();
            for (int index = 0; index < identity.length(); index++) {
                accumulator = (accumulator ^ identity.charAt(index)) * FNV_PRIME;
            }
        }
        return accumulator;
    }

    public Optional<Integer> indexOf(IComponent component, String methodName) {
        Class<?> current = component.getClass();
        while (current != null && current != Object.class) {
            Integer index = indexByLookupKey.get(lookupKey(current, methodName));
            if (index != null) {
                return Optional.of(index);
            }
            current = current.getSuperclass();
        }
        return Optional.empty();
    }

    public Optional<RpcMethod> at(int index) {
        if (index < 0 || index >= ordered.size()) {
            return Optional.empty();
        }
        return Optional.of(ordered.get(index));
    }

    public int size() {
        return ordered.size();
    }

    public int hash() {
        return hash;
    }

    public List<String> warnings() {
        return warnings;
    }

    public static final class Builder {
        private final Map<String, RpcMethod> collected = new LinkedHashMap<>();
        private final List<String> warnings = new ArrayList<>();

        public Builder addComponentType(Class<? extends IComponent> componentType) {
            Class<?> current = componentType;
            while (current != null && current != Object.class) {
                for (Method method : current.getDeclaredMethods()) {
                    acceptMethod(method);
                }
                current = current.getSuperclass();
            }
            return this;
        }

        private void acceptMethod(Method method) {
            Rpc annotation = method.getAnnotation(Rpc.class);
            if (annotation == null) {
                return;
            }
            Optional<RpcMethod> rpc = RpcMethod.of(method, annotation);
            if (rpc.isEmpty()) {
                warnings.add("@Rpc method " + method.getDeclaringClass().getName() + "#" + method.getName()
                        + " has a parameter type the network codec cannot write");
                return;
            }
            collected.putIfAbsent(rpc.get().identity(), rpc.get());
        }

        public RpcRegistry build() {
            List<RpcMethod> ordered = new ArrayList<>(collected.values());
            ordered.sort(Comparator.comparing(RpcMethod::identity));
            return new RpcRegistry(ordered, warnings);
        }
    }
}
