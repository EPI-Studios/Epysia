package fr.epistudio.epysia.components;

import fr.epistudio.epysia.render.material.Material;
import fr.epistudio.epysia.render.mesh.UploadedMesh;

import java.util.Optional;

public interface MeshRenderSource {

    UploadedMesh meshOrNull();

    Optional<Material> materialForSlot(int slot);

    int layerMask();

    boolean castsShadows();

    float visibilityRangeBegin();

    float visibilityRangeEnd();
}
