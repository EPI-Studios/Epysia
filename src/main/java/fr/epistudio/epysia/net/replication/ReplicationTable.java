package fr.epistudio.epysia.net.replication;

import fr.epistudio.epysia.components.IComponent;
import fr.epistudio.epysia.gameobjects.GameObject;
import fr.epistudio.epysia.reflection.ExportedProperty;
import fr.epistudio.epysia.reflection.Reflection;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;

public final class ReplicationTable {
    private static final int FNV_OFFSET_BASIS = 0x811C9DC5;
    private static final int FNV_PRIME = 0x01000193;

    private final List<Class<? extends IComponent>> orderedTypes;
    private final Map<Class<? extends IComponent>, List<ReplicatedField>> fieldsByType;
    private final Set<Class<? extends IComponent>> customSerializers;
    private final List<String> warnings;
    private final Map<Class<?>, Integer> indexByType;
    private final int hash;

    private ReplicationTable(List<Class<? extends IComponent>> orderedTypes,
                             Map<Class<? extends IComponent>, List<ReplicatedField>> fieldsByType,
                             Set<Class<? extends IComponent>> customSerializers,
                             List<String> warnings) {
        this.orderedTypes = List.copyOf(orderedTypes);
        this.fieldsByType = Map.copyOf(fieldsByType);
        this.customSerializers = Set.copyOf(customSerializers);
        this.warnings = List.copyOf(warnings);
        this.indexByType = buildIndexLookup(orderedTypes);
        this.hash = computeHash(orderedTypes, fieldsByType, customSerializers);
    }

    public static Builder builder() {
        return new Builder();
    }

    public List<Class<? extends IComponent>> componentTypes() {
        return orderedTypes;
    }

    public List<ReplicatedField> fieldsFor(Class<?> componentType) {
        return fieldsByType.getOrDefault(componentType, List.of());
    }

    public int indexOf(Class<?> componentType) {
        return indexByType.getOrDefault(componentType, -1);
    }

    private static Map<Class<?>, Integer> buildIndexLookup(List<Class<? extends IComponent>> orderedTypes) {
        Map<Class<?>, Integer> lookup = new LinkedHashMap<>();
        for (int index = 0; index < orderedTypes.size(); index++) {
            lookup.put(orderedTypes.get(index), index);
        }
        return Map.copyOf(lookup);
    }

    public boolean hasCustomSerializer(Class<?> componentType) {
        return customSerializers.contains(componentType);
    }

    public boolean replicates(Class<?> componentType) {
        return customSerializers.contains(componentType) || fieldsByType.containsKey(componentType);
    }

    public int hash() {
        return hash;
    }

    public List<String> warnings() {
        return warnings;
    }

    private static int computeHash(List<Class<? extends IComponent>> orderedTypes,
                                   Map<Class<? extends IComponent>, List<ReplicatedField>> fieldsByType,
                                   Set<Class<? extends IComponent>> customSerializers) {
        int accumulator = FNV_OFFSET_BASIS;
        for (Class<? extends IComponent> type : orderedTypes) {
            accumulator = mix(accumulator, type.getName());
            if (customSerializers.contains(type)) {
                accumulator = mix(accumulator, "custom");
            }
            for (ReplicatedField replicated : fieldsByType.getOrDefault(type, List.of())) {
                accumulator = mix(accumulator, replicated.identity());
            }
        }
        return accumulator;
    }

    private static int mix(int accumulator, String text) {
        int result = accumulator;
        for (int index = 0; index < text.length(); index++) {
            result = (result ^ text.charAt(index)) * FNV_PRIME;
        }
        return result;
    }

    public static final class Builder {
        private final Map<Class<? extends IComponent>, Map<String, ReplicatedField>> collected = new LinkedHashMap<>();
        private final Set<Class<? extends IComponent>> customSerializers = new LinkedHashSet<>();
        private final List<String> warnings = new ArrayList<>();

        public Builder addComponentType(Class<? extends IComponent> componentType) {
            if (collected.containsKey(componentType)) {
                return this;
            }
            collected.put(componentType, new TreeMap<>());
            if (NetworkSerializable.class.isAssignableFrom(componentType)) {
                customSerializers.add(componentType);
                return this;
            }
            collectAnnotatedFields(componentType);
            return this;
        }

        private void collectAnnotatedFields(Class<? extends IComponent> componentType) {
            Class<?> current = componentType;
            while (current != null && current != Object.class) {
                for (Field field : current.getDeclaredFields()) {
                    acceptAnnotatedField(componentType, field);
                }
                current = current.getSuperclass();
            }
        }

        private void acceptAnnotatedField(Class<? extends IComponent> componentType, Field field) {
            Replicated annotation = field.getAnnotation(Replicated.class);
            if (annotation == null) {
                return;
            }
            Optional<ReplicatedField> replicated = ReplicatedField.of(
                    field, annotation.condition(), annotation.interpolate(), annotation.sendRate(),
                    annotation.precision());
            if (replicated.isEmpty()) {
                warnings.add("@Replicated field " + componentType.getName() + "#" + field.getName()
                        + " has a type the replication codec cannot write: " + field.getType().getName());
                return;
            }
            collected.get(componentType).put(field.getName(), replicated.get());
        }

        public Builder addSynchronizedProperty(Class<? extends IComponent> componentType,
                                               SynchronizedProperty property) {
            addComponentType(componentType);
            if (customSerializers.contains(componentType)) {
                warnings.add("NetworkSynchronizer entry on " + componentType.getName()
                        + " is ignored because the component serialises itself");
                return this;
            }
            resolveField(componentType, property.fieldName())
                    .ifPresentOrElse(field -> acceptSynchronizedField(componentType, property, field),
                            () -> warnUnresolved(componentType, property));
            return this;
        }

        private void acceptSynchronizedField(Class<? extends IComponent> componentType,
                                             SynchronizedProperty property, Field field) {
            Optional<ReplicatedField> replicated = ReplicatedField.of(
                    field, ReplicationCondition.ALWAYS, property.interpolate(), property.sendRate(),
                    property.precision());
            if (replicated.isEmpty()) {
                warnings.add("NetworkSynchronizer entry " + componentType.getName() + "#" + field.getName()
                        + " has a type the replication codec cannot write: " + field.getType().getName());
                return;
            }
            collected.get(componentType).putIfAbsent(field.getName(), replicated.get());
        }

        private void warnUnresolved(Class<? extends IComponent> componentType, SynchronizedProperty property) {
            warnings.add("NetworkSynchronizer entry " + componentType.getName() + "#" + property.fieldName()
                    + " no longer resolves and is skipped");
        }

        private static Optional<Field> resolveField(Class<?> componentType, String fieldName) {
            Class<?> current = componentType;
            while (current != null && current != Object.class) {
                try {
                    return Optional.of(current.getDeclaredField(fieldName));
                } catch (NoSuchFieldException absent) {
                    current = current.getSuperclass();
                }
            }
            return Optional.empty();
        }

        public List<String> warnings() {
            return warnings;
        }

        public ReplicationTable build() {
            List<Class<? extends IComponent>> ordered = new ArrayList<>(collected.keySet());
            ordered.removeIf(type -> collected.get(type).isEmpty() && !customSerializers.contains(type));
            ordered.sort(Comparator.comparing(Class::getName));
            Map<Class<? extends IComponent>, List<ReplicatedField>> fields = new LinkedHashMap<>();
            for (Class<? extends IComponent> type : ordered) {
                fields.put(type, List.copyOf(collected.get(type).values()));
            }
            customSerializers.retainAll(ordered);
            return new ReplicationTable(ordered, fields, customSerializers, warnings);
        }
    }

    public static boolean isReplicable(ExportedProperty property) {
        return ReplicatedField.isSupported(Reflection.kindOf(property.fieldType()));
    }

    public static Optional<IComponent> findComponentNamed(GameObject owner, String className) {
        for (IComponent component : owner.components()) {
            Class<?> type = component.getClass();
            if (type.getName().equals(className) || type.getSimpleName().equals(className)) {
                return Optional.of(component);
            }
        }
        return Optional.empty();
    }
}
