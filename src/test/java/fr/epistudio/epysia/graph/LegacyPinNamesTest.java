package fr.epistudio.epysia.graph;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class LegacyPinNamesTest {

    private static final String PLAY = "reflect:fr.epistudio.epysia.components.SpriteFlipbook#play(java.lang.String)";
    private static final String MOVE =
            "reflect:fr.epistudio.epysia.physics.components.CharacterController2D#setDesiredMove(float)";

    @Test
    void graphsSavedBeforeParameterNamesKeepTheirValuesAndWires() {
        GraphAsset asset = new GraphAsset();
        GraphNode play = new GraphNode(1, PLAY);
        play.values().put("String 1", "idle");
        asset.nodes().add(play);
        asset.nodes().add(new GraphNode(2, MOVE));
        asset.edges().add(new GraphEdge(3, "Value", 2, "Float 1"));

        ReflectionNodes.renameLegacyPins(asset, GraphNodeRegistry.withBuiltins().classResolver());

        assertEquals("idle", play.values().get("Name"));
        assertFalse(play.values().containsKey("String 1"));
        assertEquals("Value", asset.edges().getFirst().fromPin());
        assertFalse(asset.edges().getFirst().toPin().equals("Float 1"));
    }
}
