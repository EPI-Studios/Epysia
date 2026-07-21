package fr.epistudio.epysia.render;

import fr.epistudio.epysia.exceptions.EpysiaException;
import fr.epistudio.epysia.render.backend.DrawCommand;
import fr.epistudio.epysia.render.backend.BindingSetHandle;
import fr.epistudio.epysia.render.backend.MeshHandle;
import fr.epistudio.epysia.render.backend.PipelineHandle;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RenderPassRegistrationTest {

    private static DrawCommand command() {
        return new DrawCommand(new PipelineHandle(1L), new MeshHandle(1L),
                new BindingSetHandle(1L), 0L, 1);
    }

    @Test
    void projectPassIsOrderedBetweenBuiltins() {
        RenderPass outline = RenderPasses.register("TEST_OUTLINE",
                RenderPasses.OPAQUE_3D_ORDER + 10);
        List<RenderPass> ordered = RenderPasses.ordered();
        int opaqueIndex = ordered.indexOf(RenderPasses.OPAQUE_3D);
        int outlineIndex = ordered.indexOf(outline);
        int transparentIndex = ordered.indexOf(RenderPasses.TRANSPARENT_3D);
        assertTrue(opaqueIndex < outlineIndex);
        assertTrue(outlineIndex < transparentIndex);
    }

    @Test
    void frameAcceptsPassRegisteredAfterItWasCreated() {
        Frame frame = new Frame();
        RenderPass late = RenderPasses.register("TEST_LATE", RenderPasses.UI_ORDER + 10);
        frame.submit(late, command());
        assertEquals(1, frame.commandsFor(late).size());
        frame.reset();
        assertEquals(0, frame.commandsFor(late).size());
    }

    @Test
    void reregisteringSameNameAndOrderReturnsSamePass() {
        RenderPass first = RenderPasses.register("TEST_REPLAY", 900);
        RenderPass second = RenderPasses.register("TEST_REPLAY", 900);
        assertSame(first, second);
    }

    @Test
    void reregisteringSameNameAtDifferentOrderIsRejected() {
        RenderPasses.register("TEST_CONFLICT", 910);
        assertThrows(EpysiaException.class, () -> RenderPasses.register("TEST_CONFLICT", 911));
    }

    @Test
    void builtinPassesKeepDeclaredOrder() {
        List<RenderPass> ordered = RenderPasses.ordered();
        assertTrue(ordered.indexOf(RenderPasses.PRE_3D) < ordered.indexOf(RenderPasses.OPAQUE_3D));
        assertTrue(ordered.indexOf(RenderPasses.TRANSPARENT_3D) < ordered.indexOf(RenderPasses.POST));
        assertTrue(ordered.indexOf(RenderPasses.POST) < ordered.indexOf(RenderPasses.UI));
    }
}
