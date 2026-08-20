package fr.epistudio.epysia.lang.python;

import fr.epistudio.epysia.scripting.foreign.ForeignComponentType;
import fr.epistudio.epysia.scripting.foreign.ForeignInstance;
import fr.epistudio.epysia.scripting.foreign.ForeignPropertyDefinition;
import org.graalvm.polyglot.Value;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

final class PythonComponentType implements ForeignComponentType {

    private static final String META_ATTRIBUTE = "_epysia_meta";

    private final Value pythonClass;
    private final String name;
    private final String category;
    private final String description;
    private final Map<String, ForeignPropertyDefinition> definitions;
    private final Consumer<String> failures;

    private PythonComponentType(Value pythonClass, String name, String category, String description,
                                Map<String, ForeignPropertyDefinition> definitions,
                                Consumer<String> failures) {
        this.pythonClass = pythonClass;
        this.name = name;
        this.category = category;
        this.description = description;
        this.definitions = definitions;
        this.failures = failures;
    }

    static PythonComponentType of(Value pythonClass, Consumer<String> failures) {
        Value meta = pythonClass.getMember(META_ATTRIBUTE);
        return new PythonComponentType(pythonClass,
                meta.getHashValue("name").asString(),
                meta.getHashValue("category").asString(),
                meta.getHashValue("description").asString(),
                definitionsOf(meta.getHashValue("exports")), failures);
    }

    private static Map<String, ForeignPropertyDefinition> definitionsOf(Value exports) {
        Map<String, ForeignPropertyDefinition> definitions = new LinkedHashMap<>();
        for (long index = 0; index < exports.getArraySize(); index++) {
            Value entry = exports.getArrayElement(index);
            String property = entry.getArrayElement(0).asString();
            Value declaration = entry.getArrayElement(1);
            definitions.put(property, definitionOf(property, declaration));
        }
        return Collections.unmodifiableMap(definitions);
    }

    private static ForeignPropertyDefinition definitionOf(String property, Value declaration) {
        Value defaultValue = declaration.getMember("default");
        return new ForeignPropertyDefinition(property,
                declaration.getMember("label").asString(),
                PythonValues.typeOf(defaultValue),
                PythonValues.toJava(defaultValue, PythonValues.typeOf(defaultValue)),
                (float) declaration.getMember("minimum").asDouble(),
                (float) declaration.getMember("maximum").asDouble(),
                (float) declaration.getMember("step").asDouble(),
                declaration.getMember("color").asBoolean());
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
        return new PythonInstance(pythonClass.newInstance(), definitions, failures);
    }
}
