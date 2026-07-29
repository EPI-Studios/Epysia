package fr.epistudio.epysia.editor.icons;

import fr.epistudio.epysia.editor.assets.AssetType;

public final class AssetTypeIcons {

    private AssetTypeIcons() {
    }

    public static EditorIcon iconFor(AssetType type) {
        return switch (type) {
            case MESH -> EditorIcon.MESH;
            case PRESET -> EditorIcon.MESH_INSTANCE_3D;
            case TEXTURE -> EditorIcon.TEXTURE_2D;
            case ATLAS -> EditorIcon.ATLAS_TEXTURE;
            case MATERIAL -> EditorIcon.STANDARD_MATERIAL;
            case SHADER -> EditorIcon.SHADER;
            case SCRIPT -> EditorIcon.SCRIPT;
            case SCENE -> EditorIcon.PACKED_SCENE;
            case PREFAB -> EditorIcon.NODE_3D;
            case GRAPH -> EditorIcon.GRAPH_EDIT;
            case CLIP -> EditorIcon.ANIMATION;
            case AUDIO -> EditorIcon.AUDIO_STREAM;
            case OTHER -> EditorIcon.FILE;
        };
    }
}
