package fr.epistudio.epysia.vfx.lut;

import fr.epistudio.epysia.graph.GraphAsset;
import fr.epistudio.epysia.graph.GraphNode;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

public final class VfxLutPack {

    public static final int VFX_LUT_RESOLUTION = 256;
    public static final int MISSING_INDEX = -1;

    private record LutSlot(int nodeId, String settingKey, String encoded) {
    }

    private final float[] curveSamples;
    private final float[] gradientSamples;
    private final int curveCount;
    private final int gradientCount;

    private VfxLutPack(List<LutSlot> curveSlots, List<LutSlot> gradientSlots) {
        this.curveCount = curveSlots.size();
        this.gradientCount = gradientSlots.size();
        this.curveSamples = new float[curveCount * VFX_LUT_RESOLUTION];
        this.gradientSamples = new float[gradientCount * VFX_LUT_RESOLUTION * 4];
        bakeCurves(curveSlots);
        bakeGradients(gradientSlots);
    }

    private void bakeCurves(List<LutSlot> slots) {
        for (int index = 0; index < slots.size(); index++) {
            float[] samples = VfxCurve.decode(slots.get(index).encoded()).sample(VFX_LUT_RESOLUTION);
            System.arraycopy(samples, 0, curveSamples, index * VFX_LUT_RESOLUTION, samples.length);
        }
    }

    private void bakeGradients(List<LutSlot> slots) {
        int stride = VFX_LUT_RESOLUTION * 4;
        for (int index = 0; index < slots.size(); index++) {
            float[] samples = VfxGradient.decode(slots.get(index).encoded()).sample(VFX_LUT_RESOLUTION);
            System.arraycopy(samples, 0, gradientSamples, index * stride, samples.length);
        }
    }

    public static VfxLutPack build(GraphAsset asset) {
        return new VfxLutPack(curveSlots(asset), gradientSlots(asset));
    }

    public float[] curveSamples() {
        return curveSamples;
    }

    public float[] gradientSamples() {
        return gradientSamples;
    }

    public int curveCount() {
        return curveCount;
    }

    public int gradientCount() {
        return gradientCount;
    }

    public static int curveIndexOf(GraphAsset asset, int nodeId, String settingKey) {
        return indexOf(curveSlots(asset), nodeId, settingKey);
    }

    public static int gradientIndexOf(GraphAsset asset, int nodeId, String settingKey) {
        return indexOf(gradientSlots(asset), nodeId, settingKey);
    }

    private static int indexOf(List<LutSlot> slots, int nodeId, String settingKey) {
        for (int index = 0; index < slots.size(); index++) {
            LutSlot slot = slots.get(index);
            if (slot.nodeId() == nodeId && slot.settingKey().equals(settingKey)) {
                return index;
            }
        }
        return MISSING_INDEX;
    }

    private static List<LutSlot> curveSlots(GraphAsset asset) {
        return collectSlots(asset, true);
    }

    private static List<LutSlot> gradientSlots(GraphAsset asset) {
        return collectSlots(asset, false);
    }

    private static List<LutSlot> collectSlots(GraphAsset asset, boolean wantCurves) {
        List<LutSlot> slots = new ArrayList<>();
        for (GraphNode node : orderedNodes(asset)) {
            for (Map.Entry<String, Object> entry : orderedValues(node).entrySet()) {
                String encoded = String.valueOf(entry.getValue());
                if (matches(encoded, wantCurves)) {
                    slots.add(new LutSlot(node.id(), entry.getKey(), encoded));
                }
            }
        }
        return slots;
    }

    private static boolean matches(String encoded, boolean wantCurves) {
        return wantCurves ? VfxCurve.isEncodedCurve(encoded) : VfxGradient.isEncodedGradient(encoded);
    }

    private static List<GraphNode> orderedNodes(GraphAsset asset) {
        List<GraphNode> ordered = new ArrayList<>(asset.nodes());
        ordered.sort(Comparator.comparingInt(GraphNode::id));
        return ordered;
    }

    private static Map<String, Object> orderedValues(GraphNode node) {
        return new TreeMap<>(node.values());
    }
}
