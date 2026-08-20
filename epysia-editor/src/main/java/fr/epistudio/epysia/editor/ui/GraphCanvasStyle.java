package fr.epistudio.epysia.editor.ui;

import fr.epistudio.epysia.editor.shell.EditorStyle;
import imgui.extension.imnodes.ImNodes;
import imgui.extension.imnodes.ImNodesStyle;
import imgui.extension.imnodes.flag.ImNodesCol;

import java.util.Map;

final class GraphCanvasStyle {

    private static final float NODE_PADDING_X = 10.0f;
    private static final float NODE_PADDING_Y = 7.0f;
    private static final float NODE_BORDER_THICKNESS = 1.0f;
    private static final float GRID_SPACING = 28.0f;
    private static final float PIN_CIRCLE_RADIUS = 4.5f;
    private static final float PIN_HOVER_RADIUS = 10.0f;
    private static final float PIN_LINE_THICKNESS = 1.4f;
    private static final float LINK_THICKNESS = 2.4f;
    private static final float LINK_HOVER_DISTANCE = 9.0f;
    private static final float SELECTION_ALPHA = 0.22f;
    private static final float OUTLINE_ALPHA = 0.85f;
    private static final float GRID_LINE_ALPHA = 0.35f;
    private static final float GRID_LINE_PRIMARY_ALPHA = 0.6f;
    private static final float MINIMAP_BACKGROUND_ALPHA = 0.7f;
    private static final int NEUTRAL_TITLE = EditorStyle.rgb(74, 78, 86);
    private static final int PUSHED_COLOR_COUNT = 16;

    private static final Map<String, Integer> TITLE_BY_CATEGORY = Map.ofEntries(
            Map.entry("Events", EditorStyle.rgb(110, 66, 48)),
            Map.entry("Flow", EditorStyle.rgb(96, 74, 46)),
            Map.entry("State Machine", EditorStyle.rgb(106, 74, 38)),
            Map.entry("Math", EditorStyle.rgb(48, 84, 70)),
            Map.entry("Logic", EditorStyle.rgb(58, 58, 100)),
            Map.entry("Input", EditorStyle.rgb(40, 70, 96)),
            Map.entry("Variables", EditorStyle.rgb(60, 68, 78)),
            Map.entry("GameObject", EditorStyle.rgb(88, 64, 56)),
            Map.entry("Physics", EditorStyle.rgb(84, 52, 52)),
            Map.entry("Utility", EditorStyle.rgb(64, 64, 72)),
            Map.entry("Shader Input", EditorStyle.rgb(40, 70, 96)),
            Map.entry("Shader Constant", EditorStyle.rgb(52, 62, 78)),
            Map.entry("Shader Math", EditorStyle.rgb(48, 84, 70)),
            Map.entry("Shader Vector", EditorStyle.rgb(44, 78, 92)),
            Map.entry("Shader Texture", EditorStyle.rgb(96, 58, 74)),
            Map.entry("Shader Effect", EditorStyle.rgb(84, 60, 96)),
            Map.entry("Shader Parameter", EditorStyle.rgb(70, 76, 46)),
            Map.entry("Shader Custom", EditorStyle.rgb(72, 52, 92)),
            Map.entry("Shader Output", EditorStyle.rgb(46, 76, 88)),
            Map.entry("VFX Output", EditorStyle.rgb(46, 76, 88)),
            Map.entry("VFX Particle", EditorStyle.rgb(96, 74, 46)),
            Map.entry("VFX Random", EditorStyle.rgb(64, 64, 72)),
            Map.entry("VFX Value", EditorStyle.rgb(52, 62, 78)),
            Map.entry("VFX Shape", EditorStyle.rgb(52, 82, 58)),
            Map.entry("VFX Math", EditorStyle.rgb(48, 84, 70)));

    private GraphCanvasStyle() {
    }

    static void applyBase() {
        ImNodesStyle style = ImNodes.getStyle();
        style.setNodeCornerRounding(EditorStyle.frameRounding());
        style.setNodePadding(NODE_PADDING_X, NODE_PADDING_Y);
        style.setNodeBorderThickness(NODE_BORDER_THICKNESS);
        style.setGridSpacing(GRID_SPACING);
        style.setLinkThickness(LINK_THICKNESS);
        style.setLinkHoverDistance(LINK_HOVER_DISTANCE);
        style.setPinCircleRadius(PIN_CIRCLE_RADIUS);
        style.setPinHoverRadius(PIN_HOVER_RADIUS);
        style.setPinLineThickness(PIN_LINE_THICKNESS);
    }

    static void pushColors() {
        ImNodes.pushColorStyle(ImNodesCol.NodeBackground, EditorStyle.COLOR_ELEVATED_BACKGROUND);
        ImNodes.pushColorStyle(ImNodesCol.NodeBackgroundHovered, EditorStyle.COLOR_WIDGET_HOVER);
        ImNodes.pushColorStyle(ImNodesCol.NodeBackgroundSelected, EditorStyle.COLOR_WIDGET_ACTIVE);
        ImNodes.pushColorStyle(ImNodesCol.NodeOutline,
                EditorStyle.withAlpha(EditorStyle.COLOR_WIDGET_OUTLINE, OUTLINE_ALPHA));
        ImNodes.pushColorStyle(ImNodesCol.GridBackground, EditorStyle.COLOR_SUNKEN_BACKGROUND);
        ImNodes.pushColorStyle(ImNodesCol.GridLine,
                EditorStyle.withAlpha(EditorStyle.COLOR_OUTLINE, GRID_LINE_ALPHA));
        ImNodes.pushColorStyle(ImNodesCol.GridLinePrimary,
                EditorStyle.withAlpha(EditorStyle.COLOR_OUTLINE, GRID_LINE_PRIMARY_ALPHA));
        ImNodes.pushColorStyle(ImNodesCol.BoxSelector,
                EditorStyle.withAlpha(EditorStyle.COLOR_ACCENT, SELECTION_ALPHA));
        ImNodes.pushColorStyle(ImNodesCol.BoxSelectorOutline, EditorStyle.COLOR_ACCENT);
        ImNodes.pushColorStyle(ImNodesCol.MiniMapBackground,
                EditorStyle.withAlpha(EditorStyle.COLOR_SUNKEN_BACKGROUND, MINIMAP_BACKGROUND_ALPHA));
        ImNodes.pushColorStyle(ImNodesCol.MiniMapOutline, EditorStyle.COLOR_WIDGET_OUTLINE);
        ImNodes.pushColorStyle(ImNodesCol.MiniMapNodeBackground, EditorStyle.COLOR_WIDGET_BACKGROUND);
        ImNodes.pushColorStyle(ImNodesCol.MiniMapNodeOutline, EditorStyle.COLOR_OUTLINE);
        ImNodes.pushColorStyle(ImNodesCol.MiniMapLink, EditorStyle.COLOR_TEXT_FAINT);
        ImNodes.pushColorStyle(ImNodesCol.MiniMapCanvas,
                EditorStyle.withAlpha(EditorStyle.COLOR_ACCENT, SELECTION_ALPHA));
        ImNodes.pushColorStyle(ImNodesCol.MiniMapCanvasOutline, EditorStyle.COLOR_ACCENT);
    }

    static void popColors() {
        for (int index = 0; index < PUSHED_COLOR_COUNT; index++) {
            ImNodes.popColorStyle();
        }
    }

    static int titleColorFor(String category) {
        return TITLE_BY_CATEGORY.getOrDefault(category, NEUTRAL_TITLE);
    }

}
