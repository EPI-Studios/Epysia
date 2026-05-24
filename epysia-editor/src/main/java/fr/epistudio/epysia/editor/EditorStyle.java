package fr.epistudio.epysia.editor;

import com.miry.ui.component.Color;
import com.miry.ui.theme.Theme;

public final class EditorStyle {

    public static final int CONTENT_PADDING_X = 14;
    public static final int CONTENT_PADDING_Y = 12;
    public static final int LINE_HEIGHT = 22;

    public static final int LEAF_HEADER_HEIGHT = 26;
    public static final int LEAF_HEADER_BG = 0xFF121418;
    public static final int LEAF_HEADER_ACCENT = 0xFF4772B3;
    public static final int LEAF_BACKGROUND = 0xFF161E1E;

    public static final int TOPBAR_HEIGHT = 38;
    public static final int STATUSBAR_HEIGHT = 24;
    public static final int TOPBAR_BG = 0xFF0F1114;
    public static final int STATUSBAR_BG = 0xFF0F1114;

    public static final int COLOR_WINDOW_BG = 0xFF0F1114;
    public static final int COLOR_PANEL_BG = LEAF_BACKGROUND;
    public static final int COLOR_HEADER_BG = LEAF_HEADER_BG;
    public static final int COLOR_VIEWPORT_BG = 0xFF0F1114;
    public static final int COLOR_TEXT_PRIMARY = 0xFFE8ECF5;
    public static final int COLOR_TEXT_MUTED = 0xFF96A0B2;
    public static final int COLOR_TEXT_HEADER = 0xFFFFFFFF;
    public static final int COLOR_TEXT_DIM = 0xFF606878;
    public static final int COLOR_SEPARATOR = 0xFF232830;
    public static final int COLOR_WIDGET_BG = 0xFF1C2028;
    public static final int COLOR_WIDGET_HOVER = 0xFF242A36;
    public static final int COLOR_WIDGET_ACTIVE = 0xFF4772B3;
    public static final int COLOR_ACCENT = LEAF_HEADER_ACCENT;
    public static final int COLOR_SELECTION = 0xFF4772B3;
    public static final int COLOR_HOVER_ROW = 0x30FFFFFF;

    public static final int COLOR_AXIS_X = 0xFFF55366;
    public static final int COLOR_AXIS_Y = 0xFFB3E867;
    public static final int COLOR_AXIS_Z = 0xFF6BABF5;
    public static final int COLOR_AXIS_X_DIM = 0x40F55366;
    public static final int COLOR_AXIS_Y_DIM = 0x40B3E867;
    public static final int COLOR_AXIS_Z_DIM = 0x406BABF5;

    public static final int COLOR_PLAY = 0xFF73F280;
    public static final int COLOR_STOP = 0xFFFF6B6B;

    private EditorStyle() {
    }

    public static void apply(Theme theme) {
        copyArgb(theme.windowBg, COLOR_WINDOW_BG);
        copyArgb(theme.panelBg, COLOR_PANEL_BG);
        copyArgb(theme.headerBg, COLOR_HEADER_BG);
        copyArgb(theme.headerLine, COLOR_SEPARATOR);
        copyArgb(theme.widgetBg, COLOR_WIDGET_BG);
        copyArgb(theme.widgetHover, COLOR_WIDGET_HOVER);
        copyArgb(theme.widgetActive, COLOR_WIDGET_ACTIVE);
        copyArgb(theme.widgetOutline, COLOR_SEPARATOR);
        copyArgb(theme.text, COLOR_TEXT_PRIMARY);
        copyArgb(theme.textMuted, COLOR_TEXT_MUTED);
        copyArgb(theme.accent, COLOR_ACCENT);
        applyTypographyScale(theme);
        applyWidgetSizing(theme);
    }

    private static void copyArgb(Color destination, int argb) {
        destination.set(new Color(argb));
    }

    private static void applyTypographyScale(Theme theme) {
        theme.design.font_xs = 11;
        theme.design.font_sm = 12;
        theme.design.font_base = 13;
        theme.design.font_md = 14;
        theme.design.font_lg = 17;
        theme.design.font_xl = 22;
        theme.design.space_xs = 4;
        theme.design.space_sm = 8;
        theme.design.space_md = 12;
        theme.design.space_lg = 18;
        theme.design.space_xl = 24;
        theme.design.radius_sm = 3;
        theme.design.radius_md = 4;
        theme.design.radius_lg = 6;
        theme.design.radius_xl = 10;
    }

    private static void applyWidgetSizing(Theme theme) {
        theme.design.widget_height_sm = 22;
        theme.design.widget_height_md = 26;
        theme.design.widget_height_lg = 30;
        theme.design.widget_height_xl = 36;
        theme.tokens.padding = 10;
        theme.tokens.itemHeight = 24;
        theme.tokens.itemSpacing = 3;
        theme.tokens.cornerRadius = 4;
        theme.tokens.animSpeed = 16.0f;
    }
}
