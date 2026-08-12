package fr.epistudio.epysia.steam;

import fr.epistudio.epysia.EngineModule;
import fr.epistudio.epysia.SystemRegistry;

public final class SteamModule implements EngineModule {

    private static final int ORDER = 5;

    @Override
    public int order() {
        return ORDER;
    }

    @Override
    public void registerSystems(SystemRegistry registry) {
        registry.add(new SteamCallbackSystem());
    }
}
