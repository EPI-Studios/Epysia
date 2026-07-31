package fr.epistudio.epysia.editor.importer;

import de.javagl.jgltf.model.MeshModel;
import fr.epistudio.epysia.render.mesh.MeshData;

import java.nio.file.Path;

record ImportedMesh(MeshModel model, String name, Path file, MeshData data) {
}
