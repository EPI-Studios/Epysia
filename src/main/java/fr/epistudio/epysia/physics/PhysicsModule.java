package fr.epistudio.epysia.physics;

import fr.epistudio.epysia.EngineModule;
import fr.epistudio.epysia.SystemRegistry;

public final class PhysicsModule implements EngineModule {

    @Override
    public int order() {
        return 100;
    }

    @Override
    public void registerSystems(SystemRegistry registry) {
        registry.add(new PhysicsSystem());
    }
}
