package fr.epistudio.epysia.graph;

import fr.epistudio.epysia.components.transforms.Transform3D;
import fr.epistudio.epysia.gameobjects.GameObject;
import fr.epistudio.epysia.input.InputState;
import fr.epistudio.epysia.input.KeyCode;
import fr.epistudio.epysia.input.action.InputActions;
import fr.epistudio.epysia.physics.PhysicsSystem;
import fr.epistudio.epysia.physics.api.QueryFilter;
import fr.epistudio.epysia.physics.api.RaycastHit2D;
import fr.epistudio.epysia.prefab.PrefabInstantiator;
import org.joml.Vector2f;
import org.joml.Vector3f;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ThreadLocalRandom;

public final class BuiltinNodes {
    public static final String IN_PIN = "In";
    public static final String OUT_PIN = "Out";
    public static final String VALUE_PIN = "Value";
    public static final String RESULT_PIN = "Result";
    public static final String TARGET_PIN = "Target";
    public static final String OTHER_PIN = "Other";
    public static final String DELTA_TIME_PIN = "Delta Time";
    public static final String CONDITION_PIN = "Condition";

    public static final String VARIABLE_GET = "variable.get";
    public static final String VARIABLE_SET = "variable.set";
    public static final String FLOW_SEQUENCE = "flow.sequence";
    public static final String EVENT_ON_START = "event.onStart";
    public static final String EVENT_ON_UPDATE = "event.onUpdate";
    public static final String EVENT_ON_TRIGGER_ENTER = "event.onTriggerEnter";
    public static final String EVENT_ON_TRIGGER_EXIT = "event.onTriggerExit";
    public static final String EVENT_ON_COLLISION = "event.onCollision";
    public static final String EVENT_ON_KEY_PRESSED = "event.onKeyPressed";
    public static final String EVENT_ON_KEY_RELEASED = "event.onKeyReleased";
    public static final String EVENT_ON_MOUSE_BUTTON_PRESSED = "event.onMouseButtonPressed";
    public static final String INPUT_KEY_DOWN = "input.keyDown";
    public static final String INPUT_AXIS = "input.axis";
    public static final String PHYSICS_RAYCAST_2D = "physics.raycast2D";
    public static final String INPUT_ACTION_DOWN = "input.actionDown";
    public static final String INPUT_ACTION_PRESSED = "input.actionPressed";
    public static final String INPUT_ACTION_RELEASED = "input.actionReleased";
    public static final String INPUT_ACTION_AXIS = "input.actionAxis";
    public static final String FLOW_TIMER = "flow.timer";
    public static final String ACTION_SETTING = "action";

    public static final String VARIABLE_NAME_SETTING = "variableName";
    public static final String OUTPUT_COUNT_SETTING = "outputCount";
    public static final String KEY_SETTING = "key";
    public static final String NEGATIVE_KEY_SETTING = "negativeKey";
    public static final String POSITIVE_KEY_SETTING = "positiveKey";
    public static final String BUTTON_SETTING = "button";
    public static final String PATH_SETTING = "path";
    public static final String OPERATOR_SETTING = "operator";
    public static final String LEVEL_SETTING = "level";

    public static final String CATEGORY_INPUT = "Input";

    private static final String CATEGORY_EVENTS = "Events";
    private static final String CATEGORY_FLOW = "Flow";
    private static final String CATEGORY_VARIABLES = "Variables";
    private static final String CATEGORY_MATH = "Math";
    private static final String CATEGORY_LOGIC = "Logic";
    private static final String CATEGORY_GAME_OBJECT = "GameObject";
    private static final float MINIMUM_SMOOTH_TIME = 0.0001f;

    private static final String CATEGORY_PHYSICS = "Physics";
    private static final String CATEGORY_UTILITY = "Utility";

    private BuiltinNodes() {
    }

    public static String sequencePinName(int index) {
        return "Then " + index;
    }

    public static void registerInto(GraphNodeRegistry registry) {
        registerEvents(registry);
        registerInputValues(registry);
        registerActionNodes(registry);
        registerFlow(registry);
        registerVariables(registry);
        registerFloatMath(registry);
        registerScalarHelpers(registry);
        registerVectorMath(registry);
        registerLogic(registry);
        registerGameObject(registry);
        registerPhysics2D(registry);
        registerUtility(registry);
    }

    private static void registerEvents(GraphNodeRegistry registry) {
        registry.register(event(EVENT_ON_START, "On Start", List.of(), List.of()));
        registry.register(event(EVENT_ON_UPDATE, "On Update",
                List.of(new PinDefinition(DELTA_TIME_PIN, PinType.FLOAT)), List.of()));
        registry.register(event(EVENT_ON_TRIGGER_ENTER, "On Trigger Enter",
                List.of(new PinDefinition(OTHER_PIN, PinType.GAME_OBJECT)), List.of()));
        registry.register(event(EVENT_ON_TRIGGER_EXIT, "On Trigger Exit",
                List.of(new PinDefinition(OTHER_PIN, PinType.GAME_OBJECT)), List.of()));
        registry.register(event(EVENT_ON_COLLISION, "On Collision",
                List.of(new PinDefinition(OTHER_PIN, PinType.GAME_OBJECT)), List.of()));
        registerInputEvents(registry);
    }

    private static void registerInputEvents(GraphNodeRegistry registry) {
        registry.register(event(EVENT_ON_KEY_PRESSED, "On Key Pressed", List.of(),
                List.of(new NodeSetting(KEY_SETTING, SettingKind.KEY, "SPACE"))));
        registry.register(event(EVENT_ON_KEY_RELEASED, "On Key Released", List.of(),
                List.of(new NodeSetting(KEY_SETTING, SettingKind.KEY, "SPACE"))));
        registry.register(event(EVENT_ON_MOUSE_BUTTON_PRESSED, "On Mouse Button Pressed", List.of(),
                List.of(new NodeSetting(BUTTON_SETTING, SettingKind.MOUSE_BUTTON, "LEFT"))));
    }

    private static NodeDefinition event(String typeKey, String displayName,
                                        List<PinDefinition> payloadOutputs, List<NodeSetting> settings) {
        List<PinDefinition> outputs = new ArrayList<>();
        outputs.add(PinDefinition.exec(OUT_PIN));
        outputs.addAll(payloadOutputs);
        return new NodeDefinition(typeKey, displayName, CATEGORY_EVENTS, false, true,
                List.of(), List.copyOf(outputs), settings,
                context -> context.triggerExec(OUT_PIN));
    }

    private static void registerInputValues(GraphNodeRegistry registry) {
        registry.register(dataNode(INPUT_KEY_DOWN, "Key Down", CATEGORY_INPUT, true,
                List.of(),
                List.of(new PinDefinition(VALUE_PIN, PinType.BOOLEAN)),
                List.of(new NodeSetting(KEY_SETTING, SettingKind.KEY, "SPACE")),
                context -> context.setOutput(VALUE_PIN, keyHeld(context, KEY_SETTING, "SPACE"))));
        registry.register(dataNode(INPUT_AXIS, "Input Axis", CATEGORY_INPUT, true,
                List.of(),
                List.of(new PinDefinition(VALUE_PIN, PinType.FLOAT)),
                List.of(new NodeSetting(NEGATIVE_KEY_SETTING, SettingKind.KEY, "A"),
                        new NodeSetting(POSITIVE_KEY_SETTING, SettingKind.KEY, "D")),
                BuiltinNodes::runInputAxis));
    }

    private static void registerActionNodes(GraphNodeRegistry registry) {
        registry.register(actionNode(INPUT_ACTION_DOWN, "Action Down", InputActions::isDown));
        registry.register(actionNode(INPUT_ACTION_PRESSED, "Action Pressed", InputActions::wasPressed));
        registry.register(actionNode(INPUT_ACTION_RELEASED, "Action Released", InputActions::wasReleased));
        registry.register(dataNode(INPUT_ACTION_AXIS, "Action Axis", CATEGORY_INPUT, true,
                List.of(),
                List.of(new PinDefinition(VALUE_PIN, PinType.FLOAT)),
                List.of(new NodeSetting(ACTION_SETTING, SettingKind.ACTION_NAME, InputActions.UNNAMED_ACTION)),
                context -> context.setOutput(VALUE_PIN, actionsOf(context)
                        .value(actionNameOf(context), context.inputState()))));
    }

    private static NodeDefinition actionNode(String typeKey, String displayName, ActionTest test) {
        return dataNode(typeKey, displayName, CATEGORY_INPUT, true,
                List.of(),
                List.of(new PinDefinition(VALUE_PIN, PinType.BOOLEAN)),
                List.of(new NodeSetting(ACTION_SETTING, SettingKind.ACTION_NAME, InputActions.UNNAMED_ACTION)),
                context -> context.setOutput(VALUE_PIN,
                        test.matches(actionsOf(context), actionNameOf(context), context.inputState())));
    }

    private interface ActionTest {
        boolean matches(InputActions actions, String name, InputState input);
    }

    private static InputActions actionsOf(NodeContext context) {
        return context.services().inputActions();
    }

    private static String actionNameOf(NodeContext context) {
        return context.settingString(ACTION_SETTING, InputActions.UNNAMED_ACTION);
    }

    private static void runInputAxis(NodeContext context) {
        boolean negativeHeld = keyHeld(context, NEGATIVE_KEY_SETTING, "A");
        boolean positiveHeld = keyHeld(context, POSITIVE_KEY_SETTING, "D");
        float axis = (positiveHeld ? 1.0f : 0.0f) - (negativeHeld ? 1.0f : 0.0f);
        context.setOutput(VALUE_PIN, axis);
    }

    private static boolean keyHeld(NodeContext context, String settingKey, String fallbackKeyName) {
        String keyName = context.settingString(settingKey, fallbackKeyName);
        try {
            return context.inputState().isKeyDown(KeyCode.valueOf(keyName));
        } catch (IllegalArgumentException unknown) {
            return false;
        }
    }

    private static void registerFlow(GraphNodeRegistry registry) {
        registry.register(branchDefinition());
        registry.register(sequenceDefinition());
        registry.register(delayDefinition());
        registry.register(repeatDefinition());
        registry.register(timerDefinition());
        registry.register(whileLoopDefinition());
    }

    private static NodeDefinition branchDefinition() {
        return execNode("flow.branch", "Branch", CATEGORY_FLOW,
                List.of(PinDefinition.exec(IN_PIN), new PinDefinition(CONDITION_PIN, PinType.BOOLEAN)),
                List.of(PinDefinition.exec("True"), PinDefinition.exec("False")),
                List.of(),
                context -> context.triggerExec(context.booleanInput(CONDITION_PIN) ? "True" : "False"));
    }

    private static NodeDefinition sequenceDefinition() {
        return execNode(FLOW_SEQUENCE, "Sequence", CATEGORY_FLOW,
                List.of(PinDefinition.exec(IN_PIN)),
                List.of(PinDefinition.exec(sequencePinName(1)), PinDefinition.exec(sequencePinName(2))),
                List.of(new NodeSetting(OUTPUT_COUNT_SETTING, SettingKind.WHOLE_NUMBER, 2)),
                BuiltinNodes::runSequence);
    }

    private static void runSequence(NodeContext context) {
        int count = Math.max(1, context.settingInt(OUTPUT_COUNT_SETTING, 2));
        for (int index = 1; index <= count; index++) {
            context.triggerExec(sequencePinName(index));
        }
    }

    private static NodeDefinition delayDefinition() {
        return execNode("flow.delay", "Delay", CATEGORY_FLOW,
                List.of(PinDefinition.exec(IN_PIN), new PinDefinition("Seconds", PinType.FLOAT)),
                List.of(PinDefinition.exec(OUT_PIN)),
                List.of(),
                context -> context.scheduleExec(OUT_PIN, context.floatInput("Seconds")));
    }

    private static NodeDefinition repeatDefinition() {
        return execNode("flow.repeat", "Repeat", CATEGORY_FLOW,
                List.of(PinDefinition.exec(IN_PIN), new PinDefinition("Count", PinType.INT)),
                List.of(PinDefinition.exec("Loop"), PinDefinition.exec("Completed"),
                        new PinDefinition("Index", PinType.INT)),
                List.of(),
                BuiltinNodes::runRepeat);
    }

    private static void runRepeat(NodeContext context) {
        int count = context.intInput("Count");
        for (int index = 0; index < count; index++) {
            context.setOutput("Index", index);
            context.triggerExec("Loop");
        }
        context.triggerExec("Completed");
    }

    private static NodeDefinition whileLoopDefinition() {
        return execNode("flow.whileLoop", "While Loop", CATEGORY_FLOW,
                List.of(PinDefinition.exec(IN_PIN), new PinDefinition(CONDITION_PIN, PinType.BOOLEAN)),
                List.of(PinDefinition.exec("Loop"), PinDefinition.exec("Completed")),
                List.of(),
                BuiltinNodes::runWhileLoop);
    }

    private static void runWhileLoop(NodeContext context) {
        int iterations = 0;
        while (context.booleanInput(CONDITION_PIN)
                && iterations < GraphInterpreter.WHILE_LOOP_ITERATION_LIMIT) {
            context.triggerExec("Loop");
            iterations++;
        }
        if (iterations >= GraphInterpreter.WHILE_LOOP_ITERATION_LIMIT) {
            context.services().logger().warn("[Graph] While Loop reached its iteration cap of "
                    + GraphInterpreter.WHILE_LOOP_ITERATION_LIMIT + " in " + context.instance().sourcePath());
        }
        context.triggerExec("Completed");
    }

    private static NodeDefinition timerDefinition() {
        return execNode(FLOW_TIMER, "Timer", CATEGORY_FLOW,
                List.of(PinDefinition.exec(IN_PIN),
                        new PinDefinition("Restart", PinType.BOOLEAN),
                        new PinDefinition("Duration", PinType.FLOAT),
                        new PinDefinition(DELTA_TIME_PIN, PinType.FLOAT)),
                List.of(PinDefinition.exec(OUT_PIN),
                        new PinDefinition("Remaining", PinType.FLOAT),
                        new PinDefinition("Finished", PinType.BOOLEAN)),
                List.of(),
                BuiltinNodes::runTimer);
    }

    private static void runTimer(NodeContext context) {
        float duration = context.floatInput("Duration");
        float remaining = context.booleanInput("Restart")
                ? duration
                : context.memory(0.0f) - context.floatInput(DELTA_TIME_PIN);
        float clamped = Math.max(remaining, 0.0f);
        context.setMemory(clamped);
        context.setOutput("Remaining", clamped);
        context.setOutput("Finished", clamped <= 0.0f);
        context.triggerExec(OUT_PIN);
    }

    private static void registerVariables(GraphNodeRegistry registry) {
        registry.register(new NodeDefinition(VARIABLE_GET, "Get Variable", CATEGORY_VARIABLES,
                false, false, List.of(),
                List.of(new PinDefinition(VALUE_PIN, PinType.OBJECT)),
                List.of(new NodeSetting(VARIABLE_NAME_SETTING, SettingKind.VARIABLE_NAME, "")),
                BuiltinNodes::runGetVariable));
        registry.register(execNode(VARIABLE_SET, "Set Variable", CATEGORY_VARIABLES,
                List.of(PinDefinition.exec(IN_PIN), new PinDefinition(VALUE_PIN, PinType.OBJECT)),
                List.of(PinDefinition.exec(OUT_PIN)),
                List.of(new NodeSetting(VARIABLE_NAME_SETTING, SettingKind.VARIABLE_NAME, "")),
                BuiltinNodes::runSetVariable));
    }

    private static void runGetVariable(NodeContext context) {
        String name = context.settingString(VARIABLE_NAME_SETTING, "");
        context.setOutput(VALUE_PIN, context.variableValue(name));
    }

    private static void runSetVariable(NodeContext context) {
        String name = context.settingString(VARIABLE_NAME_SETTING, "");
        Object value = context.input(VALUE_PIN, context.variableType(name));
        context.setVariableValue(name, value);
        context.triggerExec(OUT_PIN);
    }

    private static void registerFloatMath(GraphNodeRegistry registry) {
        registry.register(floatPair("math.add", "Add (Float)", (a, b) -> a + b));
        registry.register(floatPair("math.subtract", "Subtract (Float)", (a, b) -> a - b));
        registry.register(floatPair("math.multiply", "Multiply (Float)", (a, b) -> a * b));
        registry.register(floatPair("math.divide", "Divide (Float)", (a, b) -> b == 0.0f ? 0.0f : a / b));
        registry.register(compareDefinition());
        registry.register(randomRangeDefinition());
        registry.register(lerpDefinition());
    }

    private interface FloatOperation {
        float apply(float a, float b);
    }

    private static NodeDefinition floatPair(String typeKey, String displayName, FloatOperation operation) {
        return dataNode(typeKey, displayName, CATEGORY_MATH, true,
                List.of(new PinDefinition("A", PinType.FLOAT), new PinDefinition("B", PinType.FLOAT)),
                List.of(new PinDefinition(RESULT_PIN, PinType.FLOAT)),
                List.of(),
                context -> context.setOutput(RESULT_PIN,
                        operation.apply(context.floatInput("A"), context.floatInput("B"))));
    }

    private static NodeDefinition compareDefinition() {
        return dataNode("math.compare", "Compare", CATEGORY_MATH, true,
                List.of(new PinDefinition("A", PinType.FLOAT), new PinDefinition("B", PinType.FLOAT)),
                List.of(new PinDefinition(RESULT_PIN, PinType.BOOLEAN)),
                List.of(new NodeSetting(OPERATOR_SETTING, SettingKind.COMPARISON, ">")),
                BuiltinNodes::runCompare);
    }

    private static void runCompare(NodeContext context) {
        float a = context.floatInput("A");
        float b = context.floatInput("B");
        String operator = context.settingString(OPERATOR_SETTING, ">");
        boolean result = switch (operator) {
            case "<" -> a < b;
            case ">=" -> a >= b;
            case "<=" -> a <= b;
            case "==" -> a == b;
            case "!=" -> a != b;
            default -> a > b;
        };
        context.setOutput(RESULT_PIN, result);
    }

    private static NodeDefinition randomRangeDefinition() {
        return dataNode("math.randomRange", "Random Range", CATEGORY_MATH, true,
                List.of(new PinDefinition("Min", PinType.FLOAT), new PinDefinition("Max", PinType.FLOAT)),
                List.of(new PinDefinition(RESULT_PIN, PinType.FLOAT)),
                List.of(),
                BuiltinNodes::runRandomRange);
    }

    private static void runRandomRange(NodeContext context) {
        float minimum = context.floatInput("Min");
        float maximum = context.floatInput("Max");
        float span = maximum - minimum;
        context.setOutput(RESULT_PIN, minimum + ThreadLocalRandom.current().nextFloat() * span);
    }

    private static NodeDefinition lerpDefinition() {
        return dataNode("math.lerp", "Lerp (Float)", CATEGORY_MATH, true,
                List.of(new PinDefinition("A", PinType.FLOAT), new PinDefinition("B", PinType.FLOAT),
                        new PinDefinition("T", PinType.FLOAT)),
                List.of(new PinDefinition(RESULT_PIN, PinType.FLOAT)),
                List.of(),
                context -> context.setOutput(RESULT_PIN, context.floatInput("A")
                        + (context.floatInput("B") - context.floatInput("A")) * context.floatInput("T")));
    }

    private static void registerScalarHelpers(GraphNodeRegistry registry) {
        registry.register(singleFloat("math.sign", "Sign", Math::signum));
        registry.register(singleFloat("math.abs", "Abs", Math::abs));
        registry.register(pairFloat("math.min", "Min", Math::min));
        registry.register(pairFloat("math.max", "Max", Math::max));
        registry.register(clampDefinition());
        registry.register(moveTowardDefinition());
        registry.register(smoothDampDefinition());
    }

    private static NodeDefinition singleFloat(String typeKey, String displayName, FloatUnary operation) {
        return dataNode(typeKey, displayName, CATEGORY_MATH, true,
                List.of(new PinDefinition(VALUE_PIN, PinType.FLOAT)),
                List.of(new PinDefinition(RESULT_PIN, PinType.FLOAT)),
                List.of(),
                context -> context.setOutput(RESULT_PIN, operation.apply(context.floatInput(VALUE_PIN))));
    }

    private static NodeDefinition pairFloat(String typeKey, String displayName, FloatBinary operation) {
        return dataNode(typeKey, displayName, CATEGORY_MATH, true,
                List.of(new PinDefinition("A", PinType.FLOAT), new PinDefinition("B", PinType.FLOAT)),
                List.of(new PinDefinition(RESULT_PIN, PinType.FLOAT)),
                List.of(),
                context -> context.setOutput(RESULT_PIN,
                        operation.apply(context.floatInput("A"), context.floatInput("B"))));
    }

    private static NodeDefinition clampDefinition() {
        return dataNode("math.clamp", "Clamp", CATEGORY_MATH, true,
                List.of(new PinDefinition(VALUE_PIN, PinType.FLOAT),
                        new PinDefinition("Min", PinType.FLOAT), new PinDefinition("Max", PinType.FLOAT)),
                List.of(new PinDefinition(RESULT_PIN, PinType.FLOAT)),
                List.of(),
                context -> context.setOutput(RESULT_PIN, Math.clamp(context.floatInput(VALUE_PIN),
                        context.floatInput("Min"), context.floatInput("Max"))));
    }

    private static NodeDefinition moveTowardDefinition() {
        return dataNode("math.moveToward", "Move Toward", CATEGORY_MATH, true,
                List.of(new PinDefinition("Current", PinType.FLOAT),
                        new PinDefinition("Target", PinType.FLOAT),
                        new PinDefinition("Max Step", PinType.FLOAT)),
                List.of(new PinDefinition(RESULT_PIN, PinType.FLOAT)),
                List.of(),
                context -> context.setOutput(RESULT_PIN, moveToward(context.floatInput("Current"),
                        context.floatInput("Target"), context.floatInput("Max Step"))));
    }

    private static float moveToward(float current, float target, float maxStep) {
        float difference = target - current;
        if (Math.abs(difference) <= maxStep) {
            return target;
        }
        return current + Math.signum(difference) * maxStep;
    }

    private static NodeDefinition smoothDampDefinition() {
        return dataNode("math.smoothDamp", "Smooth Damp", CATEGORY_MATH, false,
                List.of(new PinDefinition("Current", PinType.FLOAT),
                        new PinDefinition("Target", PinType.FLOAT),
                        new PinDefinition("Smooth Time", PinType.FLOAT),
                        new PinDefinition(DELTA_TIME_PIN, PinType.FLOAT)),
                List.of(new PinDefinition(RESULT_PIN, PinType.FLOAT)),
                List.of(),
                BuiltinNodes::runSmoothDamp);
    }

    private static void runSmoothDamp(NodeContext context) {
        float current = context.floatInput("Current");
        float target = context.floatInput("Target");
        float smoothTime = Math.max(context.floatInput("Smooth Time"), MINIMUM_SMOOTH_TIME);
        float delta = context.floatInput(DELTA_TIME_PIN);
        float omega = 2.0f / smoothTime;
        float decay = (float) Math.exp(-omega * delta);
        float difference = current - target;
        float velocity = context.memory(0.0f);
        float acceleration = (velocity + omega * difference) * delta;
        context.setMemory((velocity - omega * acceleration) * decay);
        context.setOutput(RESULT_PIN, target + (difference + acceleration) * decay);
    }

    private interface FloatUnary {
        float apply(float value);
    }

    private interface FloatBinary {
        float apply(float left, float right);
    }

    private static void registerVectorMath(GraphNodeRegistry registry) {
        registry.register(vectorPair("math.addVectors", "Add (Vector)", Vector3f::add));
        registry.register(vectorPair("math.subtractVectors", "Subtract (Vector)", Vector3f::sub));
        registry.register(scaleVectorDefinition());
        registry.register(lerpVectorsDefinition());
        registry.register(distanceDefinition());
    }

    private interface VectorOperation {
        Vector3f apply(Vector3f a, Vector3f b);
    }

    private static NodeDefinition vectorPair(String typeKey, String displayName, VectorOperation operation) {
        return dataNode(typeKey, displayName, CATEGORY_MATH, true,
                List.of(new PinDefinition("A", PinType.VECTOR3), new PinDefinition("B", PinType.VECTOR3)),
                List.of(new PinDefinition(RESULT_PIN, PinType.VECTOR3)),
                List.of(),
                context -> context.setOutput(RESULT_PIN,
                        operation.apply(context.vectorInput("A"), context.vectorInput("B"))));
    }

    private static NodeDefinition scaleVectorDefinition() {
        return dataNode("math.scaleVector", "Scale Vector", CATEGORY_MATH, true,
                List.of(new PinDefinition("Vector", PinType.VECTOR3), new PinDefinition("Scale", PinType.FLOAT)),
                List.of(new PinDefinition(RESULT_PIN, PinType.VECTOR3)),
                List.of(),
                context -> context.setOutput(RESULT_PIN,
                        context.vectorInput("Vector").mul(context.floatInput("Scale"))));
    }

    private static NodeDefinition lerpVectorsDefinition() {
        return dataNode("math.lerpVectors", "Lerp (Vector)", CATEGORY_MATH, true,
                List.of(new PinDefinition("A", PinType.VECTOR3), new PinDefinition("B", PinType.VECTOR3),
                        new PinDefinition("T", PinType.FLOAT)),
                List.of(new PinDefinition(RESULT_PIN, PinType.VECTOR3)),
                List.of(),
                context -> context.setOutput(RESULT_PIN,
                        context.vectorInput("A").lerp(context.vectorInput("B"), context.floatInput("T"))));
    }

    private static NodeDefinition distanceDefinition() {
        return dataNode("math.distance", "Distance", CATEGORY_MATH, true,
                List.of(new PinDefinition("A", PinType.VECTOR3), new PinDefinition("B", PinType.VECTOR3)),
                List.of(new PinDefinition(RESULT_PIN, PinType.FLOAT)),
                List.of(),
                context -> context.setOutput(RESULT_PIN,
                        context.vectorInput("A").distance(context.vectorInput("B"))));
    }

    private static void registerLogic(GraphNodeRegistry registry) {
        registry.register(booleanPair("logic.and", "And", (a, b) -> a && b));
        registry.register(booleanPair("logic.or", "Or", (a, b) -> a || b));
        registry.register(dataNode("logic.not", "Not", CATEGORY_LOGIC, true,
                List.of(new PinDefinition(VALUE_PIN, PinType.BOOLEAN)),
                List.of(new PinDefinition(RESULT_PIN, PinType.BOOLEAN)),
                List.of(),
                context -> context.setOutput(RESULT_PIN, !context.booleanInput(VALUE_PIN))));
    }

    private interface BooleanOperation {
        boolean apply(boolean a, boolean b);
    }

    private static NodeDefinition booleanPair(String typeKey, String displayName, BooleanOperation operation) {
        return dataNode(typeKey, displayName, CATEGORY_LOGIC, true,
                List.of(new PinDefinition("A", PinType.BOOLEAN), new PinDefinition("B", PinType.BOOLEAN)),
                List.of(new PinDefinition(RESULT_PIN, PinType.BOOLEAN)),
                List.of(),
                context -> context.setOutput(RESULT_PIN,
                        operation.apply(context.booleanInput("A"), context.booleanInput("B"))));
    }

    private static void registerGameObject(GraphNodeRegistry registry) {
        registry.register(selfDefinition());
        registry.register(findByNameDefinition());
        registry.register(findByTagDefinition());
        registry.register(getPositionDefinition());
        registry.register(setPositionDefinition());
        registry.register(setActiveDefinition());
        registry.register(destroyDefinition());
        registry.register(instantiatePrefabDefinition());
    }

    private static NodeDefinition selfDefinition() {
        return dataNode("gameobject.self", "Self", CATEGORY_GAME_OBJECT, true,
                List.of(),
                List.of(new PinDefinition("Self", PinType.GAME_OBJECT)),
                List.of(),
                context -> context.setOutput("Self", context.self()));
    }

    private static NodeDefinition findByNameDefinition() {
        return dataNode("gameobject.findByName", "Find By Name", CATEGORY_GAME_OBJECT, false,
                List.of(new PinDefinition("Name", PinType.STRING)),
                List.of(new PinDefinition(RESULT_PIN, PinType.GAME_OBJECT)),
                List.of(),
                context -> context.scene().findByName(context.stringInput("Name"))
                        .ifPresent(found -> context.setOutput(RESULT_PIN, found)));
    }

    private static NodeDefinition findByTagDefinition() {
        return dataNode("gameobject.findByTag", "Find By Tag", CATEGORY_GAME_OBJECT, false,
                List.of(new PinDefinition("Tag", PinType.STRING)),
                List.of(new PinDefinition(RESULT_PIN, PinType.GAME_OBJECT)),
                List.of(),
                BuiltinNodes::runFindByTag);
    }

    private static void runFindByTag(NodeContext context) {
        List<GameObject> matches = context.scene().findByTag(context.stringInput("Tag"));
        if (!matches.isEmpty()) {
            context.setOutput(RESULT_PIN, matches.get(0));
        } else {
            context.setOutput(RESULT_PIN, GraphValues.ABSENT);
        }
    }

    private static NodeDefinition getPositionDefinition() {
        return dataNode("gameobject.getPosition", "Get Position", CATEGORY_GAME_OBJECT, false,
                List.of(new PinDefinition(TARGET_PIN, PinType.GAME_OBJECT)),
                List.of(new PinDefinition("Position", PinType.VECTOR3)),
                List.of(),
                context -> context.setOutput("Position",
                        context.gameObjectInput(TARGET_PIN).getComponent(Transform3D.class)
                                .map(transform -> new Vector3f(transform.position()))
                                .orElseGet(Vector3f::new)));
    }

    private static NodeDefinition setPositionDefinition() {
        return execNode("gameobject.setPosition", "Set Position", CATEGORY_GAME_OBJECT,
                List.of(PinDefinition.exec(IN_PIN), new PinDefinition(TARGET_PIN, PinType.GAME_OBJECT),
                        new PinDefinition("Position", PinType.VECTOR3)),
                List.of(PinDefinition.exec(OUT_PIN)),
                List.of(),
                BuiltinNodes::runSetPosition);
    }

    private static void runSetPosition(NodeContext context) {
        Vector3f position = context.vectorInput("Position");
        context.gameObjectInput(TARGET_PIN).getComponent(Transform3D.class)
                .ifPresent(transform -> transform.setPosition(position.x, position.y, position.z));
        context.triggerExec(OUT_PIN);
    }

    private static NodeDefinition setActiveDefinition() {
        return execNode("gameobject.setActive", "Set Active", CATEGORY_GAME_OBJECT,
                List.of(PinDefinition.exec(IN_PIN), new PinDefinition(TARGET_PIN, PinType.GAME_OBJECT),
                        new PinDefinition("Active", PinType.BOOLEAN)),
                List.of(PinDefinition.exec(OUT_PIN)),
                List.of(),
                context -> {
                    context.gameObjectInput(TARGET_PIN).setActive(context.booleanInput("Active"));
                    context.triggerExec(OUT_PIN);
                });
    }

    private static NodeDefinition destroyDefinition() {
        return execNode("gameobject.destroy", "Destroy", CATEGORY_GAME_OBJECT,
                List.of(PinDefinition.exec(IN_PIN), new PinDefinition(TARGET_PIN, PinType.GAME_OBJECT)),
                List.of(PinDefinition.exec(OUT_PIN)),
                List.of(),
                context -> {
                    context.scene().removeGameObject(context.gameObjectInput(TARGET_PIN));
                    context.triggerExec(OUT_PIN);
                });
    }

    private static NodeDefinition instantiatePrefabDefinition() {
        return execNode("gameobject.instantiatePrefab", "Instantiate Prefab", CATEGORY_GAME_OBJECT,
                List.of(PinDefinition.exec(IN_PIN), new PinDefinition("Position", PinType.VECTOR3)),
                List.of(PinDefinition.exec(OUT_PIN), new PinDefinition("Instance", PinType.GAME_OBJECT)),
                List.of(new NodeSetting(PATH_SETTING, SettingKind.ASSET_PATH, "")),
                BuiltinNodes::runInstantiatePrefab);
    }

    private static void runInstantiatePrefab(NodeContext context) {
        String path = context.settingString(PATH_SETTING, "");
        if (!path.isEmpty()) {
            instantiatePrefab(context, path);
        }
        context.triggerExec(OUT_PIN);
    }

    private static void instantiatePrefab(NodeContext context, String path) {
        try {
            PrefabInstantiator instantiator = new PrefabInstantiator(context.interpreter().componentRegistry());
            GameObject instance = instantiator.instantiate(Path.of(path), context.scene(), context.services());
            Vector3f position = context.vectorInput("Position");
            instance.getComponent(Transform3D.class)
                    .ifPresent(transform -> transform.setPosition(position.x, position.y, position.z));
            context.setOutput("Instance", instance);
        } catch (IOException | RuntimeException error) {
            context.services().logger().error("[Graph] Instantiate Prefab failed for " + path, error);
        }
    }

    private static void registerPhysics2D(GraphNodeRegistry registry) {
        registry.register(raycast2DDefinition());
    }

    private static NodeDefinition raycast2DDefinition() {
        return dataNode(PHYSICS_RAYCAST_2D, "Raycast 2D", CATEGORY_PHYSICS, false,
                List.of(new PinDefinition("Origin X", PinType.FLOAT),
                        new PinDefinition("Origin Y", PinType.FLOAT),
                        new PinDefinition("Direction X", PinType.FLOAT),
                        new PinDefinition("Direction Y", PinType.FLOAT),
                        new PinDefinition("Distance", PinType.FLOAT)),
                List.of(new PinDefinition("Hit", PinType.BOOLEAN),
                        new PinDefinition("Distance", PinType.FLOAT),
                        new PinDefinition("Normal X", PinType.FLOAT),
                        new PinDefinition("Normal Y", PinType.FLOAT),
                        new PinDefinition("Hit Object", PinType.GAME_OBJECT)),
                List.of(),
                BuiltinNodes::performRaycast2D);
    }

    private static void performRaycast2D(NodeContext context) {
        PhysicsSystem physics = context.services().systems().get(PhysicsSystem.class);
        if (physics == null) {
            writeRaycastMiss(context);
            return;
        }
        Vector2f origin = new Vector2f(context.floatInput("Origin X"), context.floatInput("Origin Y"));
        Vector2f direction = new Vector2f(context.floatInput("Direction X"), context.floatInput("Direction Y"));
        Optional<RaycastHit2D> hit = physics.raycast2D(origin, direction,
                context.floatInput("Distance"), selfExcludingFilter(context, physics));
        hit.ifPresentOrElse(found -> writeRaycastHit(context, physics, found), () -> writeRaycastMiss(context));
    }

    private static QueryFilter selfExcludingFilter(NodeContext context, PhysicsSystem physics) {
        return physics.bodyOf(context.self())
                .map(body -> new QueryFilter(QueryFilter.ALL.mask(), body.id()))
                .orElse(QueryFilter.ALL);
    }

    private static void writeRaycastHit(NodeContext context, PhysicsSystem physics, RaycastHit2D hit) {
        context.setOutput("Hit", true);
        context.setOutput("Distance", hit.distance());
        context.setOutput("Normal X", hit.normal().x());
        context.setOutput("Normal Y", hit.normal().y());
        physics.ownerOf(hit.body()).ifPresent(owner -> context.setOutput("Hit Object", owner));
    }

    private static void writeRaycastMiss(NodeContext context) {
        context.setOutput("Hit", false);
        context.setOutput("Distance", 0.0f);
        context.setOutput("Normal X", 0.0f);
        context.setOutput("Normal Y", 0.0f);
    }

    private static void registerUtility(GraphNodeRegistry registry) {
        registry.register(logDefinition());
        registry.register(hudTextDefinition());
        registry.register(makeVectorDefinition());
        registry.register(splitVectorDefinition());
    }

    private static NodeDefinition logDefinition() {
        return execNode("utility.log", "Log", CATEGORY_UTILITY,
                List.of(PinDefinition.exec(IN_PIN), new PinDefinition("Message", PinType.STRING)),
                List.of(PinDefinition.exec(OUT_PIN)),
                List.of(new NodeSetting(LEVEL_SETTING, SettingKind.LOG_LEVEL, "Info")),
                BuiltinNodes::runLog);
    }

    private static void runLog(NodeContext context) {
        String message = context.stringInput("Message");
        switch (context.settingString(LEVEL_SETTING, "Info")) {
            case "Warning" -> context.services().logger().warn(message);
            case "Error" -> context.services().logger().error(message);
            default -> context.services().logger().info(message);
        }
        context.triggerExec(OUT_PIN);
    }

    private static NodeDefinition hudTextDefinition() {
        return execNode("utility.hudText", "Hud Text", CATEGORY_UTILITY,
                List.of(PinDefinition.exec(IN_PIN), new PinDefinition("X", PinType.FLOAT),
                        new PinDefinition("Y", PinType.FLOAT), new PinDefinition("Text", PinType.STRING)),
                List.of(PinDefinition.exec(OUT_PIN)),
                List.of(),
                context -> {
                    context.services().hud().text(context.floatInput("X"), context.floatInput("Y"),
                            context.stringInput("Text"));
                    context.triggerExec(OUT_PIN);
                });
    }

    private static NodeDefinition makeVectorDefinition() {
        return dataNode("utility.makeVector", "Make Vector", CATEGORY_UTILITY, true,
                List.of(new PinDefinition("X", PinType.FLOAT), new PinDefinition("Y", PinType.FLOAT),
                        new PinDefinition("Z", PinType.FLOAT)),
                List.of(new PinDefinition("Vector", PinType.VECTOR3)),
                List.of(),
                context -> context.setOutput("Vector", new Vector3f(context.floatInput("X"),
                        context.floatInput("Y"), context.floatInput("Z"))));
    }

    private static NodeDefinition splitVectorDefinition() {
        return dataNode("utility.splitVector", "Split Vector", CATEGORY_UTILITY, true,
                List.of(new PinDefinition("Vector", PinType.VECTOR3)),
                List.of(new PinDefinition("X", PinType.FLOAT), new PinDefinition("Y", PinType.FLOAT),
                        new PinDefinition("Z", PinType.FLOAT)),
                List.of(),
                BuiltinNodes::runSplitVector);
    }

    private static void runSplitVector(NodeContext context) {
        Vector3f vector = context.vectorInput("Vector");
        context.setOutput("X", vector.x);
        context.setOutput("Y", vector.y);
        context.setOutput("Z", vector.z);
    }

    private static NodeDefinition execNode(String typeKey, String displayName, String category,
                                           List<PinDefinition> inputs, List<PinDefinition> outputs,
                                           List<NodeSetting> settings, NodeBehavior behavior) {
        return new NodeDefinition(typeKey, displayName, category, false, false,
                inputs, outputs, settings, behavior);
    }

    private static NodeDefinition dataNode(String typeKey, String displayName, String category,
                                           boolean memoized, List<PinDefinition> inputs,
                                           List<PinDefinition> outputs, List<NodeSetting> settings,
                                           NodeBehavior behavior) {
        return new NodeDefinition(typeKey, displayName, category, memoized, false,
                inputs, outputs, settings, behavior);
    }
}
