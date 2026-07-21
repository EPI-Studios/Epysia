package fr.epistudio.epysia.editor.shell;

import imgui.ImFont;
import imgui.ImGui;
import imgui.ImGuiStyle;
import imgui.flag.ImGuiCol;

import java.util.Optional;

public final class EditorStyle {

    public static final int COLOR_WINDOW_BACKGROUND = rgb(30, 30, 30);
    public static final int COLOR_PANEL_BACKGROUND = rgb(37, 37, 38);
    public static final int COLOR_HEADER_BACKGROUND = rgb(45, 45, 48);
    public static final int COLOR_WIDGET_BACKGROUND = rgb(60, 60, 60);
    public static final int COLOR_WIDGET_HOVER = rgb(78, 78, 80);
    public static final int COLOR_WIDGET_ACTIVE = rgb(9, 71, 113);
    public static final int COLOR_OUTLINE = rgb(24, 24, 24);
    public static final int COLOR_TEXT = rgb(212, 212, 212);
    public static final int COLOR_TEXT_MUTED = rgb(133, 133, 133);
    public static final int COLOR_ACCENT = rgb(0, 122, 204);
    public static final int COLOR_ACCENT_HOVER = rgb(28, 151, 234);
    public static final int COLOR_DANGER = rgb(232, 81, 73);
    public static final int COLOR_WARNING = rgb(224, 182, 90);
    public static final int COLOR_SUCCESS = rgb(122, 184, 95);
    public static final int COLOR_SYSTEM = rgb(107, 198, 224);

    public static final float WINDOW_ROUNDING = 4.0f;
    public static final float FRAME_ROUNDING = 3.0f;
    public static final float POPUP_ROUNDING = 4.0f;
    public static final float SCROLLBAR_ROUNDING = 6.0f;
    public static final float GRAB_ROUNDING = 3.0f;
    public static final float TAB_ROUNDING = 3.0f;
    public static final float WINDOW_PADDING = 8.0f;
    public static final float FRAME_PADDING_X = 8.0f;
    public static final float FRAME_PADDING_Y = 4.0f;
    public static final float ITEM_SPACING_X = 8.0f;
    public static final float ITEM_SPACING_Y = 5.0f;
    public static final float INNER_SPACING = 5.0f;
    public static final float INDENT_SPACING = 14.0f;
    public static final float SCROLLBAR_SIZE = 12.0f;
    public static final float BORDER_SIZE = 1.0f;

    public static final float ICON_SIZE_SMALL = 14.0f;
    public static final float ICON_SIZE_MEDIUM = 16.0f;
    public static final float ICON_SIZE_TOOLBAR = 18.0f;
    public static final float FONT_PIXEL_HEIGHT = 16.0f;
    public static final float MONOSPACE_FONT_PIXEL_HEIGHT = 15.0f;

    private static ImFont monospaceFont;

    public static void setMonospaceFont(ImFont font) {
        monospaceFont = font;
    }

    public static Optional<ImFont> monospaceFont() {
        return Optional.ofNullable(monospaceFont);
    }

    private EditorStyle() {
    }

    public static void apply() {
        applyMetrics(ImGui.getStyle());
        applyColors();
    }

    private static void applyMetrics(ImGuiStyle style) {
        style.setWindowRounding(WINDOW_ROUNDING);
        style.setFrameRounding(FRAME_ROUNDING);
        style.setPopupRounding(POPUP_ROUNDING);
        style.setScrollbarRounding(SCROLLBAR_ROUNDING);
        style.setGrabRounding(GRAB_ROUNDING);
        style.setTabRounding(TAB_ROUNDING);
        style.setWindowPadding(WINDOW_PADDING, WINDOW_PADDING);
        style.setFramePadding(FRAME_PADDING_X, FRAME_PADDING_Y);
        style.setItemSpacing(ITEM_SPACING_X, ITEM_SPACING_Y);
        style.setItemInnerSpacing(INNER_SPACING, INNER_SPACING);
        style.setIndentSpacing(INDENT_SPACING);
        style.setScrollbarSize(SCROLLBAR_SIZE);
        style.setWindowBorderSize(BORDER_SIZE);
        style.setFrameBorderSize(0.0f);
        style.setPopupBorderSize(BORDER_SIZE);
    }

    private static void applyColors() {
        ImGuiStyle style = ImGui.getStyle();
        setColor(style, ImGuiCol.WindowBg, COLOR_WINDOW_BACKGROUND);
        setColor(style, ImGuiCol.ChildBg, COLOR_WINDOW_BACKGROUND);
        setColor(style, ImGuiCol.PopupBg, COLOR_PANEL_BACKGROUND);
        setColor(style, ImGuiCol.MenuBarBg, COLOR_HEADER_BACKGROUND);
        setColor(style, ImGuiCol.Border, COLOR_OUTLINE);
        setColor(style, ImGuiCol.FrameBg, COLOR_WIDGET_BACKGROUND);
        setColor(style, ImGuiCol.FrameBgHovered, COLOR_WIDGET_HOVER);
        setColor(style, ImGuiCol.FrameBgActive, COLOR_WIDGET_ACTIVE);
        setColor(style, ImGuiCol.TitleBg, COLOR_HEADER_BACKGROUND);
        setColor(style, ImGuiCol.TitleBgActive, COLOR_HEADER_BACKGROUND);
        setColor(style, ImGuiCol.TitleBgCollapsed, COLOR_HEADER_BACKGROUND);
        setColor(style, ImGuiCol.Text, COLOR_TEXT);
        setColor(style, ImGuiCol.TextDisabled, COLOR_TEXT_MUTED);
        applyInteractiveColors(style);
        applyDockingColors(style);
    }

    private static void applyInteractiveColors(ImGuiStyle style) {
        setColor(style, ImGuiCol.Button, COLOR_WIDGET_BACKGROUND);
        setColor(style, ImGuiCol.ButtonHovered, COLOR_WIDGET_HOVER);
        setColor(style, ImGuiCol.ButtonActive, COLOR_WIDGET_ACTIVE);
        setColor(style, ImGuiCol.Header, COLOR_WIDGET_ACTIVE);
        setColor(style, ImGuiCol.HeaderHovered, COLOR_WIDGET_HOVER);
        setColor(style, ImGuiCol.HeaderActive, COLOR_WIDGET_ACTIVE);
        setColor(style, ImGuiCol.CheckMark, COLOR_ACCENT);
        setColor(style, ImGuiCol.SliderGrab, COLOR_ACCENT);
        setColor(style, ImGuiCol.SliderGrabActive, COLOR_ACCENT_HOVER);
        setColor(style, ImGuiCol.Separator, COLOR_OUTLINE);
        setColor(style, ImGuiCol.SeparatorHovered, COLOR_ACCENT);
        setColor(style, ImGuiCol.SeparatorActive, COLOR_ACCENT_HOVER);
        setColor(style, ImGuiCol.ResizeGrip, COLOR_WIDGET_BACKGROUND);
        setColor(style, ImGuiCol.ResizeGripHovered, COLOR_ACCENT);
        setColor(style, ImGuiCol.ResizeGripActive, COLOR_ACCENT_HOVER);
        setColor(style, ImGuiCol.ScrollbarBg, COLOR_WINDOW_BACKGROUND);
        setColor(style, ImGuiCol.ScrollbarGrab, COLOR_WIDGET_BACKGROUND);
        setColor(style, ImGuiCol.ScrollbarGrabHovered, COLOR_WIDGET_HOVER);
        setColor(style, ImGuiCol.ScrollbarGrabActive, COLOR_ACCENT);
        setColor(style, ImGuiCol.NavHighlight, COLOR_ACCENT);
        setColor(style, ImGuiCol.DragDropTarget, COLOR_ACCENT_HOVER);
    }

    private static void applyDockingColors(ImGuiStyle style) {
        setColor(style, ImGuiCol.Tab, COLOR_PANEL_BACKGROUND);
        setColor(style, ImGuiCol.TabHovered, COLOR_WIDGET_HOVER);
        setColor(style, ImGuiCol.TabActive, COLOR_WIDGET_ACTIVE);
        setColor(style, ImGuiCol.TabUnfocused, COLOR_PANEL_BACKGROUND);
        setColor(style, ImGuiCol.TabUnfocusedActive, COLOR_HEADER_BACKGROUND);
        setColor(style, ImGuiCol.DockingPreview, withAlpha(COLOR_ACCENT, 0.55f));
        setColor(style, ImGuiCol.DockingEmptyBg, COLOR_WINDOW_BACKGROUND);
    }

    private static void setColor(ImGuiStyle style, int slot, int rgba) {
        style.setColor(slot, rgba);
    }

    public static int rgb(int red, int green, int blue) {
        return rgba(red, green, blue, 255);
    }

    public static int rgba(int red, int green, int blue, int alpha) {
        return (alpha << 24) | (blue << 16) | (green << 8) | red;
    }

    public static int withAlpha(int abgrColor, float alpha) {
        int clamped = Math.round(Math.clamp(alpha, 0.0f, 1.0f) * 255.0f);
        return (abgrColor & 0x00FFFFFF) | (clamped << 24);
    }
}
