package fr.epistudio.epysia.editor.shell;

import imgui.ImFont;
import imgui.ImGui;
import imgui.ImGuiStyle;
import imgui.flag.ImGuiCol;
import imgui.flag.ImGuiDir;

import java.util.Optional;

public final class EditorStyle {

    public static final int COLOR_WINDOW_BACKGROUND = rgb(18, 18, 26);
    public static final int COLOR_PANEL_BACKGROUND = rgb(30, 30, 40);
    public static final int COLOR_HEADER_BACKGROUND = rgb(24, 24, 33);
    public static final int COLOR_SUNKEN_BACKGROUND = rgb(13, 13, 18);
    public static final int COLOR_FIELD_BACKGROUND = rgb(38, 38, 48);
    public static final int COLOR_FIELD_HOVER = rgb(48, 48, 60);
    public static final int COLOR_FIELD_ACTIVE = rgb(56, 56, 71);
    public static final int COLOR_WIDGET_BACKGROUND = rgb(51, 56, 69);
    public static final int COLOR_WIDGET_HOVER = rgb(74, 80, 99);
    public static final int COLOR_WIDGET_ACTIVE = rgb(92, 101, 128);
    public static final int COLOR_OUTLINE = rgba(51, 51, 64, 128);
    public static final int COLOR_TEXT = rgb(230, 230, 242);
    public static final int COLOR_TEXT_MUTED = rgb(128, 128, 143);
    public static final int COLOR_ACCENT = rgb(61, 155, 233);
    public static final int COLOR_ACCENT_HOVER = rgb(99, 180, 245);
    public static final int COLOR_HIGHLIGHT = rgb(255, 128, 0);
    public static final int COLOR_DANGER = rgb(235, 95, 88);
    public static final int COLOR_WARNING = rgb(224, 182, 90);
    public static final int COLOR_SUCCESS = rgb(134, 196, 107);
    public static final int COLOR_SYSTEM = rgb(107, 198, 224);

    public static final float WINDOW_ROUNDING = 3.0f;
    public static final float CHILD_ROUNDING = 3.0f;
    public static final float FRAME_ROUNDING = 3.0f;
    public static final float POPUP_ROUNDING = 3.0f;
    public static final float SCROLLBAR_ROUNDING = 3.0f;
    public static final float GRAB_ROUNDING = 3.0f;
    public static final float TAB_ROUNDING = 0.0f;
    public static final float WINDOW_PADDING = 10.0f;
    public static final float FRAME_PADDING_X = 6.0f;
    public static final float FRAME_PADDING_Y = 4.0f;
    public static final float ITEM_SPACING_X = 8.0f;
    public static final float ITEM_SPACING_Y = 6.0f;
    public static final float INNER_SPACING = 6.0f;
    public static final float CELL_PADDING_X = 6.0f;
    public static final float CELL_PADDING_Y = 3.0f;
    public static final float INDENT_SPACING = 16.0f;
    public static final float SCROLLBAR_SIZE = 12.0f;
    public static final float GRAB_MINIMUM_SIZE = 10.0f;
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
        ImGuiStyle style = ImGui.getStyle();
        applyRounding(style);
        applySpacing(style);
        applyBorders(style);
        applySurfaceColors(style);
        applyFieldColors(style);
        applyInteractiveColors(style);
        applyScrollbarColors(style);
        applyDockingColors(style);
        applyTableColors(style);
        applyPlotColors(style);
    }

    private static void applyRounding(ImGuiStyle style) {
        style.setWindowRounding(WINDOW_ROUNDING);
        style.setChildRounding(CHILD_ROUNDING);
        style.setFrameRounding(FRAME_ROUNDING);
        style.setPopupRounding(POPUP_ROUNDING);
        style.setScrollbarRounding(SCROLLBAR_ROUNDING);
        style.setGrabRounding(GRAB_ROUNDING);
        style.setTabRounding(TAB_ROUNDING);
    }

    private static void applySpacing(ImGuiStyle style) {
        style.setWindowPadding(WINDOW_PADDING, WINDOW_PADDING);
        style.setFramePadding(FRAME_PADDING_X, FRAME_PADDING_Y);
        style.setItemSpacing(ITEM_SPACING_X, ITEM_SPACING_Y);
        style.setItemInnerSpacing(INNER_SPACING, INNER_SPACING);
        style.setCellPadding(CELL_PADDING_X, CELL_PADDING_Y);
        style.setIndentSpacing(INDENT_SPACING);
        style.setScrollbarSize(SCROLLBAR_SIZE);
        style.setGrabMinSize(GRAB_MINIMUM_SIZE);
        style.setWindowTitleAlign(0.0f, 0.5f);
        style.setButtonTextAlign(0.5f, 0.5f);
        style.setSelectableTextAlign(0.0f, 0.5f);
        style.setWindowMenuButtonPosition(ImGuiDir.None);
    }

    private static void applyBorders(ImGuiStyle style) {
        style.setWindowBorderSize(BORDER_SIZE);
        style.setChildBorderSize(BORDER_SIZE);
        style.setFrameBorderSize(0.0f);
        style.setPopupBorderSize(0.0f);
        style.setTabBorderSize(0.0f);
    }

    private static void applySurfaceColors(ImGuiStyle style) {
        style.setColor(ImGuiCol.WindowBg, COLOR_WINDOW_BACKGROUND);
        style.setColor(ImGuiCol.ChildBg, COLOR_WINDOW_BACKGROUND);
        style.setColor(ImGuiCol.PopupBg, COLOR_PANEL_BACKGROUND);
        style.setColor(ImGuiCol.MenuBarBg, COLOR_HEADER_BACKGROUND);
        style.setColor(ImGuiCol.TitleBg, COLOR_HEADER_BACKGROUND);
        style.setColor(ImGuiCol.TitleBgActive, COLOR_PANEL_BACKGROUND);
        style.setColor(ImGuiCol.TitleBgCollapsed, COLOR_HEADER_BACKGROUND);
        style.setColor(ImGuiCol.Border, COLOR_OUTLINE);
        style.setColor(ImGuiCol.BorderShadow, rgba(0, 0, 0, 0));
        style.setColor(ImGuiCol.Text, COLOR_TEXT);
        style.setColor(ImGuiCol.TextDisabled, COLOR_TEXT_MUTED);
        style.setColor(ImGuiCol.TextSelectedBg, withAlpha(COLOR_ACCENT, 0.35f));
        style.setColor(ImGuiCol.ModalWindowDimBg, rgba(8, 8, 12, 150));
    }

    private static void applyFieldColors(ImGuiStyle style) {
        style.setColor(ImGuiCol.FrameBg, COLOR_FIELD_BACKGROUND);
        style.setColor(ImGuiCol.FrameBgHovered, COLOR_FIELD_HOVER);
        style.setColor(ImGuiCol.FrameBgActive, COLOR_FIELD_ACTIVE);
        style.setColor(ImGuiCol.CheckMark, COLOR_ACCENT);
        style.setColor(ImGuiCol.SliderGrab, COLOR_ACCENT);
        style.setColor(ImGuiCol.SliderGrabActive, COLOR_ACCENT_HOVER);
    }

    private static void applyInteractiveColors(ImGuiStyle style) {
        style.setColor(ImGuiCol.Button, COLOR_WIDGET_BACKGROUND);
        style.setColor(ImGuiCol.ButtonHovered, COLOR_WIDGET_HOVER);
        style.setColor(ImGuiCol.ButtonActive, COLOR_WIDGET_ACTIVE);
        style.setColor(ImGuiCol.Header, COLOR_PANEL_BACKGROUND);
        style.setColor(ImGuiCol.HeaderHovered, COLOR_WIDGET_HOVER);
        style.setColor(ImGuiCol.HeaderActive, COLOR_WIDGET_ACTIVE);
        style.setColor(ImGuiCol.Separator, COLOR_OUTLINE);
        style.setColor(ImGuiCol.SeparatorHovered, COLOR_ACCENT);
        style.setColor(ImGuiCol.SeparatorActive, COLOR_ACCENT_HOVER);
        style.setColor(ImGuiCol.ResizeGrip, withAlpha(COLOR_ACCENT, 0.35f));
        style.setColor(ImGuiCol.ResizeGripHovered, withAlpha(COLOR_ACCENT, 0.75f));
        style.setColor(ImGuiCol.ResizeGripActive, COLOR_ACCENT_HOVER);
        style.setColor(ImGuiCol.NavHighlight, COLOR_ACCENT);
        style.setColor(ImGuiCol.NavWindowingHighlight, withAlpha(COLOR_TEXT, 0.7f));
        style.setColor(ImGuiCol.NavWindowingDimBg, rgba(8, 8, 12, 100));
        style.setColor(ImGuiCol.DragDropTarget, COLOR_HIGHLIGHT);
    }

    private static void applyScrollbarColors(ImGuiStyle style) {
        style.setColor(ImGuiCol.ScrollbarBg, COLOR_SUNKEN_BACKGROUND);
        style.setColor(ImGuiCol.ScrollbarGrab, rgb(56, 56, 71));
        style.setColor(ImGuiCol.ScrollbarGrabHovered, rgb(74, 80, 99));
        style.setColor(ImGuiCol.ScrollbarGrabActive, rgb(92, 101, 128));
    }

    private static void applyDockingColors(ImGuiStyle style) {
        style.setColor(ImGuiCol.Tab, COLOR_PANEL_BACKGROUND);
        style.setColor(ImGuiCol.TabHovered, COLOR_WIDGET_HOVER);
        style.setColor(ImGuiCol.TabActive, rgb(45, 49, 64));
        style.setColor(ImGuiCol.TabUnfocused, rgb(22, 22, 30));
        style.setColor(ImGuiCol.TabUnfocusedActive, rgb(33, 33, 43));
        style.setColor(ImGuiCol.DockingPreview, withAlpha(COLOR_ACCENT, 0.55f));
        style.setColor(ImGuiCol.DockingEmptyBg, COLOR_SUNKEN_BACKGROUND);
    }

    private static void applyTableColors(ImGuiStyle style) {
        style.setColor(ImGuiCol.TableHeaderBg, COLOR_PANEL_BACKGROUND);
        style.setColor(ImGuiCol.TableBorderStrong, rgb(63, 63, 79));
        style.setColor(ImGuiCol.TableBorderLight, rgb(43, 43, 54));
        style.setColor(ImGuiCol.TableRowBg, rgba(0, 0, 0, 0));
        style.setColor(ImGuiCol.TableRowBgAlt, rgba(255, 255, 255, 10));
    }

    private static void applyPlotColors(ImGuiStyle style) {
        style.setColor(ImGuiCol.PlotLines, COLOR_ACCENT);
        style.setColor(ImGuiCol.PlotLinesHovered, COLOR_ACCENT_HOVER);
        style.setColor(ImGuiCol.PlotHistogram, COLOR_HIGHLIGHT);
        style.setColor(ImGuiCol.PlotHistogramHovered, rgb(255, 168, 77));
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
