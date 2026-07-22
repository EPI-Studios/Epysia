package fr.epistudio.epysia.editor.ui.widgets;

import fr.epistudio.epysia.editor.shell.EditorStyle;
import fr.epistudio.epysia.vfx.lut.VfxCurve;
import imgui.ImGui;
import imgui.flag.ImGuiMouseButton;

import java.util.List;
import java.util.Locale;

public final class CurveEditorWidget {

    private static final float DEFAULT_HEIGHT = 170.0f;
    private static final float MINIMUM_WIDTH = 180.0f;
    private static final float PADDING_LEFT = 42.0f;
    private static final float PADDING_RIGHT = 10.0f;
    private static final float PADDING_TOP = 18.0f;
    private static final float PADDING_BOTTOM = 18.0f;
    private static final float MINIMUM_SPAN = 0.0001f;
    private static final float TIME_EPSILON = 0.002f;
    private static final float POINT_RADIUS = 4.5f;
    private static final float POINT_HOVER_RADIUS = 6.5f;
    private static final float HANDLE_RADIUS = 3.5f;
    private static final float HANDLE_LENGTH = 34.0f;
    private static final float HIT_RADIUS = 9.0f;
    private static final float MINIMUM_HANDLE_PIXELS = 3.0f;
    private static final float MAXIMUM_TANGENT = 1000.0f;
    private static final float SLOPE_STEP = 0.01f;
    private static final float CURVE_THICKNESS = 2.0f;
    private static final float GRID_THICKNESS = 1.0f;
    private static final int CURVE_SAMPLES = 192;
    private static final int GRID_COLUMNS = 8;
    private static final int GRID_ROWS = 4;
    private static final int MINIMUM_KEYFRAMES = 2;
    private static final int NONE = -1;
    private static final String CANVAS_ID = "##curve-canvas";
    private static final int COLOR_BACKGROUND = EditorStyle.rgb(24, 24, 26);
    private static final int COLOR_GRID = EditorStyle.rgba(255, 255, 255, 16);
    private static final int COLOR_GRID_STRONG = EditorStyle.rgba(255, 255, 255, 34);
    private static final int COLOR_BORDER = EditorStyle.rgba(255, 255, 255, 40);
    private static final int COLOR_CURVE = EditorStyle.rgb(120, 200, 255);
    private static final int COLOR_CURVE_FILL = EditorStyle.rgba(120, 200, 255, 26);
    private static final int COLOR_POINT = EditorStyle.rgb(230, 230, 235);
    private static final int COLOR_POINT_HOVER = EditorStyle.rgb(255, 255, 255);
    private static final int COLOR_POINT_SELECTED = EditorStyle.COLOR_ACCENT_HOVER;
    private static final int COLOR_HANDLE = EditorStyle.rgb(224, 182, 90);

    private interface PresetShape {
        List<VfxCurve.Keyframe> keyframes(float low, float high);
    }

    private record CurvePreset(String label, PresetShape shape) {
    }

    private record ScreenPoint(float x, float y) {
    }

    private enum DragMode {
        NONE,
        POINT,
        IN_TANGENT,
        OUT_TANGENT
    }

    private static final List<CurvePreset> PRESETS = List.of(
            new CurvePreset("Rise", CurveEditorWidget::riseShape),
            new CurvePreset("Fall", CurveEditorWidget::fallShape),
            new CurvePreset("Ease In", CurveEditorWidget::easeInShape),
            new CurvePreset("Ease Out", CurveEditorWidget::easeOutShape),
            new CurvePreset("Ease In Out", CurveEditorWidget::easeInOutShape),
            new CurvePreset("Bell", CurveEditorWidget::bellShape),
            new CurvePreset("Constant", CurveEditorWidget::constantShape));

    private String horizontalAxisLabel = "life";
    private String verticalAxisLabel = "value";
    private String activeIdentifier = "";
    private DragMode dragMode = DragMode.NONE;
    private boolean brokenTangents;
    private int selectedKeyframe;
    private int hoveredKeyframe = NONE;

    public CurveEditorWidget setAxisLabels(String horizontal, String vertical) {
        this.horizontalAxisLabel = horizontal;
        this.verticalAxisLabel = vertical;
        return this;
    }

    public boolean render(String identifier, VfxCurve curve) {
        return render(identifier, curve, DEFAULT_HEIGHT);
    }

    public boolean render(String identifier, VfxCurve curve, float height) {
        adoptIdentifier(identifier);
        ImGui.pushID(identifier);
        boolean modified = renderPresetRow(curve);
        modified |= renderCanvas(curve, Math.max(DEFAULT_HEIGHT * 0.5f, height));
        renderSelectionRow(curve);
        ImGui.popID();
        return modified;
    }

    private void adoptIdentifier(String identifier) {
        if (identifier.equals(activeIdentifier)) {
            return;
        }
        activeIdentifier = identifier;
        selectedKeyframe = 0;
        hoveredKeyframe = NONE;
        dragMode = DragMode.NONE;
    }

    private boolean renderPresetRow(VfxCurve curve) {
        boolean modified = false;
        for (int index = 0; index < PRESETS.size(); index++) {
            if (index > 0) {
                ImGui.sameLine();
            }
            modified |= renderPresetButton(PRESETS.get(index), curve);
        }
        ImGui.sameLine();
        if (ImGui.checkbox("Broken", brokenTangents)) {
            brokenTangents = !brokenTangents;
        }
        return modified;
    }

    private boolean renderPresetButton(CurvePreset preset, VfxCurve curve) {
        if (!ImGui.smallButton(preset.label())) {
            return false;
        }
        curve.clearKeyframes();
        for (VfxCurve.Keyframe keyframe : preset.shape().keyframes(curve.minimumBound(), curve.maximumBound())) {
            curve.addKeyframe(keyframe);
        }
        selectedKeyframe = 0;
        dragMode = DragMode.NONE;
        return true;
    }

    private void renderSelectionRow(VfxCurve curve) {
        List<VfxCurve.Keyframe> keyframes = curve.keyframes();
        if (selectedKeyframe < 0 || selectedKeyframe >= keyframes.size()) {
            ImGui.textDisabled("no keyframe selected");
            return;
        }
        VfxCurve.Keyframe keyframe = keyframes.get(selectedKeyframe);
        ImGui.textDisabled(String.format(Locale.ROOT, "key %d   %s %.3f   %s %.3f   in %.2f   out %.2f",
                selectedKeyframe, horizontalAxisLabel, keyframe.time(), verticalAxisLabel, keyframe.value(),
                keyframe.inTangent(), keyframe.outTangent()));
    }

    private boolean renderCanvas(VfxCurve curve, float height) {
        float width = Math.max(MINIMUM_WIDTH, ImGui.getContentRegionAvailX());
        float originX = ImGui.getCursorScreenPosX();
        float originY = ImGui.getCursorScreenPosY();
        ImGui.invisibleButton(CANVAS_ID, width, height);
        CurveCanvas canvas = CurveCanvas.of(originX, originY, width, height, curve);
        boolean hovered = ImGui.isItemHovered();
        boolean modified = handleInput(canvas, curve, hovered);
        drawFrame(canvas, originX, originY, width, height);
        drawCurve(canvas, curve);
        drawKeyframes(canvas, curve);
        return modified;
    }

    private boolean handleInput(CurveCanvas canvas, VfxCurve curve, boolean hovered) {
        hoveredKeyframe = hovered ? keyframeAt(canvas, curve) : NONE;
        if (ImGui.isItemActivated()) {
            beginDrag(canvas, curve);
        }
        boolean modified = ImGui.isItemActive() && applyDrag(canvas, curve);
        if (ImGui.isItemDeactivated()) {
            dragMode = DragMode.NONE;
        }
        modified |= handleAddKeyframe(canvas, curve, hovered);
        modified |= handleRemoveKeyframe(curve, hovered);
        return modified;
    }

    private void beginDrag(CurveCanvas canvas, VfxCurve curve) {
        dragMode = tangentHandleAt(canvas, curve);
        if (dragMode != DragMode.NONE) {
            return;
        }
        int index = keyframeAt(canvas, curve);
        if (index == NONE) {
            return;
        }
        selectedKeyframe = index;
        dragMode = DragMode.POINT;
    }

    private boolean applyDrag(CurveCanvas canvas, VfxCurve curve) {
        return switch (dragMode) {
            case POINT -> dragPoint(canvas, curve);
            case IN_TANGENT -> dragTangent(canvas, curve, true);
            case OUT_TANGENT -> dragTangent(canvas, curve, false);
            case NONE -> false;
        };
    }

    private boolean dragPoint(CurveCanvas canvas, VfxCurve curve) {
        List<VfxCurve.Keyframe> keyframes = curve.keyframes();
        if (selectedKeyframe < 0 || selectedKeyframe >= keyframes.size()) {
            return false;
        }
        VfxCurve.Keyframe current = keyframes.get(selectedKeyframe);
        float time = clampTime(keyframes, selectedKeyframe, canvas.timeAt(ImGui.getMousePosX()));
        float value = canvas.valueAt(ImGui.getMousePosY());
        if (current.time() == time && current.value() == value) {
            return false;
        }
        selectedKeyframe = curve.setKeyframe(selectedKeyframe,
                new VfxCurve.Keyframe(time, value, current.inTangent(), current.outTangent()));
        return true;
    }

    private static float clampTime(List<VfxCurve.Keyframe> keyframes, int index, float time) {
        float lowest = index > 0 ? keyframes.get(index - 1).time() + TIME_EPSILON : 0.0f;
        float highest = index < keyframes.size() - 1 ? keyframes.get(index + 1).time() - TIME_EPSILON : 1.0f;
        return Math.clamp(time, Math.min(lowest, highest), Math.max(lowest, highest));
    }

    private boolean dragTangent(CurveCanvas canvas, VfxCurve curve, boolean incoming) {
        List<VfxCurve.Keyframe> keyframes = curve.keyframes();
        if (selectedKeyframe < 0 || selectedKeyframe >= keyframes.size()) {
            return false;
        }
        VfxCurve.Keyframe current = keyframes.get(selectedKeyframe);
        float tangent = tangentFromMouse(canvas, current, incoming);
        VfxCurve.Keyframe updated = retangent(current, tangent, incoming);
        if (updated.inTangent() == current.inTangent() && updated.outTangent() == current.outTangent()) {
            return false;
        }
        selectedKeyframe = curve.setKeyframe(selectedKeyframe, updated);
        return true;
    }

    private VfxCurve.Keyframe retangent(VfxCurve.Keyframe keyframe, float tangent, boolean incoming) {
        if (!brokenTangents) {
            return new VfxCurve.Keyframe(keyframe.time(), keyframe.value(), tangent, tangent);
        }
        if (incoming) {
            return new VfxCurve.Keyframe(keyframe.time(), keyframe.value(), tangent, keyframe.outTangent());
        }
        return new VfxCurve.Keyframe(keyframe.time(), keyframe.value(), keyframe.inTangent(), tangent);
    }

    private static float tangentFromMouse(CurveCanvas canvas, VfxCurve.Keyframe keyframe, boolean incoming) {
        float deltaX = ImGui.getMousePosX() - canvas.screenX(keyframe.time());
        float deltaY = ImGui.getMousePosY() - canvas.screenY(keyframe.value());
        float horizontal = incoming
                ? Math.min(deltaX, -MINIMUM_HANDLE_PIXELS)
                : Math.max(deltaX, MINIMUM_HANDLE_PIXELS);
        float timeDelta = horizontal / canvas.width();
        float valueDelta = -deltaY / canvas.height() * canvas.valueSpan();
        return Math.clamp(valueDelta / timeDelta, -MAXIMUM_TANGENT, MAXIMUM_TANGENT);
    }

    private boolean handleAddKeyframe(CurveCanvas canvas, VfxCurve curve, boolean hovered) {
        if (!hovered || hoveredKeyframe != NONE || !ImGui.isMouseDoubleClicked(ImGuiMouseButton.Left)) {
            return false;
        }
        float time = canvas.timeAt(ImGui.getMousePosX());
        float value = canvas.valueAt(ImGui.getMousePosY());
        float slope = slopeAt(curve, time);
        selectedKeyframe = curve.addKeyframe(new VfxCurve.Keyframe(time, value, slope, slope));
        dragMode = DragMode.POINT;
        return true;
    }

    private static float slopeAt(VfxCurve curve, float time) {
        float ahead = curve.evaluate(Math.min(1.0f, time + SLOPE_STEP));
        float behind = curve.evaluate(Math.max(0.0f, time - SLOPE_STEP));
        return Math.clamp((ahead - behind) / (2.0f * SLOPE_STEP), -MAXIMUM_TANGENT, MAXIMUM_TANGENT);
    }

    private boolean handleRemoveKeyframe(VfxCurve curve, boolean hovered) {
        boolean requested = hovered && hoveredKeyframe != NONE
                && ImGui.isMouseClicked(ImGuiMouseButton.Right);
        if (!requested || curve.keyframes().size() <= MINIMUM_KEYFRAMES) {
            return false;
        }
        curve.removeKeyframe(hoveredKeyframe);
        hoveredKeyframe = NONE;
        selectedKeyframe = Math.clamp(selectedKeyframe, 0, curve.keyframes().size() - 1);
        dragMode = DragMode.NONE;
        return true;
    }

    private static int keyframeAt(CurveCanvas canvas, VfxCurve curve) {
        List<VfxCurve.Keyframe> keyframes = curve.keyframes();
        for (int index = 0; index < keyframes.size(); index++) {
            VfxCurve.Keyframe keyframe = keyframes.get(index);
            ScreenPoint point = new ScreenPoint(canvas.screenX(keyframe.time()), canvas.screenY(keyframe.value()));
            if (withinHitRadius(point, HIT_RADIUS)) {
                return index;
            }
        }
        return NONE;
    }

    private DragMode tangentHandleAt(CurveCanvas canvas, VfxCurve curve) {
        List<VfxCurve.Keyframe> keyframes = curve.keyframes();
        if (selectedKeyframe < 0 || selectedKeyframe >= keyframes.size()) {
            return DragMode.NONE;
        }
        VfxCurve.Keyframe keyframe = keyframes.get(selectedKeyframe);
        if (withinHitRadius(tangentHandle(canvas, keyframe, true), HIT_RADIUS)) {
            return DragMode.IN_TANGENT;
        }
        if (withinHitRadius(tangentHandle(canvas, keyframe, false), HIT_RADIUS)) {
            return DragMode.OUT_TANGENT;
        }
        return DragMode.NONE;
    }

    private static boolean withinHitRadius(ScreenPoint point, float radius) {
        float deltaX = ImGui.getMousePosX() - point.x();
        float deltaY = ImGui.getMousePosY() - point.y();
        return deltaX * deltaX + deltaY * deltaY <= radius * radius;
    }

    private static ScreenPoint tangentHandle(CurveCanvas canvas, VfxCurve.Keyframe keyframe, boolean incoming) {
        float tangent = incoming ? keyframe.inTangent() : keyframe.outTangent();
        float pixelSlope = tangent * (canvas.height() / canvas.valueSpan()) / canvas.width();
        float length = (float) Math.sqrt(1.0f + pixelSlope * pixelSlope);
        float directionX = (incoming ? -1.0f : 1.0f) / length;
        float directionY = (incoming ? pixelSlope : -pixelSlope) / length;
        return new ScreenPoint(canvas.screenX(keyframe.time()) + directionX * HANDLE_LENGTH,
                canvas.screenY(keyframe.value()) + directionY * HANDLE_LENGTH);
    }

    private void drawFrame(CurveCanvas canvas, float originX, float originY, float width, float height) {
        var drawList = ImGui.getWindowDrawList();
        drawList.addRectFilled(originX, originY, originX + width, originY + height,
                COLOR_BACKGROUND, EditorStyle.FRAME_ROUNDING);
        drawList.addRect(originX, originY, originX + width, originY + height,
                COLOR_BORDER, EditorStyle.FRAME_ROUNDING);
        drawGrid(canvas);
        drawAxisLabels(canvas, originX, originY, width, height);
    }

    private static void drawGrid(CurveCanvas canvas) {
        var drawList = ImGui.getWindowDrawList();
        for (int column = 0; column <= GRID_COLUMNS; column++) {
            float x = canvas.screenX(column / (float) GRID_COLUMNS);
            int color = column == 0 || column == GRID_COLUMNS ? COLOR_GRID_STRONG : COLOR_GRID;
            drawList.addLine(x, canvas.minY(), x, canvas.maxY(), color, GRID_THICKNESS);
        }
        for (int row = 0; row <= GRID_ROWS; row++) {
            float y = canvas.minY() + row / (float) GRID_ROWS * canvas.height();
            int color = row == 0 || row == GRID_ROWS ? COLOR_GRID_STRONG : COLOR_GRID;
            drawList.addLine(canvas.minX(), y, canvas.maxX(), y, color, GRID_THICKNESS);
        }
    }

    private void drawAxisLabels(CurveCanvas canvas, float originX, float originY, float width, float height) {
        var drawList = ImGui.getWindowDrawList();
        drawList.addText(originX + EditorStyle.INNER_SPACING, originY + 2.0f,
                EditorStyle.COLOR_TEXT_MUTED, verticalAxisLabel);
        String horizontal = horizontalAxisLabel;
        float horizontalWidth = ImGui.calcTextSize(horizontal).x;
        float baseline = originY + height - PADDING_BOTTOM + 2.0f;
        drawList.addText(originX + (width - horizontalWidth) * 0.5f, baseline,
                EditorStyle.COLOR_TEXT_MUTED, horizontal);
        drawList.addText(canvas.minX(), baseline, EditorStyle.COLOR_TEXT_MUTED, "0");
        drawList.addText(canvas.maxX() - ImGui.calcTextSize("1").x, baseline, EditorStyle.COLOR_TEXT_MUTED, "1");
        drawBoundLabels(canvas);
    }

    private static void drawBoundLabels(CurveCanvas canvas) {
        var drawList = ImGui.getWindowDrawList();
        float lineHeight = ImGui.getTextLineHeight();
        String high = String.format(Locale.ROOT, "%.2f", canvas.highValue());
        String low = String.format(Locale.ROOT, "%.2f", canvas.lowValue());
        drawList.addText(canvas.minX() - EditorStyle.INNER_SPACING - ImGui.calcTextSize(high).x,
                canvas.minY(), EditorStyle.COLOR_TEXT_MUTED, high);
        drawList.addText(canvas.minX() - EditorStyle.INNER_SPACING - ImGui.calcTextSize(low).x,
                canvas.maxY() - lineHeight, EditorStyle.COLOR_TEXT_MUTED, low);
    }

    private static void drawCurve(CurveCanvas canvas, VfxCurve curve) {
        float[] samples = curve.sample(CURVE_SAMPLES);
        if (samples.length < 2) {
            return;
        }
        var drawList = ImGui.getWindowDrawList();
        float previousX = canvas.screenX(0.0f);
        float previousY = canvas.screenY(samples[0]);
        for (int index = 1; index < samples.length; index++) {
            float x = canvas.screenX(index / (float) (samples.length - 1));
            float y = canvas.screenY(samples[index]);
            drawList.addQuadFilled(previousX, previousY, x, y, x, canvas.maxY(),
                    previousX, canvas.maxY(), COLOR_CURVE_FILL);
            drawList.addLine(previousX, previousY, x, y, COLOR_CURVE, CURVE_THICKNESS);
            previousX = x;
            previousY = y;
        }
    }

    private void drawKeyframes(CurveCanvas canvas, VfxCurve curve) {
        List<VfxCurve.Keyframe> keyframes = curve.keyframes();
        if (selectedKeyframe >= 0 && selectedKeyframe < keyframes.size()) {
            drawTangents(canvas, keyframes.get(selectedKeyframe));
        }
        for (int index = 0; index < keyframes.size(); index++) {
            drawKeyframe(canvas, keyframes.get(index), index);
        }
        drawHoverTooltip(keyframes);
    }

    private void drawKeyframe(CurveCanvas canvas, VfxCurve.Keyframe keyframe, int index) {
        var drawList = ImGui.getWindowDrawList();
        float x = canvas.screenX(keyframe.time());
        float y = canvas.screenY(keyframe.value());
        boolean selected = index == selectedKeyframe;
        float radius = index == hoveredKeyframe || selected ? POINT_HOVER_RADIUS : POINT_RADIUS;
        int color = selected ? COLOR_POINT_SELECTED : index == hoveredKeyframe ? COLOR_POINT_HOVER : COLOR_POINT;
        drawList.addCircleFilled(x, y, radius, color);
        drawList.addCircle(x, y, radius + 1.0f, COLOR_BACKGROUND, 0, GRID_THICKNESS);
    }

    private void drawTangents(CurveCanvas canvas, VfxCurve.Keyframe keyframe) {
        var drawList = ImGui.getWindowDrawList();
        float x = canvas.screenX(keyframe.time());
        float y = canvas.screenY(keyframe.value());
        for (boolean incoming : new boolean[]{true, false}) {
            ScreenPoint handle = tangentHandle(canvas, keyframe, incoming);
            boolean active = withinHitRadius(handle, HIT_RADIUS);
            drawList.addLine(x, y, handle.x(), handle.y(), COLOR_HANDLE, GRID_THICKNESS);
            drawList.addCircleFilled(handle.x(), handle.y(),
                    active ? HANDLE_RADIUS + 1.5f : HANDLE_RADIUS, COLOR_HANDLE);
        }
    }

    private void drawHoverTooltip(List<VfxCurve.Keyframe> keyframes) {
        if (hoveredKeyframe < 0 || hoveredKeyframe >= keyframes.size()) {
            return;
        }
        VfxCurve.Keyframe keyframe = keyframes.get(hoveredKeyframe);
        ImGui.setTooltip(String.format(Locale.ROOT, "%s %.3f   %s %.3f",
                horizontalAxisLabel, keyframe.time(), verticalAxisLabel, keyframe.value()));
    }

    private static List<VfxCurve.Keyframe> riseShape(float low, float high) {
        float slope = high - low;
        return List.of(new VfxCurve.Keyframe(0.0f, low, slope, slope),
                new VfxCurve.Keyframe(1.0f, high, slope, slope));
    }

    private static List<VfxCurve.Keyframe> fallShape(float low, float high) {
        float slope = low - high;
        return List.of(new VfxCurve.Keyframe(0.0f, high, slope, slope),
                new VfxCurve.Keyframe(1.0f, low, slope, slope));
    }

    private static List<VfxCurve.Keyframe> easeInShape(float low, float high) {
        float slope = 2.0f * (high - low);
        return List.of(new VfxCurve.Keyframe(0.0f, low, 0.0f, 0.0f),
                new VfxCurve.Keyframe(1.0f, high, slope, slope));
    }

    private static List<VfxCurve.Keyframe> easeOutShape(float low, float high) {
        float slope = 2.0f * (high - low);
        return List.of(new VfxCurve.Keyframe(0.0f, low, slope, slope),
                new VfxCurve.Keyframe(1.0f, high, 0.0f, 0.0f));
    }

    private static List<VfxCurve.Keyframe> easeInOutShape(float low, float high) {
        return List.of(new VfxCurve.Keyframe(0.0f, low, 0.0f, 0.0f),
                new VfxCurve.Keyframe(1.0f, high, 0.0f, 0.0f));
    }

    private static List<VfxCurve.Keyframe> bellShape(float low, float high) {
        return List.of(new VfxCurve.Keyframe(0.0f, low, 0.0f, 0.0f),
                new VfxCurve.Keyframe(0.5f, high, 0.0f, 0.0f),
                new VfxCurve.Keyframe(1.0f, low, 0.0f, 0.0f));
    }

    private static List<VfxCurve.Keyframe> constantShape(float low, float high) {
        return List.of(new VfxCurve.Keyframe(0.0f, high, 0.0f, 0.0f),
                new VfxCurve.Keyframe(1.0f, high, 0.0f, 0.0f));
    }

    private record CurveCanvas(float minX, float minY, float maxX, float maxY, float lowValue, float highValue) {

        static CurveCanvas of(float originX, float originY, float width, float height, VfxCurve curve) {
            float low = curve.minimumBound();
            float high = Math.max(curve.maximumBound(), curve.minimumBound() + MINIMUM_SPAN);
            return new CurveCanvas(originX + PADDING_LEFT, originY + PADDING_TOP,
                    originX + width - PADDING_RIGHT, originY + height - PADDING_BOTTOM, low, high);
        }

        float width() {
            return Math.max(1.0f, maxX - minX);
        }

        float height() {
            return Math.max(1.0f, maxY - minY);
        }

        float valueSpan() {
            return Math.max(MINIMUM_SPAN, highValue - lowValue);
        }

        float screenX(float time) {
            return minX + time * width();
        }

        float screenY(float value) {
            float normalized = (Math.clamp(value, lowValue, highValue) - lowValue) / valueSpan();
            return maxY - normalized * height();
        }

        float timeAt(float screenX) {
            return Math.clamp((screenX - minX) / width(), 0.0f, 1.0f);
        }

        float valueAt(float screenY) {
            float normalized = Math.clamp((maxY - screenY) / height(), 0.0f, 1.0f);
            return lowValue + normalized * valueSpan();
        }
    }
}
