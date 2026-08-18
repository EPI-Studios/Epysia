package fr.epistudio.epysia.editor.ui;

import fr.epistudio.epysia.i18n.I18n;
import fr.epistudio.epysia.i18n.TextKey;
import imgui.ImGui;
import imgui.ImGuiStyle;
import imgui.extension.imnodes.ImNodes;
import imgui.extension.imnodes.ImNodesStyle;
import imgui.extension.imnodes.flag.ImNodesMiniMapLocation;
import imgui.extension.imnodes.flag.ImNodesStyleVar;
import imgui.flag.ImGuiFocusedFlags;
import imgui.flag.ImGuiHoveredFlags;
import imgui.flag.ImGuiKey;
import imgui.flag.ImGuiStyleVar;
import imgui.type.ImBoolean;

import java.util.Locale;

final class GraphCanvasNavigation {

    enum Framing {
        NONE,
        ALL,
        SELECTION
    }

    private static final int SCALED_NODE_STYLE_VARS = 12;
    private static final int SCALED_WIDGET_STYLE_VARS = 3;
    private static final float MINIMAP_SIZE_FRACTION = 0.18f;
    private static final float BUTTON_STEP = 1.0f;
    private static final float WHEEL_STEP = 1.0f;

    private final GraphCanvasZoom zoom = new GraphCanvasZoom();
    private final ImBoolean minimapToggle = new ImBoolean(true);

    private Framing framing = Framing.NONE;
    private boolean minimapVisible = true;
    private float originX;
    private float originY;
    private float viewportWidth = 1.0f;
    private float viewportHeight = 1.0f;

    boolean lowDetail() {
        return zoom.lowDetail();
    }

    float factor() {
        return zoom.factor();
    }

    float scaled(float logical) {
        return zoom.scaled(logical);
    }

    float logicalExtent(float screenExtent) {
        return zoom.unscaled(screenExtent);
    }

    void renderToolbarControls() {
        renderZoomButtons();
        ImGui.sameLine();
        renderFramingButtons();
        ImGui.sameLine();
        minimapToggle.set(minimapVisible);
        if (ImGui.checkbox(I18n.label(TextKey.EDITOR_GRAPH_CANVAS_NAVIGATION_MINIMAP,
                "graph-minimap"), minimapToggle)) {
            minimapVisible = minimapToggle.get();
        }
    }

    private void renderZoomButtons() {
        if (ImGui.smallButton("-##graph-zoom-out")) {
            stepFromCenter(-BUTTON_STEP);
        }
        ImGui.sameLine();
        ImGui.textUnformatted(String.format(Locale.ROOT, "%3d%%", zoom.percentage()));
        ImGui.sameLine();
        if (ImGui.smallButton("+##graph-zoom-in")) {
            stepFromCenter(BUTTON_STEP);
        }
        ImGui.sameLine();
        if (ImGui.smallButton(I18n.label(TextKey.EDITOR_GRAPH_CANVAS_NAVIGATION_ZOOM_RESET,
                "graph-zoom-reset")) && !zoom.atDefault()) {
            resetFromCenter();
        }
    }

    private void renderFramingButtons() {
        if (ImGui.smallButton(I18n.label(TextKey.EDITOR_GRAPH_CANVAS_NAVIGATION_FIT,
                "graph-fit"))) {
            framing = Framing.ALL;
        }
        if (ImGui.isItemHovered()) {
            ImGui.setTooltip(I18n.translate(TextKey.EDITOR_GRAPH_CANVAS_NAVIGATION_TOOLTIP));
        }
        ImGui.sameLine();
        if (ImGui.smallButton(I18n.label(TextKey.EDITOR_GRAPH_CANVAS_NAVIGATION_FRAME_SELECTION,
                "graph-frame-selection"))) {
            framing = Framing.SELECTION;
        }
    }

    void beginCanvas() {
        viewportWidth = Math.max(1.0f, ImGui.getContentRegionAvailX());
        viewportHeight = Math.max(1.0f, ImGui.getContentRegionAvailY());
        ImGui.setWindowFontScale(zoom.factor());
        GraphCanvasStyle.applyBase();
        pushScaledWidgetStyle();
        pushScaledNodeStyle();
        GraphCanvasStyle.pushColors();
    }

    void endCanvas() {
        GraphCanvasStyle.popColors();
        for (int index = 0; index < SCALED_NODE_STYLE_VARS; index++) {
            ImNodes.popStyleVar();
        }
        ImGui.popStyleVar(SCALED_WIDGET_STYLE_VARS);
        ImGui.setWindowFontScale(GraphCanvasZoom.DEFAULT_FACTOR);
    }

    private void pushScaledWidgetStyle() {
        ImGuiStyle style = ImGui.getStyle();
        float scale = zoom.factor();
        ImGui.pushStyleVar(ImGuiStyleVar.FramePadding,
                style.getFramePaddingX() * scale, style.getFramePaddingY() * scale);
        ImGui.pushStyleVar(ImGuiStyleVar.ItemSpacing,
                style.getItemSpacingX() * scale, style.getItemSpacingY() * scale);
        ImGui.pushStyleVar(ImGuiStyleVar.ItemInnerSpacing,
                style.getItemInnerSpacingX() * scale, style.getItemInnerSpacingY() * scale);
    }

    private static final float MINIMUM_GRID_PIXELS = 14.0f;

    private void pushScaledNodeStyle() {
        ImNodesStyle style = ImNodes.getStyle();
        float scale = zoom.factor();
        ImNodes.pushStyleVar(ImNodesStyleVar.GridSpacing,
                Math.max(MINIMUM_GRID_PIXELS, style.getGridSpacing() * scale));
        ImNodes.pushStyleVar(ImNodesStyleVar.NodeCornerRounding, style.getNodeCornerRounding() * scale);
        ImNodes.pushStyleVar(ImNodesStyleVar.NodePadding,
                style.getNodePaddingX() * scale, style.getNodePaddingY() * scale);
        ImNodes.pushStyleVar(ImNodesStyleVar.NodeBorderThickness, style.getNodeBorderThickness() * scale);
        ImNodes.pushStyleVar(ImNodesStyleVar.LinkThickness, style.getLinkThickness() * scale);
        ImNodes.pushStyleVar(ImNodesStyleVar.LinkHoverDistance, style.getLinkHoverDistance() * scale);
        pushScaledPinStyle(style, scale);
    }

    private static void pushScaledPinStyle(ImNodesStyle style, float scale) {
        ImNodes.pushStyleVar(ImNodesStyleVar.PinCircleRadius, style.getPinCircleRadius() * scale);
        ImNodes.pushStyleVar(ImNodesStyleVar.PinQuadSideLength, style.getPinQuadSideLength() * scale);
        ImNodes.pushStyleVar(ImNodesStyleVar.PinTriangleSideLength,
                style.getPinTriangleSideLength() * scale);
        ImNodes.pushStyleVar(ImNodesStyleVar.PinLineThickness, style.getPinLineThickness() * scale);
        ImNodes.pushStyleVar(ImNodesStyleVar.PinHoverRadius, style.getPinHoverRadius() * scale);
        ImNodes.pushStyleVar(ImNodesStyleVar.PinOffset, style.getPinOffset() * scale);
    }

    void captureOrigin() {
        originX = ImGui.getCursorScreenPosX();
        originY = ImGui.getCursorScreenPosY();
    }

    void refineOrigin(int nodeId) {
        originX = ImNodes.getNodeScreenSpacePosX(nodeId)
                - ImNodes.editorContextGetPanningX() - ImNodes.getNodeGridSpacePosX(nodeId);
        originY = ImNodes.getNodeScreenSpacePosY(nodeId)
                - ImNodes.editorContextGetPanningY() - ImNodes.getNodeGridSpacePosY(nodeId);
    }

    void renderMiniMap() {
        if (minimapVisible) {
            ImNodes.miniMap(MINIMAP_SIZE_FRACTION, ImNodesMiniMapLocation.BottomRight);
        }
    }

    float logicalFromScreenX(float screenX) {
        return GraphCanvasZoom.logicalFromScreen(screenX, originX,
                ImNodes.editorContextGetPanningX(), zoom.factor());
    }

    float logicalFromScreenY(float screenY) {
        return GraphCanvasZoom.logicalFromScreen(screenY, originY,
                ImNodes.editorContextGetPanningY(), zoom.factor());
    }

    void placeNode(int nodeId, float logicalX, float logicalY) {
        ImNodes.setNodeGridSpacePos(nodeId, zoom.scaled(logicalX), zoom.scaled(logicalY));
    }

    float logicalPositionX(int nodeId) {
        return zoom.unscaled(ImNodes.getNodeGridSpacePosX(nodeId));
    }

    float logicalPositionY(int nodeId) {
        return zoom.unscaled(ImNodes.getNodeGridSpacePosY(nodeId));
    }

    void handleInput() {
        float wheel = ImGui.getIO().getMouseWheel();
        if (wheel != 0.0f && ImGui.isWindowHovered(ImGuiHoveredFlags.ChildWindows)) {
            stepAnchored(wheel * WHEEL_STEP, ImGui.getMousePosX(), ImGui.getMousePosY());
        }
        if (ImGui.isWindowFocused(ImGuiFocusedFlags.RootAndChildWindows)
                && !ImGui.getIO().getWantTextInput()) {
            handleShortcuts();
        }
    }

    private void handleShortcuts() {
        boolean control = ImGui.getIO().getKeyCtrl();
        if (control && (ImGui.isKeyPressed(ImGuiKey.Equal) || ImGui.isKeyPressed(ImGuiKey.KeypadAdd))) {
            stepFromCenter(BUTTON_STEP);
        }
        if (control && (ImGui.isKeyPressed(ImGuiKey.Minus) || ImGui.isKeyPressed(ImGuiKey.KeypadSubtract))) {
            stepFromCenter(-BUTTON_STEP);
        }
        if (control && ImGui.isKeyPressed(ImGuiKey._0)) {
            resetFromCenter();
        }
        if (!control && ImGui.isKeyPressed(ImGuiKey.F)) {
            framing = Framing.SELECTION;
        }
    }

    Framing consumeFraming() {
        Framing requested = framing;
        framing = Framing.NONE;
        return requested;
    }

    void frameBounds(Bounds bounds) {
        if (bounds.empty()) {
            return;
        }
        zoom.setFactor(GraphCanvasZoom.fitFactor(bounds.spanX(), bounds.spanY(),
                viewportWidth, viewportHeight));
        ImNodes.editorContextResetPanning(
                GraphCanvasZoom.centeringPanning(bounds.centerX(), viewportWidth, zoom.factor()),
                GraphCanvasZoom.centeringPanning(bounds.centerY(), viewportHeight, zoom.factor()));
    }

    private void stepFromCenter(float notches) {
        stepAnchored(notches, originX + viewportWidth * 0.5f, originY + viewportHeight * 0.5f);
    }

    private void resetFromCenter() {
        float previous = zoom.factor();
        zoom.reset();
        anchorTo(originX + viewportWidth * 0.5f, originY + viewportHeight * 0.5f, previous);
    }

    private void stepAnchored(float notches, float cursorX, float cursorY) {
        float previous = zoom.factor();
        zoom.stepBy(notches);
        anchorTo(cursorX, cursorY, previous);
    }

    private void anchorTo(float cursorX, float cursorY, float previousFactor) {
        if (previousFactor == zoom.factor()) {
            return;
        }
        float panningX = ImNodes.editorContextGetPanningX();
        float panningY = ImNodes.editorContextGetPanningY();
        ImNodes.editorContextResetPanning(
                GraphCanvasZoom.anchoredPanning(cursorX, originX, panningX, previousFactor, zoom.factor()),
                GraphCanvasZoom.anchoredPanning(cursorY, originY, panningY, previousFactor, zoom.factor()));
    }

    static final class Bounds {

        private float minimumX = Float.MAX_VALUE;
        private float minimumY = Float.MAX_VALUE;
        private float maximumX = -Float.MAX_VALUE;
        private float maximumY = -Float.MAX_VALUE;
        private boolean empty = true;

        void include(float x, float y, float width, float height) {
            minimumX = Math.min(minimumX, x);
            minimumY = Math.min(minimumY, y);
            maximumX = Math.max(maximumX, x + width);
            maximumY = Math.max(maximumY, y + height);
            empty = false;
        }

        boolean empty() {
            return empty;
        }

        float spanX() {
            return maximumX - minimumX;
        }

        float spanY() {
            return maximumY - minimumY;
        }

        float centerX() {
            return (minimumX + maximumX) * 0.5f;
        }

        float centerY() {
            return (minimumY + maximumY) * 0.5f;
        }
    }
}
