package fr.epistudio.epysia.editor.icons;

public enum EditorIcon {
    ACTION_COPY("ActionCopy"),
    ACTION_PASTE("ActionPaste"),
    ADD("Add"),
    BUCKET("Bucket"),
    CANVAS_LAYER("CanvasLayer"),
    ANIMATION_PLAYER("AnimationPlayer"),
    CAMERA_3D("Camera3D"),
    CHARACTER_BODY_3D("CharacterBody3D"),
    COLLISION_SHAPE_3D("CollisionShape3D"),
    DIRECTIONAL_LIGHT_3D("DirectionalLight3D"),
    COLOR_PICK("ColorPick"),
    DUPLICATE("Duplicate"),
    EDIT("Edit"),
    ERASER("Eraser"),
    FILE("File"),
    FOLDER("Folder"),
    GRID("Grid"),
    VISIBILITY_HIDDEN("GuiVisibilityHidden"),
    VISIBILITY_VISIBLE("GuiVisibilityVisible"),
    LINE("Line"),
    LOAD("Load"),
    LOCK("Lock"),
    MESH("Mesh"),
    MESH_INSTANCE_3D("MeshInstance3D"),
    NODE_3D("Node3D"),
    OMNI_LIGHT_3D("OmniLight3D"),
    PAUSE("Pause"),
    PLAY("Play"),
    REDO("Redo"),
    REMOVE("Remove"),
    RIGID_BODY_3D("RigidBody3D"),
    SAVE("Save"),
    SCRIPT("Script"),
    RECTANGLE("Rectangle"),
    SNAP("Snap"),
    SPOT_LIGHT_3D("SpotLight3D"),
    STATIC_BODY_3D("StaticBody3D"),
    STOP("Stop"),
    TERRAIN_CONNECT("TerrainConnect"),
    TERRAIN_MATCH_CORNERS("TerrainMatchCorners"),
    TERRAIN_MATCH_CORNERS_AND_SIDES("TerrainMatchCornersAndSides"),
    TERRAIN_MATCH_SIDES("TerrainMatchSides"),
    TOOL_MOVE("ToolMove"),
    TOOL_ROTATE("ToolRotate"),
    TOOL_SCALE("ToolScale"),
    TOOL_SELECT("ToolSelect"),
    UNLOCK("Unlock");

    private final String fileName;

    EditorIcon(String fileName) {
        this.fileName = fileName;
    }

    public String resourcePath() {
        return "/editor/icons/png/" + fileName + ".png";
    }
}
