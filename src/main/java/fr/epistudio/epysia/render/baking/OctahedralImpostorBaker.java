package fr.epistudio.epysia.render.baking;

import fr.epistudio.epysia.render.backend.RenderBackend;
import fr.epistudio.epysia.render.shader.ShaderLoader;

import java.nio.file.Path;
import java.util.List;

public final class OctahedralImpostorBaker implements ImpostorBaker {

    private final RenderBackend backend;
    private final ShaderLoader shaderLoader;

    public OctahedralImpostorBaker(RenderBackend backend, ShaderLoader shaderLoader) {
        this.backend = backend;
        this.shaderLoader = shaderLoader;
    }

    @Override
    public List<Path> bake(ImpostorBakeRequest request) {
        if (request.parts().isEmpty() || request.hasSkinnedPart()
                || !request.settings().shouldBake(request.triangleCount(), request.parts().size())) {
            return List.of();
        }
        ImpostorBakeSession session = new ImpostorBakeSession(backend, shaderLoader, request);
        try {
            return session.run();
        } finally {
            session.destroy();
        }
    }
}
