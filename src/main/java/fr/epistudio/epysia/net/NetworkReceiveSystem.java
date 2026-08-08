package fr.epistudio.epysia.net;

import fr.epistudio.epysia.EngineServices;
import fr.epistudio.epysia.GameSystem;
import fr.epistudio.epysia.input.InputState;
import fr.epistudio.epysia.logging.ConsoleLogger;
import fr.epistudio.epysia.scene.Scene;

public final class NetworkReceiveSystem implements GameSystem {
    private final NetworkRuntime runtime = new NetworkRuntime(new ConsoleLogger());
    private final NetworkService service = new NetworkService(runtime);

    public NetworkRuntime runtime() {
        return runtime;
    }

    public NetworkService service() {
        return service;
    }

    @Override
    public void initialize(EngineServices services) {
        runtime.initialize(services);
    }

    @Override
    public void update(Scene scene, InputState input, float deltaTimeSeconds) {
        runtime.setFixedTimestepSeconds(deltaTimeSeconds);
        runtime.receiveTick(scene, deltaTimeSeconds);
    }

    @Override
    public void shutdown() {
        runtime.shutdown();
    }
}
