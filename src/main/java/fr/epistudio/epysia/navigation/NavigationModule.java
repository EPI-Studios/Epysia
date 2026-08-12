package fr.epistudio.epysia.navigation;

import fr.epistudio.epysia.EngineModule;
import fr.epistudio.epysia.SystemRegistry;

public final class NavigationModule implements EngineModule {
    @Override
    public int order() {
        return 5;
    }

    @Override
    public void registerSystems(SystemRegistry registry) {
        registry.add(new NavigationSystem());
    }
}
