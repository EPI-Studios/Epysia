package fr.epistudio.epysia.steam;

import fr.epistudio.epysia.EngineServices;
import fr.epistudio.epysia.GameSystem;
import fr.epistudio.epysia.input.InputState;
import fr.epistudio.epysia.logging.ConsoleLogger;
import fr.epistudio.epysia.scene.Scene;

public final class SteamCallbackSystem implements GameSystem {

    private final SteamRuntime runtime = new SteamRuntime(new ConsoleLogger());
    private final SteamService service = new SteamService(runtime);

    public SteamService service() {
        return service;
    }

    @Override
    public void initialize(EngineServices services) {
        SteamStartup.requested().ifPresent(service::start);
    }

    @Override
    public void update(Scene scene, InputState input, float deltaTimeSeconds) {
        service.runCallbacks();
    }

    @Override
    public void shutdown() {
        service.stop();
    }
}
