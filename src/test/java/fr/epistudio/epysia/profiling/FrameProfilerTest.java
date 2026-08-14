package fr.epistudio.epysia.profiling;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FrameProfilerTest {

    @Test
    void aScopeOpenedInsideAnotherBecomesItsChild() {
        FrameProfiler profiler = new FrameProfiler();
        profiler.begin("render");
        profiler.begin("collect");
        profiler.record("collect/Meshes", 1_000L);
        profiler.end();
        profiler.end();
        profiler.publishFrame();

        List<ProfileNode> roots = profiler.frame().roots();

        assertEquals(1, roots.size(), "render is the only root");
        ProfileNode collect = roots.getFirst().children().getFirst();
        assertEquals("collect", collect.name());
        assertEquals("collect/Meshes", collect.children().getFirst().name());
    }

    @Test
    void selfTimeExcludesTheChildren() {
        FrameProfiler profiler = new FrameProfiler();
        profiler.begin("parent");
        profiler.record("child", 500L);
        profiler.end();
        profiler.publishFrame();

        ProfileNode parent = profiler.frame().roots().getFirst();

        assertEquals(parent.totalNanos() - 500L, parent.selfNanos(),
                "a parent's self time is what it did outside its children");
    }

    @Test
    void aSectionThatDidNotRunLeavesTheTree() {
        FrameProfiler profiler = new FrameProfiler();
        profiler.record("once", 10L);
        profiler.publishFrame();
        profiler.record("other", 10L);
        profiler.publishFrame();

        assertFalse(profiler.sections().containsKey("once"),
                "a section absent from this frame must not linger at zero");
        assertTrue(profiler.sections().containsKey("other"));
    }

    @Test
    void everyClosedScopeLeavesASpanAtItsOwnDepth() {
        FrameProfiler profiler = new FrameProfiler();
        profiler.begin("outer");
        profiler.begin("inner");
        profiler.end();
        profiler.end();
        profiler.publishFrame();

        List<ProfileSpan> spans = profiler.frame().spans();

        assertEquals(2, spans.size());
        assertEquals(1, spans.getFirst().depth(), "the inner scope closes first, one level down");
        assertEquals(0, spans.get(1).depth());
    }

    @Test
    void theCsvCarriesOneRowPerNodeWithItsPath() {
        FrameProfiler profiler = new FrameProfiler();
        profiler.begin("render");
        profiler.record("drain", 250L);
        profiler.end();
        profiler.publishFrame();

        List<String> lines = ProfileCsv.of(profiler.frame()).lines().toList();

        assertEquals("depth,path,name,totalMs,selfMs,calls", lines.getFirst());
        assertTrue(lines.get(1).startsWith("0,\"render\",\"render\","), lines.get(1));
        assertTrue(lines.get(2).startsWith("1,\"render/drain\",\"drain\","), lines.get(2));
    }
}
