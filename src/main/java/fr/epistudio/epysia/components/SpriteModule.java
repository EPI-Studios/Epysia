package fr.epistudio.epysia.components;

import fr.epistudio.epysia.EngineModule;
import fr.epistudio.epysia.SystemRegistry;

public final class SpriteModule implements EngineModule {

    @Override
    public int order() {
        return 60;
    }

    @Override
    public void registerSystems(SystemRegistry registry) {
        registry.add(new FlipbookSystem());
    }
}
