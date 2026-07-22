package fr.epistudio.epysia.vfx.lut;

import fr.epistudio.epysia.graph.GraphAsset;
import fr.epistudio.epysia.graph.GraphJsonCodec;
import fr.epistudio.epysia.graph.GraphKind;
import fr.epistudio.epysia.graph.GraphNode;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class VfxLutTest {

    @Test
    void curveAndGradientSurviveGraphJsonRoundTrip() {
        GraphAsset asset = new GraphAsset();
        asset.setKind(GraphKind.VFX);
        VfxCurve curve = VfxCurve.linear(0.25f, 2.5f);
        curve.setBounds(0.0f, 4.0f);
        VfxGradient gradient = VfxGradient.opaqueWhite();
        gradient.addColorStop(new VfxGradient.ColorStop(0.5f, 1.0f, 0.25f, 0.0f));
        gradient.addAlphaStop(new VfxGradient.AlphaStop(0.5f, 0.125f));
        GraphNode node = asset.addNode("vfx.curve", 0.0f, 0.0f);
        node.values().put("curve", curve.encode());
        node.values().put("gradient", gradient.encode());

        GraphJsonCodec codec = new GraphJsonCodec();
        GraphNode decodedNode = codec.read(codec.write(asset)).nodes().get(0);
        VfxCurve decodedCurve = VfxCurve.decode(String.valueOf(decodedNode.values().get("curve")));
        VfxGradient decodedGradient = VfxGradient.decode(String.valueOf(decodedNode.values().get("gradient")));

        assertEquals(curve.encode(), decodedCurve.encode());
        assertEquals(gradient.encode(), decodedGradient.encode());
        assertEquals(4.0f, decodedCurve.maximumBound());
        assertEquals(3, decodedGradient.colorStops().size());
        assertEquals(0.125f, decodedGradient.evaluate(0.5f).w);
    }

    @Test
    void hermiteEvaluationReturnsExactKeyframeValues() {
        VfxCurve curve = new VfxCurve();
        curve.addKeyframe(new VfxCurve.Keyframe(0.0f, 1.0f, 0.0f, 3.0f));
        curve.addKeyframe(new VfxCurve.Keyframe(0.5f, -2.0f, -4.0f, 6.0f));
        curve.addKeyframe(new VfxCurve.Keyframe(1.0f, 0.5f, 2.0f, 0.0f));

        assertEquals(1.0f, curve.evaluate(0.0f), 1.0e-6f);
        assertEquals(-2.0f, curve.evaluate(0.5f), 1.0e-6f);
        assertEquals(0.5f, curve.evaluate(1.0f), 1.0e-6f);
        assertEquals(1.0f, curve.evaluate(-4.0f), 1.0e-6f);
        assertEquals(0.5f, curve.evaluate(9.0f), 1.0e-6f);

        float[] samples = curve.sample(VfxLutPack.VFX_LUT_RESOLUTION);
        assertEquals(VfxLutPack.VFX_LUT_RESOLUTION, samples.length);
        assertEquals(1.0f, samples[0], 1.0e-6f);
        assertEquals(0.5f, samples[samples.length - 1], 1.0e-6f);
    }

    @Test
    void lutIndicesFollowAscendingNodeIdOrder() {
        GraphAsset asset = new GraphAsset();
        GraphNode second = new GraphNode(2, "vfx.curve");
        GraphNode first = new GraphNode(1, "vfx.gradient");
        asset.nodes().add(second);
        asset.nodes().add(first);
        GraphNode third = asset.addNode("vfx.curve", 0.0f, 0.0f);
        third.values().put("curve", VfxCurve.constant(0.75f).encode());
        second.values().put("curve", VfxCurve.constant(0.25f).encode());
        second.values().put("tint", VfxGradient.opaqueWhite().encode());
        first.values().put("tint", VfxGradient.opaqueWhite().encode());

        assertEquals(0, VfxLutPack.curveIndexOf(asset, second.id(), "curve"));
        assertEquals(1, VfxLutPack.curveIndexOf(asset, third.id(), "curve"));
        assertEquals(0, VfxLutPack.gradientIndexOf(asset, first.id(), "tint"));
        assertEquals(1, VfxLutPack.gradientIndexOf(asset, second.id(), "tint"));
        assertEquals(VfxLutPack.MISSING_INDEX, VfxLutPack.curveIndexOf(asset, first.id(), "curve"));

        VfxLutPack pack = VfxLutPack.build(asset);
        assertEquals(2, pack.curveCount());
        assertEquals(2, pack.gradientCount());
        assertEquals(2 * VfxLutPack.VFX_LUT_RESOLUTION, pack.curveSamples().length);
        assertEquals(2 * VfxLutPack.VFX_LUT_RESOLUTION * 4, pack.gradientSamples().length);
        assertEquals(0.25f, pack.curveSamples()[0], 1.0e-6f);
        assertEquals(0.75f, pack.curveSamples()[VfxLutPack.VFX_LUT_RESOLUTION], 1.0e-6f);
    }
}
