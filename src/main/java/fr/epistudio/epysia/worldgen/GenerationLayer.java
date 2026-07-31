package fr.epistudio.epysia.worldgen;

import fr.epistudio.epysia.EngineServices;

import java.util.List;

public abstract class GenerationLayer<T> {

    public abstract float chunkSize();

    public abstract T generate(ChunkCoordinate coordinate, LayerContext context);

    public List<LayerDependency> dependencies() {
        return List.of();
    }

    public void attach(ChunkCoordinate coordinate, T data, EngineServices services) {
    }

    public void detach(ChunkCoordinate coordinate, T data, EngineServices services) {
    }

    public void shutdown(EngineServices services) {
    }

    public String name() {
        return getClass().getSimpleName();
    }
}
