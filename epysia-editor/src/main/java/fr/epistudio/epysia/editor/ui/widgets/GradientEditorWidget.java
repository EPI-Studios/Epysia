package fr.epistudio.epysia.editor.ui.widgets;

import fr.epistudio.epysia.editor.shell.EditorStyle;
import fr.epistudio.epysia.vfx.lut.VfxGradient;
import imgui.ImGui;
import imgui.flag.ImGuiMouseButton;

import java.util.List;
import java.util.Locale;

public final class GradientEditorWidget {

    private static final float DEFAULT_BAR_HEIGHT = 30.0f;
    private static final float MINIMUM_WIDTH = 160.0f;
    private static final float MARKER_HEIGHT = 13.0f;
    private static final float MARKER_HALF_WIDTH = 6.0f;
    private static final float LANE_GAP = 3.0f;
    private static final float CHECKER_SIZE = 6.0f;
    private static final float DRAG_THRESHOLD_PIXELS = 3.0f;
    private static final float TIME_DRAG_STEP = 0.005f;
    private static final float EDITOR_ITEM_WIDTH = 190.0f;
    private static final float TIME_ITEM_WIDTH = 95.0f;
    private static final float OUTLINE_THICKNESS = 1.0f;
    private static final float SELECTED_OUTLINE_THICKNESS = 2.0f;
    private static final int BAR_SAMPLES = 128;
    private static final int NONE = -1;
    private static final String STRIP_ID = "##gradient-strip";
    private static final String POPUP_ID = "##gradient-stop-popup";
    private static final int COLOR_CHECKER_LIGHT = EditorStyle.rgb(96, 96, 100);
    private static final int COLOR_CHECKER_DARK = EditorStyle.rgb(64, 64, 68);
    private static final int COLOR_BORDER = EditorStyle.rgba(255, 255, 255, 60);
    private static final int COLOR_MARKER_OUTLINE = EditorStyle.rgb(20, 20, 22);
    private static final int COLOR_SELECTED_OUTLINE = EditorStyle.rgb(255, 255, 255);
    private static final int COLOR_GUIDE = EditorStyle.rgba(255, 255, 255, 110);

    private enum StopKind {
        NONE,
        COLOR,
        ALPHA
    }

    private String activeIdentifier = "";
    private StopKind selectedKind = StopKind.NONE;
    private StopKind draggedKind = StopKind.NONE;
    private int selectedIndex = NONE;
    private int hoveredColorStop = NONE;
    private int hoveredAlphaStop = NONE;
    private float pressX;
    private boolean dragMoved;

    public boolean render(String identifier, VfxGradient gradient) {
        return render(identifier, gradient, DEFAULT_BAR_HEIGHT);
    }

    public boolean render(String identifier, VfxGradient gradient, float barHeight) {
        adoptIdentifier(identifier);
        ImGui.pushID(identifier);
        boolean modified = renderStrip(gradient, Math.max(DEFAULT_BAR_HEIGHT * 0.5f, barHeight));
        modified |= renderStopEditor(gradient);
        modified |= renderStopPopup(gradient);
        ImGui.popID();
        return modified;
    }

    private void adoptIdentifier(String identifier) {
        if (identifier.equals(activeIdentifier)) {
            return;
        }
        activeIdentifier = identifier;
        selectedKind = StopKind.NONE;
        selectedIndex = NONE;
        draggedKind = StopKind.NONE;
    }

    private boolean renderStrip(VfxGradient gradient, float barHeight) {
        float width = Math.max(MINIMUM_WIDTH, ImGui.getContentRegionAvailX());
        float originX = ImGui.getCursorScreenPosX();
        float originY = ImGui.getCursorScreenPosY();
        ImGui.invisibleButton(STRIP_ID, width, totalHeight(barHeight));
        GradientStrip strip = GradientStrip.of(originX, originY, width, barHeight);
        boolean hovered = ImGui.isItemHovered();
        boolean modified = handleInput(strip, gradient, hovered);
        drawCheckerboard(strip);
        drawBar(strip, gradient);
        drawMarkers(strip, gradient);
        return modified;
    }

    private static float totalHeight(float barHeight) {
        return barHeight + 2.0f * (MARKER_HEIGHT + LANE_GAP);
    }

    private boolean handleInput(GradientStrip strip, VfxGradient gradient, boolean hovered) {
        hoveredColorStop = hovered ? colorStopAt(strip, gradient) : NONE;
        hoveredAlphaStop = hovered ? alphaStopAt(strip, gradient) : NONE;
        if (ImGui.isItemActivated()) {
            beginDrag();
        }
        boolean modified = ImGui.isItemActive() && applyDrag(strip, gradient);
        if (ImGui.isItemDeactivated()) {
            endDrag();
        }
        modified |= handleAddStop(strip, gradient, hovered);
        modified |= handleRemoveStop(gradient, hovered);
        return modified;
    }

    private void beginDrag() {
        pressX = ImGui.getMousePosX();
        dragMoved = false;
        draggedKind = StopKind.NONE;
        if (hoveredColorStop != NONE) {
            select(StopKind.COLOR, hoveredColorStop);
        } else if (hoveredAlphaStop != NONE) {
            select(StopKind.ALPHA, hoveredAlphaStop);
        } else {
            return;
        }
        draggedKind = selectedKind;
    }

    private void select(StopKind kind, int index) {
        selectedKind = kind;
        selectedIndex = index;
    }

    private void endDrag() {
        if (draggedKind != StopKind.NONE && !dragMoved) {
            ImGui.openPopup(POPUP_ID);
        }
        draggedKind = StopKind.NONE;
    }

    private boolean applyDrag(GradientStrip strip, VfxGradient gradient) {
        if (draggedKind == StopKind.NONE) {
            return false;
        }
        if (Math.abs(ImGui.getMousePosX() - pressX) > DRAG_THRESHOLD_PIXELS) {
            dragMoved = true;
        }
        float time = strip.timeAt(ImGui.getMousePosX());
        return draggedKind == StopKind.COLOR
                ? moveColorStop(gradient, time)
                : moveAlphaStop(gradient, time);
    }

    private boolean moveColorStop(VfxGradient gradient, float time) {
        List<VfxGradient.ColorStop> stops = gradient.colorStops();
        if (selectedIndex < 0 || selectedIndex >= stops.size()) {
            return false;
        }
        VfxGradient.ColorStop stop = stops.get(selectedIndex);
        if (stop.time() == time) {
            return false;
        }
        selectedIndex = gradient.setColorStop(selectedIndex,
                new VfxGradient.ColorStop(time, stop.red(), stop.green(), stop.blue()));
        return true;
    }

    private boolean moveAlphaStop(VfxGradient gradient, float time) {
        List<VfxGradient.AlphaStop> stops = gradient.alphaStops();
        if (selectedIndex < 0 || selectedIndex >= stops.size()) {
            return false;
        }
        VfxGradient.AlphaStop stop = stops.get(selectedIndex);
        if (stop.time() == time) {
            return false;
        }
        selectedIndex = gradient.setAlphaStop(selectedIndex, new VfxGradient.AlphaStop(time, stop.alpha()));
        return true;
    }

    private boolean handleAddStop(GradientStrip strip, VfxGradient gradient, boolean hovered) {
        boolean onMarker = hoveredColorStop != NONE || hoveredAlphaStop != NONE;
        if (!hovered || onMarker || !ImGui.isMouseDoubleClicked(ImGuiMouseButton.Left)) {
            return false;
        }
        float time = strip.timeAt(ImGui.getMousePosX());
        if (strip.inAlphaLane(ImGui.getMousePosY())) {
            select(StopKind.ALPHA, gradient.addAlphaStop(
                    new VfxGradient.AlphaStop(time, gradient.evaluate(time).w)));
            return true;
        }
        select(StopKind.COLOR, gradient.addColorStop(colorStopAtTime(gradient, time)));
        return true;
    }

    private static VfxGradient.ColorStop colorStopAtTime(VfxGradient gradient, float time) {
        var color = gradient.evaluate(time);
        return new VfxGradient.ColorStop(time, color.x, color.y, color.z);
    }

    private boolean handleRemoveStop(VfxGradient gradient, boolean hovered) {
        if (!hovered || !ImGui.isMouseClicked(ImGuiMouseButton.Right)) {
            return false;
        }
        if (hoveredColorStop != NONE && gradient.colorStops().size() > 1) {
            gradient.removeColorStop(hoveredColorStop);
            return forgetSelection();
        }
        if (hoveredAlphaStop != NONE && gradient.alphaStops().size() > 1) {
            gradient.removeAlphaStop(hoveredAlphaStop);
            return forgetSelection();
        }
        return false;
    }

    private boolean forgetSelection() {
        selectedKind = StopKind.NONE;
        selectedIndex = NONE;
        draggedKind = StopKind.NONE;
        hoveredColorStop = NONE;
        hoveredAlphaStop = NONE;
        return true;
    }

    private static int colorStopAt(GradientStrip strip, VfxGradient gradient) {
        if (!strip.inColorLane(ImGui.getMousePosY())) {
            return NONE;
        }
        List<VfxGradient.ColorStop> stops = gradient.colorStops();
        for (int index = stops.size() - 1; index >= 0; index--) {
            if (strip.nearTime(stops.get(index).time())) {
                return index;
            }
        }
        return NONE;
    }

    private static int alphaStopAt(GradientStrip strip, VfxGradient gradient) {
        if (!strip.inAlphaLane(ImGui.getMousePosY())) {
            return NONE;
        }
        List<VfxGradient.AlphaStop> stops = gradient.alphaStops();
        for (int index = stops.size() - 1; index >= 0; index--) {
            if (strip.nearTime(stops.get(index).time())) {
                return index;
            }
        }
        return NONE;
    }

    private static void drawCheckerboard(GradientStrip strip) {
        var drawList = ImGui.getWindowDrawList();
        drawList.pushClipRect(strip.minX(), strip.barMinY(), strip.maxX(), strip.barMaxY(), true);
        int columns = (int) Math.ceil(strip.width() / CHECKER_SIZE);
        int rows = (int) Math.ceil((strip.barMaxY() - strip.barMinY()) / CHECKER_SIZE);
        for (int row = 0; row < rows; row++) {
            for (int column = 0; column < columns; column++) {
                float x = strip.minX() + column * CHECKER_SIZE;
                float y = strip.barMinY() + row * CHECKER_SIZE;
                int color = (row + column) % 2 == 0 ? COLOR_CHECKER_LIGHT : COLOR_CHECKER_DARK;
                drawList.addRectFilled(x, y, x + CHECKER_SIZE, y + CHECKER_SIZE, color);
            }
        }
        drawList.popClipRect();
    }

    private static void drawBar(GradientStrip strip, VfxGradient gradient) {
        float[] samples = gradient.sample(BAR_SAMPLES);
        var drawList = ImGui.getWindowDrawList();
        for (int index = 0; index + 1 < BAR_SAMPLES; index++) {
            float startTime = index / (float) (BAR_SAMPLES - 1);
            float endTime = (index + 1) / (float) (BAR_SAMPLES - 1);
            int startColor = packSample(samples, index);
            int endColor = packSample(samples, index + 1);
            drawList.addRectFilledMultiColor(strip.screenX(startTime), strip.barMinY(),
                    strip.screenX(endTime), strip.barMaxY(), startColor, endColor, endColor, startColor);
        }
        drawList.addRect(strip.minX(), strip.barMinY(), strip.maxX(), strip.barMaxY(),
                COLOR_BORDER, 0.0f, 0, OUTLINE_THICKNESS);
    }

    private static int packSample(float[] samples, int index) {
        return packColor(samples[index * 4], samples[index * 4 + 1],
                samples[index * 4 + 2], samples[index * 4 + 3]);
    }

    private static int packColor(float red, float green, float blue, float alpha) {
        return EditorStyle.rgba(toByte(red), toByte(green), toByte(blue), toByte(alpha));
    }

    private static int toByte(float value) {
        return Math.round(Math.clamp(value, 0.0f, 1.0f) * 255.0f);
    }

    private void drawMarkers(GradientStrip strip, VfxGradient gradient) {
        drawSelectionGuide(strip, gradient);
        List<VfxGradient.AlphaStop> alphaStops = gradient.alphaStops();
        for (int index = 0; index < alphaStops.size(); index++) {
            VfxGradient.AlphaStop stop = alphaStops.get(index);
            float shade = stop.alpha();
            drawMarker(strip, stop.time(), packColor(shade, shade, shade, 1.0f), true,
                    isSelected(StopKind.ALPHA, index), index == hoveredAlphaStop);
        }
        List<VfxGradient.ColorStop> colorStops = gradient.colorStops();
        for (int index = 0; index < colorStops.size(); index++) {
            VfxGradient.ColorStop stop = colorStops.get(index);
            drawMarker(strip, stop.time(), packColor(stop.red(), stop.green(), stop.blue(), 1.0f), false,
                    isSelected(StopKind.COLOR, index), index == hoveredColorStop);
        }
    }

    private boolean isSelected(StopKind kind, int index) {
        return selectedKind == kind && selectedIndex == index;
    }

    private static void drawMarker(GradientStrip strip, float time, int fill, boolean above,
                                   boolean selected, boolean hovered) {
        var drawList = ImGui.getWindowDrawList();
        float x = strip.screenX(time);
        float apexY = above ? strip.barMinY() : strip.barMaxY();
        float baseY = above ? apexY - MARKER_HEIGHT : apexY + MARKER_HEIGHT;
        float half = hovered || selected ? MARKER_HALF_WIDTH + 1.0f : MARKER_HALF_WIDTH;
        drawList.addTriangleFilled(x, apexY, x - half, baseY, x + half, baseY, fill);
        int outline = selected ? COLOR_SELECTED_OUTLINE : COLOR_MARKER_OUTLINE;
        float thickness = selected ? SELECTED_OUTLINE_THICKNESS : OUTLINE_THICKNESS;
        drawList.addTriangle(x, apexY, x - half, baseY, x + half, baseY, outline, thickness);
    }

    private void drawSelectionGuide(GradientStrip strip, VfxGradient gradient) {
        float time = selectedTime(gradient);
        if (time < 0.0f) {
            return;
        }
        float x = strip.screenX(time);
        ImGui.getWindowDrawList().addLine(x, strip.barMinY(), x, strip.barMaxY(), COLOR_GUIDE, OUTLINE_THICKNESS);
    }

    private float selectedTime(VfxGradient gradient) {
        if (selectedKind == StopKind.COLOR && selectedIndex >= 0 && selectedIndex < gradient.colorStops().size()) {
            return gradient.colorStops().get(selectedIndex).time();
        }
        if (selectedKind == StopKind.ALPHA && selectedIndex >= 0 && selectedIndex < gradient.alphaStops().size()) {
            return gradient.alphaStops().get(selectedIndex).time();
        }
        return -1.0f;
    }

    private boolean renderStopEditor(VfxGradient gradient) {
        return switch (selectedKind) {
            case COLOR -> renderColorStopEditor(gradient);
            case ALPHA -> renderAlphaStopEditor(gradient);
            case NONE -> renderEmptySelectionHint();
        };
    }

    private static boolean renderEmptySelectionHint() {
        ImGui.textDisabled("double click to add a stop, right click to remove");
        return false;
    }

    private boolean renderColorStopEditor(VfxGradient gradient) {
        boolean modified = renderColorComponents(gradient);
        ImGui.sameLine();
        modified |= renderColorTime(gradient);
        return modified;
    }

    private boolean renderColorComponents(VfxGradient gradient) {
        if (!hasSelectedColorStop(gradient)) {
            return false;
        }
        VfxGradient.ColorStop stop = gradient.colorStops().get(selectedIndex);
        float[] components = {stop.red(), stop.green(), stop.blue()};
        ImGui.setNextItemWidth(EDITOR_ITEM_WIDTH);
        if (!ImGui.colorEdit3("Color", components)) {
            return false;
        }
        return applyColorComponents(gradient, stop, components);
    }

    private boolean renderColorTime(VfxGradient gradient) {
        if (!hasSelectedColorStop(gradient)) {
            return false;
        }
        VfxGradient.ColorStop stop = gradient.colorStops().get(selectedIndex);
        float[] time = {stop.time()};
        ImGui.setNextItemWidth(TIME_ITEM_WIDTH);
        if (!ImGui.dragFloat("Time", time, TIME_DRAG_STEP, 0.0f, 1.0f, "%.3f") || time[0] == stop.time()) {
            return false;
        }
        return applyColorTime(gradient, stop, time[0]);
    }

    private boolean hasSelectedColorStop(VfxGradient gradient) {
        return selectedIndex >= 0 && selectedIndex < gradient.colorStops().size();
    }

    private boolean hasSelectedAlphaStop(VfxGradient gradient) {
        return selectedIndex >= 0 && selectedIndex < gradient.alphaStops().size();
    }

    private boolean applyColorComponents(VfxGradient gradient, VfxGradient.ColorStop stop, float[] components) {
        selectedIndex = gradient.setColorStop(selectedIndex,
                new VfxGradient.ColorStop(stop.time(), components[0], components[1], components[2]));
        return true;
    }

    private boolean applyColorTime(VfxGradient gradient, VfxGradient.ColorStop stop, float time) {
        selectedIndex = gradient.setColorStop(selectedIndex,
                new VfxGradient.ColorStop(time, stop.red(), stop.green(), stop.blue()));
        return true;
    }

    private boolean renderAlphaStopEditor(VfxGradient gradient) {
        boolean modified = renderAlphaValue(gradient);
        ImGui.sameLine();
        modified |= renderAlphaTime(gradient);
        return modified;
    }

    private boolean renderAlphaValue(VfxGradient gradient) {
        if (!hasSelectedAlphaStop(gradient)) {
            return false;
        }
        VfxGradient.AlphaStop stop = gradient.alphaStops().get(selectedIndex);
        float[] alpha = {stop.alpha()};
        ImGui.setNextItemWidth(EDITOR_ITEM_WIDTH);
        if (!ImGui.sliderFloat("Alpha", alpha, 0.0f, 1.0f)) {
            return false;
        }
        return applyAlphaValue(gradient, stop, alpha[0]);
    }

    private boolean renderAlphaTime(VfxGradient gradient) {
        if (!hasSelectedAlphaStop(gradient)) {
            return false;
        }
        VfxGradient.AlphaStop stop = gradient.alphaStops().get(selectedIndex);
        float[] time = {stop.time()};
        ImGui.setNextItemWidth(TIME_ITEM_WIDTH);
        if (!ImGui.dragFloat("Time", time, TIME_DRAG_STEP, 0.0f, 1.0f, "%.3f") || time[0] == stop.time()) {
            return false;
        }
        return applyAlphaTime(gradient, stop, time[0]);
    }

    private boolean applyAlphaValue(VfxGradient gradient, VfxGradient.AlphaStop stop, float alpha) {
        selectedIndex = gradient.setAlphaStop(selectedIndex, new VfxGradient.AlphaStop(stop.time(), alpha));
        return true;
    }

    private boolean applyAlphaTime(VfxGradient gradient, VfxGradient.AlphaStop stop, float time) {
        selectedIndex = gradient.setAlphaStop(selectedIndex, new VfxGradient.AlphaStop(time, stop.alpha()));
        return true;
    }

    private boolean renderStopPopup(VfxGradient gradient) {
        if (!ImGui.beginPopup(POPUP_ID)) {
            return false;
        }
        boolean modified = switch (selectedKind) {
            case COLOR -> renderColorPicker(gradient);
            case ALPHA -> renderAlphaPicker(gradient);
            case NONE -> false;
        };
        ImGui.endPopup();
        return modified;
    }

    private boolean renderColorPicker(VfxGradient gradient) {
        List<VfxGradient.ColorStop> stops = gradient.colorStops();
        if (selectedIndex < 0 || selectedIndex >= stops.size()) {
            return false;
        }
        VfxGradient.ColorStop stop = stops.get(selectedIndex);
        float[] components = {stop.red(), stop.green(), stop.blue()};
        ImGui.textDisabled(String.format(Locale.ROOT, "color stop at %.3f", stop.time()));
        if (!ImGui.colorPicker3("##gradient-color-picker", components)) {
            return false;
        }
        return applyColorComponents(gradient, stop, components);
    }

    private boolean renderAlphaPicker(VfxGradient gradient) {
        List<VfxGradient.AlphaStop> stops = gradient.alphaStops();
        if (selectedIndex < 0 || selectedIndex >= stops.size()) {
            return false;
        }
        VfxGradient.AlphaStop stop = stops.get(selectedIndex);
        float[] alpha = {stop.alpha()};
        ImGui.textDisabled(String.format(Locale.ROOT, "alpha stop at %.3f", stop.time()));
        ImGui.setNextItemWidth(EDITOR_ITEM_WIDTH);
        if (!ImGui.sliderFloat("##gradient-alpha-picker", alpha, 0.0f, 1.0f)) {
            return false;
        }
        return applyAlphaValue(gradient, stop, alpha[0]);
    }

    private record GradientStrip(float minX, float maxX, float barMinY, float barMaxY) {

        static GradientStrip of(float originX, float originY, float width, float barHeight) {
            float top = originY + MARKER_HEIGHT + LANE_GAP;
            return new GradientStrip(originX + MARKER_HALF_WIDTH, originX + width - MARKER_HALF_WIDTH,
                    top, top + barHeight);
        }

        float width() {
            return Math.max(1.0f, maxX - minX);
        }

        float screenX(float time) {
            return minX + Math.clamp(time, 0.0f, 1.0f) * width();
        }

        float timeAt(float screenX) {
            return Math.clamp((screenX - minX) / width(), 0.0f, 1.0f);
        }

        boolean inAlphaLane(float screenY) {
            return screenY < barMinY;
        }

        boolean inColorLane(float screenY) {
            return screenY > barMaxY;
        }

        boolean nearTime(float time) {
            return Math.abs(ImGui.getMousePosX() - screenX(time)) <= MARKER_HALF_WIDTH + 1.0f;
        }
    }
}
