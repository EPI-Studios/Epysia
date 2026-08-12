package fr.epistudio.epysia.editor.ui.settings;

import fr.epistudio.epysia.i18n.TextKey;
import fr.epistudio.epysia.project.ProjectQuality;
import fr.epistudio.epysia.project.RenderSettings;
import fr.epistudio.epysia.render.GraphicsApi;
import imgui.ImGui;
import imgui.type.ImInt;
import imgui.type.ImString;

public final class WindowSection {

    private static final int WINDOW_TITLE_CAPACITY = 128;
    private static final String[] RENDER_APIS = {"OpenGL", "Vulkan"};

    private final SettingsChrome chrome;
    private final ImString windowTitle = new ImString(WINDOW_TITLE_CAPACITY);
    private final ImInt windowWidth = new ImInt();
    private final ImInt windowHeight = new ImInt();
    private final ImInt maximumFrameRate = new ImInt();

    private boolean verticalSync;
    private int renderApiIndex;

    public WindowSection(SettingsChrome chrome) {
        this.chrome = chrome;
    }

    public void loadRender(RenderSettings render) {
        renderApiIndex = render.api() == GraphicsApi.VULKAN ? 1 : 0;
    }

    public RenderSettings buildRender() {
        return new RenderSettings(renderApiIndex == 1 ? GraphicsApi.VULKAN : GraphicsApi.OPENGL);
    }

    public void load(ProjectQuality quality) {
        windowTitle.set(quality.windowTitle());
        windowWidth.set(quality.windowWidth());
        windowHeight.set(quality.windowHeight());
        verticalSync = quality.verticalSync();
        maximumFrameRate.set(quality.maximumFrameRate());
    }

    public ImString titleBuffer() {
        return windowTitle;
    }

    public String title() {
        return windowTitle.get().trim();
    }

    public int width() {
        return windowWidth.get();
    }

    public int height() {
        return windowHeight.get();
    }

    public boolean verticalSync() {
        return verticalSync;
    }

    public int maximumFrameRate() {
        return maximumFrameRate.get();
    }

    private void renderApiCombo() {
        if (!ImGui.beginCombo("##value", RENDER_APIS[renderApiIndex])) {
            return;
        }
        for (int index = 0; index < RENDER_APIS.length; index++) {
            if (ImGui.selectable(RENDER_APIS[index], index == renderApiIndex)) {
                renderApiIndex = index;
            }
        }
        ImGui.endCombo();
    }

    public void render() {
        chrome.row("Width", () -> ImGui.dragInt("##value", windowWidth.getData(), 8.0f,
                ProjectQuality.MIN_WINDOW_SIZE, ProjectQuality.MAX_WINDOW_SIZE));
        chrome.row("Height", () -> ImGui.dragInt("##value", windowHeight.getData(), 8.0f,
                ProjectQuality.MIN_WINDOW_SIZE, ProjectQuality.MAX_WINDOW_SIZE));
        verticalSync = chrome.toggleRow("Vertical sync", verticalSync);
        chrome.row("Max frame rate", () -> ImGui.dragInt("##value", maximumFrameRate.getData(), 1.0f, 0,
                ProjectQuality.MAX_FRAME_RATE_LIMIT));
        chrome.row("Render API", this::renderApiCombo);
        chrome.hint(TextKey.EDITOR_SETTINGS_DIALOG_FRAME_RATE_HELP);
    }
}
