package fr.epistudio.epysia.scripting.foreign;

import fr.epistudio.epysia.EngineServices;
import fr.epistudio.epysia.input.InputState;
import fr.epistudio.epysia.reflection.DynamicProperties;
import fr.epistudio.epysia.reflection.ExportedProperty;
import fr.epistudio.epysia.reflection.PropertyBinding;
import fr.epistudio.epysia.reflection.Reflection;
import fr.epistudio.epysia.scripting.Behaviour;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class ForeignComponent extends Behaviour implements DynamicProperties {

    private final ForeignComponentType type;
    private final ForeignInstance instance;

    public ForeignComponent(ForeignComponentType type, ForeignInstance instance) {
        this.type = type;
        this.instance = instance;
    }

    public String typeName() {
        return type.name();
    }

    @Override
    public List<ExportedProperty> exportedProperties() {
        List<ExportedProperty> properties = new ArrayList<>(type.properties().size());
        for (ForeignPropertyDefinition definition : type.properties()) {
            properties.add(new ExportedProperty(this, new ForeignBinding(definition),
                    Reflection.kindOf(definition.type())));
        }
        return properties;
    }

    @Override
    public void onStart(EngineServices services) {
        owner().ifPresent(instance::onAttached);
        instance.onStart(services);
    }

    @Override
    public void onUpdate(InputState input, float deltaTimeSeconds) {
        instance.onUpdate(input, deltaTimeSeconds);
    }

    @Override
    public void onFixedUpdate(float fixedStepSeconds) {
        instance.onFixedUpdate(fixedStepSeconds);
    }

    @Override
    public void onDestroy() {
        instance.onDestroy();
    }

    private final class ForeignBinding implements PropertyBinding {

        private final ForeignPropertyDefinition definition;

        private ForeignBinding(ForeignPropertyDefinition definition) {
            this.definition = definition;
        }

        @Override
        public String name() {
            return definition.name();
        }

        @Override
        public Class<?> type() {
            return definition.type();
        }

        @Override
        public Object read() {
            Object value = instance.read(definition.name());
            return value == null ? definition.defaultValue() : value;
        }

        @Override
        public void write(Object value) {
            instance.write(definition.name(), value);
        }

        @Override
        public String label() {
            return definition.label();
        }

        @Override
        public float min() {
            return definition.minimum();
        }

        @Override
        public float max() {
            return definition.maximum();
        }

        @Override
        public float step() {
            return definition.step();
        }

        @Override
        public boolean color() {
            return definition.color();
        }

        @Override
        public Optional<Class<?>> elementType() {
            return Optional.empty();
        }
    }
}
