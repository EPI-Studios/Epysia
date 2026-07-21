package fr.epistudio.epysia.render.mesh;

import fr.epistudio.epysia.render.backend.BindingSetHandle;
import fr.epistudio.epysia.render.backend.BufferHandle;
import fr.epistudio.epysia.render.backend.TextureHandle;
import fr.epistudio.epysia.render.material.Material;

record PerSubmesh(
        BufferHandle modelUbo,
        BindingSetHandle shadowBindings,
        BindingSetHandle litBindings,
        MaterialClassResources classResources,
        Material material,
        TextureHandle[] capturedTextures,
        boolean shadowMasked,
        long surfaceStructureRevision
) {
}
