package fr.epistudio.epysia.render.mesh;

import fr.epistudio.epysia.render.backend.MeshHandle;

public record UploadedSubmesh(MeshHandle handle, int materialSlot) {
}
