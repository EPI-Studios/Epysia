package fr.epistudio.epysia.editor.icons;

import fr.epistudio.epysia.editor.shell.EditorStyle;
import imgui.ImGui;

public final class IconWidgets {

    private static final float FULL_ALPHA = 1.0f;

    private final IconAtlas atlas;

    public IconWidgets(IconAtlas atlas) {
        this.atlas = atlas;
    }

    public int atlasTextureId(EditorIcon icon) {
        return atlas.textureId(icon);
    }

    public void draw(EditorIcon icon, float size) {
        ImGui.image(atlas.textureId(icon), size, size);
    }

    public void drawTinted(EditorIcon icon, float size, float red, float green, float blue) {
        ImGui.image(atlas.textureId(icon), size, size, 0.0f, 0.0f, 1.0f, 1.0f, red, green, blue, FULL_ALPHA);
    }

    public void drawInline(EditorIcon icon, float size) {
        draw(icon, size);
        ImGui.sameLine();
    }

    public boolean iconButton(String id, EditorIcon icon, float size) {
        ImGui.pushID(id);
        boolean clicked = ImGui.imageButton(atlas.textureId(icon), size, size);
        ImGui.popID();
        return clicked;
    }

    public boolean toggleButton(String id, EditorIcon icon, float size, boolean active) {
        if (active) {
            ImGui.pushStyleColor(imgui.flag.ImGuiCol.Button, EditorStyle.COLOR_WIDGET_ACTIVE);
            ImGui.pushStyleColor(imgui.flag.ImGuiCol.ButtonHovered, EditorStyle.COLOR_ACCENT);
        }
        boolean clicked = iconButton(id, icon, size);
        if (active) {
            ImGui.popStyleColor(2);
        }
        return clicked;
    }
}
