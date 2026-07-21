package fr.epistudio.epysia.editor.assets;

public enum AssetType {
    PRESET("Presets"),
    MESH("Meshes"),
    TEXTURE("Textures"),
    AUDIO("Audio"),
    SCRIPT("Scripts"),
    SHADER("Shaders"),
    PREFAB("Prefabs"),
    SCENE("Scenes"),
    GRAPH("Graphs"),
    MATERIAL("Materials"),
    CLIP("Clips"),
    OTHER("Other");

    private final String pluralLabel;

    AssetType(String pluralLabel) {
        this.pluralLabel = pluralLabel;
    }

    public String pluralLabel() {
        return pluralLabel;
    }
}
