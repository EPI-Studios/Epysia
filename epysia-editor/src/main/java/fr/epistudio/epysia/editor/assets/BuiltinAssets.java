package fr.epistudio.epysia.editor.assets;

import fr.epistudio.epysia.assets.loaders.MeshAssetLoader;
import fr.epistudio.epysia.render.mesh.BuiltinMeshes;

import java.util.List;

public final class BuiltinAssets {

    public static final String FOLDER_LABEL = "Built-in";

    private static final List<String> MESH_PRESETS = List.of(
            BuiltinMeshes.CUBE, BuiltinMeshes.SPHERE, BuiltinMeshes.PLANE, BuiltinMeshes.CAPSULE);

    private BuiltinAssets() {
    }

    public static List<AssetEntry> entries() {
        return MESH_PRESETS.stream()
                .map(preset -> AssetEntry.builtin(MeshAssetLoader.PRESET_PREFIX + preset, capitalize(preset)))
                .toList();
    }

    private static String capitalize(String preset) {
        return Character.toUpperCase(preset.charAt(0)) + preset.substring(1);
    }
}
