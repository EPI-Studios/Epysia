package fr.epistudio.epysia.render.mesh;

import java.util.List;

public record ObjParseResult(MeshData mesh, List<String> materialSlotNames, List<String> mtllibPaths) {

    public ObjParseResult {
        materialSlotNames = List.copyOf(materialSlotNames);
        mtllibPaths = List.copyOf(mtllibPaths);
    }
}
