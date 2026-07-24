package fr.epistudio.epysia.graph;

import fr.epistudio.epysia.EngineServices;
import fr.epistudio.epysia.gameobjects.GameObject;
import fr.epistudio.epysia.input.InputState;
import fr.epistudio.epysia.scene.Scene;
import org.joml.Vector3f;

import java.util.Optional;

public final class NodeContext {

    private final GraphInterpreter interpreter;
    private final GraphInstance instance;
    private final EngineServices services;
    private final GraphNode node;

    NodeContext(GraphInterpreter interpreter, GraphInstance instance,
                EngineServices services, GraphNode node) {
        this.interpreter = interpreter;
        this.instance = instance;
        this.services = services;
        this.node = node;
    }

    public GraphInterpreter interpreter() {
        return interpreter;
    }

    public GraphInstance instance() {
        return instance;
    }

    public EngineServices services() {
        return services;
    }

    public GraphNode node() {
        return node;
    }

    public InputState inputState() {
        return instance.inputState();
    }

    public GameObject self() {
        return instance.self();
    }

    public Scene scene() {
        return services.scene();
    }

    public Object input(String pinName, PinType type) {
        return interpreter.pullValue(instance, node, pinName, type, services);
    }

    public float floatInput(String pinName) {
        return GraphValues.asFloat(input(pinName, PinType.FLOAT));
    }

    public int intInput(String pinName) {
        return GraphValues.asInt(input(pinName, PinType.INT));
    }

    public boolean booleanInput(String pinName) {
        return GraphValues.asBoolean(input(pinName, PinType.BOOLEAN));
    }

    public String stringInput(String pinName) {
        return GraphValues.asString(input(pinName, PinType.STRING));
    }

    public Vector3f vectorInput(String pinName) {
        return GraphValues.asVector(input(pinName, PinType.VECTOR3));
    }

    public GameObject gameObjectInput(String pinName) {
        Object value = input(pinName, PinType.GAME_OBJECT);
        return value instanceof GameObject gameObject ? gameObject : self();
    }

    public Object objectInput(String pinName) {
        return input(pinName, PinType.OBJECT);
    }

    public void setOutput(String pinName, Object value) {
        instance.setOutput(node.id(), pinName, value);
    }

    public void triggerExec(String pinName) {
        interpreter.triggerExec(instance, node, pinName, services);
    }

    public void scheduleExec(String pinName, float seconds) {
        interpreter.scheduleExec(instance, node, pinName, seconds, services);
    }

    public String settingString(String key, String fallback) {
        Object value = node.values().get(key);
        return value == null ? fallback : GraphValues.asString(value);
    }

    public int settingInt(String key, int fallback) {
        Object value = node.values().get(key);
        return value instanceof Number number ? number.intValue() : fallback;
    }

    public Object variableValue(String name) {
        return instance.variableValue(name);
    }

    public void setVariableValue(String name, Object value) {
        instance.setVariableValue(name, value);
    }

    public PinType variableType(String name) {
        Optional<GraphVariable> variable = instance.asset().findVariable(name);
        return variable.map(GraphVariable::type).orElse(PinType.OBJECT);
    }
}
