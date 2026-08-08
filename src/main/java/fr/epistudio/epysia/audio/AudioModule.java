package fr.epistudio.epysia.audio;

import fr.epistudio.epysia.EngineModule;
import fr.epistudio.epysia.SystemRegistry;
import fr.epistudio.epysia.logging.ConsoleLogger;

public final class AudioModule implements EngineModule {
    @Override
    public int order() {
        return 200;
    }

    @Override
    public void registerSystems(SystemRegistry registry) {
        registry.add(new AudioSystem(new ConsoleLogger()));
    }

    @Override
    public boolean runsHeadless() {
        return false;
    }
}
