package fr.epistudio.epysia.scripting;

import fr.epistudio.epysia.EngineModule;
import fr.epistudio.epysia.SystemRegistry;

public final class ScriptingModule implements EngineModule {

    @Override
    public int order() {
        return 50;
    }

    @Override
    public void registerSystems(SystemRegistry registry) {
        registry.add(new ScriptDispatcherSystem());
    }
}
