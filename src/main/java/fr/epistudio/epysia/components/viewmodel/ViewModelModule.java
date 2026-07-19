package fr.epistudio.epysia.components.viewmodel;

import fr.epistudio.epysia.EngineModule;
import fr.epistudio.epysia.SystemRegistry;

public final class ViewModelModule implements EngineModule {

    @Override
    public int order() {
        return 150;
    }

    @Override
    public void registerSystems(SystemRegistry registry) {
        registry.add(new ViewModelSystem());
    }
}
