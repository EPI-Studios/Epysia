package fr.epistudio.epysia.assets.epymesh;

import fr.epistudio.epysia.render.mesh.MeshData;

import java.util.Optional;

public record EpyMesh(MeshData mesh, Optional<BakedCollider> collider) {
}
