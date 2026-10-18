package fr.epistudio.epysia.lang.rust;

import fr.epistudio.epysia.scripting.foreign.ForeignComponentType;
import fr.epistudio.epysia.scripting.foreign.ForeignInstance;
import fr.epistudio.epysia.scripting.foreign.ForeignPropertyDefinition;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

final class RustComponentType implements ForeignComponentType {

    private final RustModule module;
    private final int index;
    private final String name;
    private final String category;
    private final String description;
    private final Map<String, ForeignPropertyDefinition> definitions;
    private final Consumer<String> failures;

    private RustComponentType(RustModule module, int index, String name, String category, String description,
                              Map<String, ForeignPropertyDefinition> definitions, Consumer<String> failures) {
        this.module = module;
        this.index = index;
        this.name = name;
        this.category = category;
        this.description = description;
        this.definitions = definitions;
        this.failures = failures;
    }

    @SuppressWarnings("unchecked")
    static List<ForeignComponentType> allFrom(RustModule module, Consumer<String> failures) {
        List<ForeignComponentType> types = new ArrayList<>();
        Object manifest = module.manifest();
        if (!(manifest instanceof List<?> entries)) {
            failures.accept("Rust manifest was not a JSON array");
            return types;
        }
        for (int index = 0; index < entries.size(); index++) {
            Object entry = entries.get(index);
            if (!(entry instanceof Map<?, ?> map)) {
                continue;
            }
            try {
                types.add(of(module, index, (Map<String, Object>) map, failures));
            } catch (RuntimeException failure) {
                failures.accept("Rust component rejected: " + failure.getMessage());
            }
        }
        return types;
    }

    @SuppressWarnings("unchecked")
    private static RustComponentType of(RustModule module, int index, Map<String, Object> entry,
                                        Consumer<String> failures) {
        Map<String, ForeignPropertyDefinition> definitions = new LinkedHashMap<>();
        if (entry.get("properties") instanceof List<?> properties) {
            for (Object rawProperty : properties) {
                if (rawProperty instanceof Map<?, ?> property) {
                    ForeignPropertyDefinition definition = definitionOf((Map<String, Object>) property);
                    definitions.put(definition.name(), definition);
                }
            }
        }
        return new RustComponentType(module, index,
                text(entry, "name", "Component"), text(entry, "category", "Scripts"),
                text(entry, "description", ""), definitions, failures);
    }

    private static ForeignPropertyDefinition definitionOf(Map<String, Object> property) {
        String name = text(property, "name", "");
        Class<?> type = RustValues.typeOf(text(property, "type", "string"));
        Object defaultValue = RustValues.toJava(property.get("default"), type);
        return new ForeignPropertyDefinition(name, text(property, "label", name), type, defaultValue,
                numberOf(property, "min"), numberOf(property, "max"), numberOf(property, "step"),
                Boolean.TRUE.equals(property.get("color")));
    }

    private static float numberOf(Map<String, Object> map, String key) {
        return map.get(key) instanceof Number number ? number.floatValue() : 0.0f;
    }

    private static String text(Map<String, Object> map, String key, String fallback) {
        return map.get(key) instanceof String text ? text : fallback;
    }

    @Override
    public String name() {
        return name;
    }

    @Override
    public String category() {
        return category;
    }

    @Override
    public String description() {
        return description;
    }

    @Override
    public List<ForeignPropertyDefinition> properties() {
        return new ArrayList<>(definitions.values());
    }

    @Override
    public ForeignInstance instantiate() {
        int handle = module.newInstance(index);
        return new RustInstance(module, handle, definitions, failures);
    }
}
