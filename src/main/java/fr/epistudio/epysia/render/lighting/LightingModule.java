package fr.epistudio.epysia.render.lighting;

import fr.epistudio.epysia.EngineModule;
import fr.epistudio.epysia.SystemRegistry;

public final class LightingModule implements EngineModule {
    @Override
    public int order() {
        return 70;
    }

    @Override
    public boolean runsHeadless() {
        return false;
    }

    @Override
    public void registerSystems(SystemRegistry registry) {
        registry.add(new ProbeRefreshSystem());
    }
}
