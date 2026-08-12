package fr.epistudio.epysia.net;

import fr.epistudio.epysia.EngineServices;
import fr.epistudio.epysia.GameSystem;
import fr.epistudio.epysia.input.InputState;
import fr.epistudio.epysia.logging.ConsoleLogger;
import fr.epistudio.epysia.physics.PhysicsSystem;
import fr.epistudio.epysia.scene.Scene;

public final class NetworkReceiveSystem implements GameSystem {
    private final NetworkRuntime runtime = new NetworkRuntime(new ConsoleLogger());
    private final NetworkService service = new NetworkService(runtime);
    private EngineServices engineServices;

    public NetworkRuntime runtime() {
        return runtime;
    }

    public NetworkService service() {
        return service;
    }

    @Override
    public void initialize(EngineServices services) {
        this.engineServices = services;
        runtime.initialize(services);
    }

    private float fixedStepSeconds(float fallbackSeconds) {
        if (engineServices == null) {
            return fallbackSeconds;
        }
        return engineServices.systems().find(PhysicsSystem.class)
                .map(PhysicsSystem::fixedStepSeconds)
                .orElse(fallbackSeconds);
    }

    @Override
    public void update(Scene scene, InputState input, float deltaTimeSeconds) {
        float fixedStep = fixedStepSeconds(deltaTimeSeconds);
        runtime.setFixedTimestepSeconds(fixedStep);
        runtime.setCurrentInput(input);
        runtime.receiveTick(scene, deltaTimeSeconds);
        runtime.fixedStep(scene, fixedStep);
    }

    @Override
    public void shutdown() {
        runtime.shutdown();
    }
}
