package fr.epistudio.epysia.editor;

import fr.epistudio.epysia.editor.shell.ImGuiShell;

final class EditorFrameProfiler {

    private static final String ENABLED_PROPERTY = "epysia.editor.profiling";
    private static final long REPORT_INTERVAL_NANOS = 1_000_000_000L;
    private static final double NANOS_PER_MILLI = 1_000_000.0;

    private final boolean enabled = Boolean.getBoolean(ENABLED_PROPERTY);

    private long reportStartNanos = System.nanoTime();
    private long pollTotal;
    private long viewTotal;
    private long drawDataTotal;
    private long viewportsTotal;
    private long swapTotal;
    private long frameTotal;
    private int frames;

    void record(long frameStart, long pollEnd, long viewEnd, ImGuiShell shell) {
        if (!enabled) {
            return;
        }
        long frameEnd = System.nanoTime();
        pollTotal += pollEnd - frameStart;
        viewTotal += viewEnd - pollEnd;
        drawDataTotal += shell.drawDataNanos();
        viewportsTotal += shell.viewportsNanos();
        swapTotal += shell.swapNanos();
        frameTotal += frameEnd - frameStart;
        frames++;
        if (frameEnd - reportStartNanos >= REPORT_INTERVAL_NANOS) {
            report();
            reportStartNanos = frameEnd;
        }
    }

    private void report() {
        double frameMillis = frameTotal / NANOS_PER_MILLI / frames;
        System.out.printf(
                "[editor] %.2f ms/frame (%.0f fps) | poll %.3f | views %.3f | imguiDraw %.3f | viewports %.3f | swap %.3f%n",
                frameMillis, 1000.0 / frameMillis,
                pollTotal / NANOS_PER_MILLI / frames,
                viewTotal / NANOS_PER_MILLI / frames,
                drawDataTotal / NANOS_PER_MILLI / frames,
                viewportsTotal / NANOS_PER_MILLI / frames,
                swapTotal / NANOS_PER_MILLI / frames);
        pollTotal = 0;
        viewTotal = 0;
        drawDataTotal = 0;
        viewportsTotal = 0;
        swapTotal = 0;
        frameTotal = 0;
        frames = 0;
    }
}
