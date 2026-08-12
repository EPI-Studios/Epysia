package fr.epistudio.epysia.render.debug;

import fr.epistudio.epysia.logging.Logger;

import java.util.Optional;

public final class FrameCaptureSchedule {

    private static final String FRAME_PROPERTY = "epysia.renderdoc.frame";
    private static final String PATH_PROPERTY = "epysia.renderdoc.path";

    private final int targetFrame;
    private final Optional<RenderDocCapture> capture;

    private int frameIndex;
    private boolean triggered;

    private FrameCaptureSchedule(int targetFrame, Optional<RenderDocCapture> capture) {
        this.targetFrame = targetFrame;
        this.capture = capture;
    }

    public static Optional<FrameCaptureSchedule> fromSystemProperties(Logger logger) {
        Integer frame = Integer.getInteger(FRAME_PROPERTY);
        if (frame == null || frame < 0) {
            return Optional.empty();
        }
        Optional<RenderDocCapture> attached = RenderDocCapture.attach(logger);
        attached.ifPresent(capture -> capture.useFilePathTemplate(
                System.getProperty(PATH_PROPERTY, "/tmp/epysia-frame")));
        return Optional.of(new FrameCaptureSchedule(frame, attached));
    }

    public void onFrameRendered() {
        frameIndex++;
        if (triggered || frameIndex < targetFrame) {
            return;
        }
        triggered = true;
        capture.ifPresent(RenderDocCapture::triggerCapture);
    }
}
