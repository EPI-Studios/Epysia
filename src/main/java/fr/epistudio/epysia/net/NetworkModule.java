package fr.epistudio.epysia.net;

import fr.epistudio.epysia.EngineModule;
import fr.epistudio.epysia.SystemRegistry;

public final class NetworkModule implements EngineModule {
    @Override
    public int order() {
        return 10;
    }

    @Override
    public void registerSystems(SystemRegistry registry) {
        registry.add(new NetworkReceiveSystem());
    }
}
