package fr.epistudio.epysia.graph;

import fr.epistudio.epysia.EngineServices;
import fr.epistudio.epysia.GameSystem;
import fr.epistudio.epysia.components.IComponent;
import fr.epistudio.epysia.gameobjects.GameObject;
import fr.epistudio.epysia.input.InputState;
import fr.epistudio.epysia.input.KeyCode;
import fr.epistudio.epysia.input.MouseButton;
import fr.epistudio.epysia.reflection.ComponentRegistry;
import fr.epistudio.epysia.scene.Scene;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

public final class GraphSystem implements GameSystem {

    private final GraphJsonCodec codec = new GraphJsonCodec();
    private final Map<GraphComponent, GraphInstance> instances = new IdentityHashMap<>();
    private final Set<GraphComponent> failedLoads = Collections.newSetFromMap(new IdentityHashMap<>());
    private final List<GraphComponent> cachedComponents = new ArrayList<>();
    private Scene cachedScene;
    private long cachedModificationCount = Long.MIN_VALUE;
    private EngineServices services;
    private GraphInterpreter interpreter;
    private StateMachineExecutor stateMachineExecutor;
    private ComponentRegistry providedComponentRegistry;

    @Override
    public void initialize(EngineServices services) {
        this.services = services;
        GraphNodeRegistry registry = GraphNodeRegistry.withBuiltins();
        registry.setClassResolver(this::resolveClass);
        this.interpreter = new GraphInterpreter(registry);
        this.stateMachineExecutor = new StateMachineExecutor(interpreter);
        if (providedComponentRegistry != null) {
            interpreter.setComponentRegistry(providedComponentRegistry);
        }
    }

    public GraphInterpreter interpreter() {
        return interpreter;
    }

    public void setComponentRegistry(ComponentRegistry componentRegistry) {
        providedComponentRegistry = componentRegistry;
        if (interpreter != null) {
            interpreter.setComponentRegistry(componentRegistry);
        }
    }

    private Optional<Class<?>> resolveClass(String className) {
        try {
            return Optional.of(Class.forName(className, false, GraphSystem.class.getClassLoader()));
        } catch (ClassNotFoundException | LinkageError missing) {
            return resolveFromScene(className);
        }
    }

    private Optional<Class<?>> resolveFromScene(String className) {
        if (cachedScene == null) {
            return Optional.empty();
        }
        for (GameObject gameObject : cachedScene.gameObjects()) {
            for (IComponent component : gameObject.components()) {
                if (component.getClass().getName().equals(className)) {
                    return Optional.of(component.getClass());
                }
            }
        }
        return Optional.empty();
    }

    @Override
    public void update(Scene scene, InputState input, float deltaTimeSeconds) {
        refreshCacheIfStructureChanged(scene);
        for (GraphComponent component : cachedComponents) {
            if (isOwnerActive(component)) {
                updateComponent(component, input, deltaTimeSeconds);
            }
        }
    }

    private void updateComponent(GraphComponent component, InputState input, float deltaTimeSeconds) {
        Optional<GraphInstance> loaded = ensureInstance(component);
        if (loaded.isEmpty()) {
            return;
        }
        GraphInstance instance = loaded.get();
        instance.resetTickBudget(GraphInterpreter.EXECUTION_BUDGET_PER_TICK);
        instance.setInputState(input);
        fireStartIfNeeded(instance);
        fireInputEvents(instance, input);
        interpreter.fireEventNodes(instance, BuiltinNodes.EVENT_ON_UPDATE,
                Map.of(BuiltinNodes.DELTA_TIME_PIN, deltaTimeSeconds), services);
        if (instance.asset().kind() == GraphKind.STATE_MACHINE) {
            stateMachineExecutor.step(instance, deltaTimeSeconds, services);
        }
    }

    private void fireStartIfNeeded(GraphInstance instance) {
        if (!instance.startFired()) {
            instance.markStartFired();
            interpreter.fireEventNodes(instance, BuiltinNodes.EVENT_ON_START, Map.of(), services);
        }
    }

    private void fireInputEvents(GraphInstance instance, InputState input) {
        fireKeyEvents(instance, input, BuiltinNodes.EVENT_ON_KEY_PRESSED, true);
        fireKeyEvents(instance, input, BuiltinNodes.EVENT_ON_KEY_RELEASED, false);
        fireMouseButtonEvents(instance, input);
    }

    private void fireKeyEvents(GraphInstance instance, InputState input, String typeKey, boolean pressed) {
        for (GraphNode node : instance.asset().nodesOfType(typeKey)) {
            Optional<KeyCode> key = parseKey(node);
            if (key.isPresent() && keyEventHappened(input, key.get(), pressed)) {
                interpreter.fireEventNode(instance, node, Map.of(), services);
            }
        }
    }

    private static boolean keyEventHappened(InputState input, KeyCode key, boolean pressed) {
        return pressed ? input.wasKeyPressed(key) : input.wasKeyReleased(key);
    }

    private static Optional<KeyCode> parseKey(GraphNode node) {
        String name = GraphValues.asString(node.values().getOrDefault(BuiltinNodes.KEY_SETTING, "SPACE"));
        try {
            return Optional.of(KeyCode.valueOf(name));
        } catch (IllegalArgumentException unknown) {
            return Optional.empty();
        }
    }

    private void fireMouseButtonEvents(GraphInstance instance, InputState input) {
        for (GraphNode node : instance.asset().nodesOfType(BuiltinNodes.EVENT_ON_MOUSE_BUTTON_PRESSED)) {
            Optional<MouseButton> button = parseButton(node);
            if (button.isPresent() && input.wasMouseButtonPressed(button.get())) {
                interpreter.fireEventNode(instance, node, Map.of(), services);
            }
        }
    }

    private static Optional<MouseButton> parseButton(GraphNode node) {
        String name = GraphValues.asString(node.values().getOrDefault(BuiltinNodes.BUTTON_SETTING, "LEFT"));
        try {
            return Optional.of(MouseButton.valueOf(name));
        } catch (IllegalArgumentException unknown) {
            return Optional.empty();
        }
    }

    private Optional<GraphInstance> ensureInstance(GraphComponent component) {
        GraphInstance existing = instances.get(component);
        if (existing != null) {
            return Optional.of(existing);
        }
        if (failedLoads.contains(component) || component.graphPath().isEmpty()) {
            return Optional.empty();
        }
        return loadInstance(component);
    }

    private Optional<GraphInstance> loadInstance(GraphComponent component) {
        Optional<GameObject> owner = component.owner();
        if (owner.isEmpty()) {
            return Optional.empty();
        }
        try {
            GraphAsset asset = codec.readFromFile(Path.of(component.graphPath()));
            GraphInstance instance = new GraphInstance(asset, component.graphPath(),
                    owner.get(), component.variableOverrides());
            instances.put(component, instance);
            component.attachRuntime(new GraphRuntimeLink(interpreter, services, instance));
            return Optional.of(instance);
        } catch (IOException | RuntimeException error) {
            failedLoads.add(component);
            services.logger().error("[Graph] Could not load graph " + component.graphPath(), error);
            return Optional.empty();
        }
    }

    private boolean isOwnerActive(GraphComponent component) {
        return component.owner().map(GameObject::active).orElse(false);
    }

    private void refreshCacheIfStructureChanged(Scene scene) {
        long modificationCount = scene.modificationCount();
        if (scene == cachedScene && modificationCount == cachedModificationCount) {
            return;
        }
        cachedScene = scene;
        cachedModificationCount = modificationCount;
        collectComponents(scene);
        releaseRemovedInstances();
    }

    private void collectComponents(Scene scene) {
        cachedComponents.clear();
        cachedComponents.addAll(scene.componentsOf(GraphComponent.class));
    }

    private void releaseRemovedInstances() {
        instances.keySet().removeIf(component -> {
            if (cachedComponents.contains(component)) {
                return false;
            }
            component.detachRuntime();
            return true;
        });
        failedLoads.removeIf(component -> !cachedComponents.contains(component));
    }

    public void resetForPlaySession() {
        for (GraphComponent component : instances.keySet()) {
            component.detachRuntime();
        }
        instances.clear();
        failedLoads.clear();
        cachedComponents.clear();
        cachedScene = null;
        cachedModificationCount = Long.MIN_VALUE;
    }

    @Override
    public void shutdown() {
        resetForPlaySession();
        services = null;
    }
}
