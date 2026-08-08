package fr.epistudio.epysia.net;

import fr.epistudio.epysia.EngineServices;
import fr.epistudio.epysia.GameSystem;
import fr.epistudio.epysia.input.InputState;
import fr.epistudio.epysia.scene.Scene;

public final class NetworkSendSystem implements GameSystem {
    private NetworkRuntime runtime;

    @Override
    public void initialize(EngineServices services) {
        runtime = services.systems().get(NetworkReceiveSystem.class).runtime();
    }

    @Override
    public void update(Scene scene, InputState input, float deltaTimeSeconds) {
        if (runtime == null) {
            return;
        }
        runtime.sendTick(scene, input, deltaTimeSeconds);
    }
}
