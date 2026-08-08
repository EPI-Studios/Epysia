package fr.epistudio.epysia.net;

import fr.epistudio.epysia.EngineModule;
import fr.epistudio.epysia.SystemRegistry;

public final class NetworkSendModule implements EngineModule {
    @Override
    public int order() {
        return 900;
    }

    @Override
    public void registerSystems(SystemRegistry registry) {
        registry.add(new NetworkSendSystem());
    }
}
