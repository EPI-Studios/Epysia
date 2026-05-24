package fr.epistudio.epysia.editor.icons;

import com.miry.graphics.Texture;
import com.miry.ui.render.UiRenderer;
import org.lwjgl.BufferUtils;
import org.lwjgl.stb.STBImage;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.IntBuffer;
import java.util.HashMap;
import java.util.Map;

public final class EditorIcons {

    public static final String NODE_3D = "Node3D";
    public static final String MESH_INSTANCE_3D = "MeshInstance3D";
    public static final String CAMERA_3D = "Camera3D";
    public static final String DIRECTIONAL_LIGHT = "DirectionalLight3D";
    public static final String OMNI_LIGHT = "OmniLight3D";
    public static final String SPOT_LIGHT = "SpotLight3D";
    public static final String CHARACTER_BODY_3D = "CharacterBody3D";
    public static final String COLLISION_SHAPE_3D = "CollisionShape3D";
    public static final String RIGID_BODY_3D = "RigidBody3D";
    public static final String STATIC_BODY_3D = "StaticBody3D";
    public static final String MESH = "Mesh";
    public static final String FOLDER = "Folder";
    public static final String FILE = "File";
    public static final String SCRIPT = "Script";
    public static final String MATERIAL = "Material";
    public static final String ANIMATION_PLAYER = "AnimationPlayer";
    public static final String ADD = "Add";
    public static final String REMOVE = "Remove";
    public static final String DUPLICATE = "Duplicate";
    public static final String SAVE = "Save";
    public static final String LOAD = "Load";
    public static final String UNDO = "Undo";
    public static final String REDO = "Redo";
    public static final String PLAY = "Play";
    public static final String PAUSE = "Pause";
    public static final String STOP = "Stop";
    public static final String TOOL_SELECT = "ToolSelect";
    public static final String TOOL_MOVE = "ToolMove";
    public static final String TOOL_ROTATE = "ToolRotate";
    public static final String TOOL_SCALE = "ToolScale";
    public static final String SNAP = "Snap";
    public static final String GRID = "Grid";
    public static final String LOCK = "Lock";
    public static final String UNLOCK = "Unlock";
    public static final String VISIBLE = "GuiVisibilityVisible";
    public static final String HIDDEN = "GuiVisibilityHidden";

    private static final Map<String, Texture> CACHE = new HashMap<>();

    private EditorIcons() {
    }

    public static Texture texture(String name) {
        Texture existing = CACHE.get(name);
        if (existing != null) {
            return existing;
        }
        Texture loaded = loadFromResources(name);
        CACHE.put(name, loaded);
        return loaded;
    }

    public static void draw(UiRenderer renderer, String name, float x, float y, float size, int argb) {
        Texture texture = texture(name);
        if (texture == null) {
            return;
        }
        renderer.drawTexturedRect(texture, x, y, size, size, argb);
    }

    private static Texture loadFromResources(String name) {
        String path = "/editor/icons/png/" + name + ".png";
        try (InputStream stream = EditorIcons.class.getResourceAsStream(path)) {
            if (stream == null) {
                System.err.println("EditorIcons: missing resource " + path);
                return null;
            }
            byte[] bytes = stream.readAllBytes();
            ByteBuffer encoded = BufferUtils.createByteBuffer(bytes.length);
            encoded.put(bytes).flip();
            IntBuffer width = BufferUtils.createIntBuffer(1);
            IntBuffer height = BufferUtils.createIntBuffer(1);
            IntBuffer channels = BufferUtils.createIntBuffer(1);
            ByteBuffer pixels = STBImage.stbi_load_from_memory(encoded, width, height, channels, 4);
            if (pixels == null) {
                System.err.println("EditorIcons: stbi_load failed for " + path + ": " + STBImage.stbi_failure_reason());
                return null;
            }
            try {
                Texture texture = new Texture();
                texture.setFilteringLinear();
                texture.uploadRgba(width.get(0), height.get(0), pixels);
                return texture;
            } finally {
                STBImage.stbi_image_free(pixels);
            }
        } catch (IOException error) {
            System.err.println("EditorIcons: failed to load " + path + ": " + error.getMessage());
            return null;
        }
    }
}
