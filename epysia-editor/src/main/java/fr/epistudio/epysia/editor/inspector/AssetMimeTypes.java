package fr.epistudio.epysia.editor.inspector;

import fr.epistudio.epysia.assets.epyatlas.SpriteAtlas;
import fr.epistudio.epysia.assets.epyinstances.InstanceTransforms;
import fr.epistudio.epysia.render.material.Material;
import fr.epistudio.epysia.render.backend.TextureHandle;
import fr.epistudio.epysia.render.mesh.UploadedMesh;

public final class AssetMimeTypes {

    public static final String MESH = "asset/mesh";
    public static final String TEXTURE = "asset/texture";
    public static final String AUDIO = "asset/audio";
    public static final String PREFAB = "asset/prefab";
    public static final String SHADER = "asset/shader";
    public static final String SCENE = "asset/scene";
    public static final String GRAPH = "asset/graph";
    public static final String MATERIAL = "asset/material";
    public static final String CLIP = "asset/clip";
    public static final String ATLAS = "asset/atlas";
    public static final String INSTANCES = "asset/instances";
    public static final String NONE = "";

    public static final String[] ALL =
            {MESH, TEXTURE, AUDIO, PREFAB, SHADER, SCENE, GRAPH, MATERIAL, CLIP, ATLAS, INSTANCES};

    private AssetMimeTypes() {
    }

    public static String forAssetType(Class<?> type) {
        if (type == UploadedMesh.class) {
            return MESH;
        }
        if (type == TextureHandle.class) {
            return TEXTURE;
        }
        if (type == SpriteAtlas.class) {
            return ATLAS;
        }
        if (type == InstanceTransforms.class) {
            return INSTANCES;
        }
        if (Material.class.isAssignableFrom(type)) {
            return MATERIAL;
        }
        return NONE;
    }
}
