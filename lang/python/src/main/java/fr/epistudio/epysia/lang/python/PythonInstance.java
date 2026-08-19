package fr.epistudio.epysia.lang.python;

import fr.epistudio.epysia.EngineServices;
import fr.epistudio.epysia.gameobjects.GameObject;
import fr.epistudio.epysia.input.InputState;
import fr.epistudio.epysia.scripting.foreign.ForeignInstance;
import fr.epistudio.epysia.scripting.foreign.ForeignPropertyDefinition;
import org.graalvm.polyglot.Value;

import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Supplier;

final class PythonInstance implements ForeignInstance {

    private static final String OWNER_ATTRIBUTE = "game_object";
    private static final String SERVICES_ATTRIBUTE = "services";
    private static final String UPDATE_BRIDGE = "_epysia_update";

    private final Value object;
    private final Map<String, ForeignPropertyDefinition> definitions;
    private final Consumer<String> failures;

    PythonInstance(Value object, Map<String, ForeignPropertyDefinition> definitions,
                   Consumer<String> failures) {
        this.object = object;
        this.definitions = definitions;
        this.failures = failures;
    }

    @Override
    public Object read(String property) {
        ForeignPropertyDefinition definition = definitions.get(property);
        if (definition == null || !object.hasMember(property)) {
            return null;
        }
        return guard("read " + property,
                () -> PythonValues.toJava(object.getMember(property), definition.type()));
    }

    @Override
    public void write(String property, Object value) {
        guard("write " + property, () -> {
            object.putMember(property, value);
            return null;
        });
    }

    @Override
    public void onAttached(GameObject owner) {
        write(OWNER_ATTRIBUTE, owner);
    }

    @Override
    public void onStart(EngineServices services) {
        write(SERVICES_ATTRIBUTE, services);
        invoke("on_start");
    }

    @Override
    public void onUpdate(InputState input, float deltaTimeSeconds) {
        if (object.hasMember(UPDATE_BRIDGE)) {
            invoke(UPDATE_BRIDGE, input, deltaTimeSeconds);
            return;
        }
        invoke("on_update", input, deltaTimeSeconds);
    }

    @Override
    public void onFixedUpdate(float fixedStepSeconds) {
        invoke("on_fixed_update", fixedStepSeconds);
    }

    @Override
    public void onDestroy() {
        invoke("on_destroy");
    }

    private void invoke(String method, Object... arguments) {
        if (!object.hasMember(method)) {
            return;
        }
        guard(method, () -> object.invokeMember(method, arguments));
    }

    private <T> T guard(String what, Supplier<T> action) {
        try {
            return action.get();
        } catch (RuntimeException failure) {
            failures.accept("Python " + what + " failed: " + failure.getMessage());
            return null;
        }
    }
}
