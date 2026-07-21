package fr.epistudio.epysia.graph;

import fr.epistudio.epysia.EngineModule;
import fr.epistudio.epysia.SystemRegistry;

public final class GraphModule implements EngineModule {

    @Override
    public int order() {
        return 55;
    }

    @Override
    public void registerSystems(SystemRegistry registry) {
        registry.add(new GraphSystem());
    }
}
