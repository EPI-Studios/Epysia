package fr.epistudio.epysia.render.baking;

import fr.epistudio.epysia.render.mesh.MeshData;
import org.joml.Matrix4f;

import java.util.List;

public record ImpostorPart(MeshData mesh, List<ImpostorSurface> surfaces, Matrix4f transform) {

    public ImpostorPart {
        surfaces = List.copyOf(surfaces);
        transform = new Matrix4f(transform);
    }

    public static ImpostorPart untransformed(MeshData mesh, List<ImpostorSurface> surfaces) {
        return new ImpostorPart(mesh, surfaces, new Matrix4f());
    }

    public ImpostorSurface surfaceForSlot(int materialSlot) {
        return materialSlot >= 0 && materialSlot < surfaces.size()
                ? surfaces.get(materialSlot) : ImpostorSurface.untextured();
    }

    public int triangleCount() {
        return mesh.indices().length / 3;
    }
}
