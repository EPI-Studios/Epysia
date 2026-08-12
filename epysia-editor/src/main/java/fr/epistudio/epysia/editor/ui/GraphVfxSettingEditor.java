package fr.epistudio.epysia.editor.ui;

import fr.epistudio.epysia.editor.shell.EditorScale;
import fr.epistudio.epysia.editor.shell.EditorStyle;
import fr.epistudio.epysia.editor.ui.widgets.CurveEditorWidget;
import fr.epistudio.epysia.editor.ui.widgets.GradientEditorWidget;
import fr.epistudio.epysia.graph.NodeSetting;
import fr.epistudio.epysia.i18n.I18n;
import fr.epistudio.epysia.i18n.TextKey;
import fr.epistudio.epysia.vfx.lut.VfxCurve;
import fr.epistudio.epysia.vfx.lut.VfxGradient;
import fr.epistudio.epysia.editor.ui.kit.Texts;
import imgui.ImDrawList;
import imgui.ImGui;
import imgui.flag.ImGuiCond;
import imgui.type.ImBoolean;
import org.joml.Vector4f;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

final class GraphVfxSettingEditor {

    private static final float SWATCH_WIDTH = 160.0f;
    private static final float SWATCH_HEIGHT = 36.0f;
    private static final float SWATCH_PADDING = 3.0f;
    private static final float WINDOW_WIDTH = 460.0f;
    private static final float WINDOW_HEIGHT = 340.0f;
    private static final float BORDER_THICKNESS = 1.0f;
    private static final float CURVE_THICKNESS = 1.5f;
    private static final float MINIMUM_SPAN = 0.0001f;
    private static final int CURVE_SAMPLES = 56;
    private static final int GRADIENT_STRIPES = 64;
    private static final int COLOR_BACKGROUND = EditorStyle.rgb(24, 24, 26);
    private static final int COLOR_BORDER = EditorStyle.rgba(255, 255, 255, 50);
    private static final int COLOR_CURVE = EditorStyle.rgb(120, 200, 255);
    private static final String WINDOW_SUFFIX = "##vfx-setting-editor";

    private final Map<String, CurveSlot> curveSlots = new HashMap<>();
    private final Map<String, GradientSlot> gradientSlots = new HashMap<>();
    private final Set<String> visited = new HashSet<>();
    private final ImBoolean windowOpen = new ImBoolean(true);

    private String openIdentifier = "";
    private String openLabel = "";
    private float swatchScale = 1.0f;

    void setSwatchScale(float scale) {
        swatchScale = scale;
    }

    boolean render(String identifier, NodeSetting setting, String stored, Consumer<String> onChanged) {
        return switch (setting.kind()) {
            case CURVE -> renderCurve(identifier, setting, stored, onChanged);
            case GRADIENT -> renderGradient(identifier, setting, stored, onChanged);
            default -> false;
        };
    }

    private boolean renderCurve(String identifier, NodeSetting setting, String stored,
                                Consumer<String> onChanged) {
        visited.add(identifier);
        CurveSlot slot = curveSlots.computeIfAbsent(identifier,
                ignored -> new CurveSlot(setting.key()));
        slot.onChanged = onChanged;
        if (!slot.encoded.equals(stored)) {
            slot.curve = VfxCurve.isEncodedCurve(stored) ? VfxCurve.decode(stored) : VfxCurve.linear(0.0f, 1.0f);
            slot.encoded = stored;
        }
        Texts.muted(setting.key());
        drawCurveSwatch(identifier, slot.curve);
        openOnClick(identifier, setting.key());
        return true;
    }

    private boolean renderGradient(String identifier, NodeSetting setting, String stored,
                                   Consumer<String> onChanged) {
        visited.add(identifier);
        GradientSlot slot = gradientSlots.computeIfAbsent(identifier, ignored -> new GradientSlot());
        slot.onChanged = onChanged;
        if (!slot.encoded.equals(stored)) {
            slot.gradient = VfxGradient.isEncodedGradient(stored)
                    ? VfxGradient.decode(stored)
                    : VfxGradient.opaqueWhite();
            slot.encoded = stored;
        }
        Texts.muted(setting.key());
        drawGradientSwatch(identifier, slot.gradient);
        openOnClick(identifier, setting.key());
        return true;
    }

    private void openOnClick(String identifier, String label) {
        if (!ImGui.isItemHovered()) {
            return;
        }
        ImGui.setTooltip(I18n.translate(TextKey.EDITOR_GRAPH_VFX_SETTING_EDITOR_CLICK_TO_EDIT, label));
        if (ImGui.isItemClicked()) {
            openIdentifier = identifier;
            openLabel = label;
            windowOpen.set(true);
        }
    }

    void renderOverlay() {
        evictUnvisited();
        if (openIdentifier.isEmpty()) {
            return;
        }
        ImGui.setNextWindowSize(EditorScale.of(WINDOW_WIDTH), EditorScale.of(WINDOW_HEIGHT), ImGuiCond.FirstUseEver);
        if (ImGui.begin(openLabel + WINDOW_SUFFIX, windowOpen)) {
            renderOpenSlot();
        }
        ImGui.end();
        if (!windowOpen.get()) {
            openIdentifier = "";
        }
    }

    private void renderOpenSlot() {
        if (curveSlots.containsKey(openIdentifier)) {
            renderCurveSlot(curveSlots.get(openIdentifier));
            return;
        }
        if (gradientSlots.containsKey(openIdentifier)) {
            renderGradientSlot(gradientSlots.get(openIdentifier));
        }
    }

    private void renderCurveSlot(CurveSlot slot) {
        if (!slot.widget.render(openIdentifier, slot.curve)) {
            return;
        }
        slot.encoded = slot.curve.encode();
        slot.onChanged.accept(slot.encoded);
    }

    private void renderGradientSlot(GradientSlot slot) {
        if (!slot.widget.render(openIdentifier, slot.gradient)) {
            return;
        }
        slot.encoded = slot.gradient.encode();
        slot.onChanged.accept(slot.encoded);
    }

    private void evictUnvisited() {
        curveSlots.keySet().retainAll(visited);
        gradientSlots.keySet().retainAll(visited);
        if (!visited.contains(openIdentifier)) {
            openIdentifier = "";
        }
        visited.clear();
    }

    private void drawCurveSwatch(String identifier, VfxCurve curve) {
        ImDrawList drawList = beginSwatch(identifier);
        float padding = EditorScale.of(SWATCH_PADDING) * swatchScale;
        float minX = ImGui.getItemRectMinX() + padding;
        float maxX = ImGui.getItemRectMaxX() - padding;
        float minY = ImGui.getItemRectMinY() + padding;
        float maxY = ImGui.getItemRectMaxY() - padding;
        float span = Math.max(MINIMUM_SPAN, curve.maximumBound() - curve.minimumBound());
        float previousX = minX;
        float previousY = curveScreenY(curve, 0.0f, span, minY, maxY);
        for (int index = 1; index < CURVE_SAMPLES; index++) {
            float progress = index / (float) (CURVE_SAMPLES - 1);
            float x = minX + (maxX - minX) * progress;
            float y = curveScreenY(curve, progress, span, minY, maxY);
            drawList.addLine(previousX, previousY, x, y, COLOR_CURVE, EditorScale.of(CURVE_THICKNESS) * swatchScale);
            previousX = x;
            previousY = y;
        }
        endSwatch(drawList);
    }

    private static float curveScreenY(VfxCurve curve, float progress, float span,
                                      float minY, float maxY) {
        float normalized = (curve.evaluate(progress) - curve.minimumBound()) / span;
        return maxY - (maxY - minY) * Math.clamp(normalized, 0.0f, 1.0f);
    }

    private void drawGradientSwatch(String identifier, VfxGradient gradient) {
        ImDrawList drawList = beginSwatch(identifier);
        float padding = EditorScale.of(SWATCH_PADDING) * swatchScale;
        float minX = ImGui.getItemRectMinX() + padding;
        float maxX = ImGui.getItemRectMaxX() - padding;
        float minY = ImGui.getItemRectMinY() + padding;
        float maxY = ImGui.getItemRectMaxY() - padding;
        float stripeWidth = (maxX - minX) / GRADIENT_STRIPES;
        for (int index = 0; index < GRADIENT_STRIPES; index++) {
            Vector4f color = gradient.evaluate(index / (float) (GRADIENT_STRIPES - 1));
            float left = minX + stripeWidth * index;
            drawList.addRectFilled(left, minY, left + stripeWidth, maxY, packColor(color));
        }
        endSwatch(drawList);
    }

    private ImDrawList beginSwatch(String identifier) {
        ImGui.invisibleButton("##vfx-swatch-" + identifier,
                EditorScale.of(SWATCH_WIDTH) * swatchScale, EditorScale.of(SWATCH_HEIGHT) * swatchScale);
        ImDrawList drawList = ImGui.getWindowDrawList();
        drawList.addRectFilled(ImGui.getItemRectMinX(), ImGui.getItemRectMinY(),
                ImGui.getItemRectMaxX(), ImGui.getItemRectMaxY(), COLOR_BACKGROUND);
        return drawList;
    }

    private void endSwatch(ImDrawList drawList) {
        drawList.addRect(ImGui.getItemRectMinX(), ImGui.getItemRectMinY(),
                ImGui.getItemRectMaxX(), ImGui.getItemRectMaxY(), COLOR_BORDER,
                0.0f, 0, EditorScale.of(BORDER_THICKNESS) * swatchScale);
    }

    private static int packColor(Vector4f color) {
        return EditorStyle.rgba(toByte(color.x), toByte(color.y), toByte(color.z), toByte(color.w));
    }

    private static int toByte(float value) {
        return Math.round(Math.clamp(value, 0.0f, 1.0f) * 255.0f);
    }

    private static final class CurveSlot {

        final CurveEditorWidget widget;
        Consumer<String> onChanged = ignored -> {
        };
        VfxCurve curve = VfxCurve.linear(0.0f, 1.0f);
        String encoded = "";

        CurveSlot(String verticalAxisLabel) {
            this.widget = new CurveEditorWidget().setAxisLabels(
                    I18n.translate(TextKey.EDITOR_GRAPH_VFX_SETTING_EDITOR_LIFE_AXIS), verticalAxisLabel);
        }
    }

    private static final class GradientSlot {

        final GradientEditorWidget widget = new GradientEditorWidget();
        Consumer<String> onChanged = ignored -> {
        };
        VfxGradient gradient = VfxGradient.opaqueWhite();
        String encoded = "";
    }
}
