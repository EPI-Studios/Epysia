package fr.epistudio.epysia.editor.preview;

import java.util.Optional;

public final class NodePreviewEntry {

    private final int slot;
    private final PreviewRenderTarget target;
    private String upstreamKey = "";
    private String errorMessage = "";
    private boolean rendered;
    private long lastFrameNanos;

    NodePreviewEntry(int slot, PreviewRenderTarget target) {
        this.slot = slot;
        this.target = target;
    }

    public int slot() {
        return slot;
    }

    public PreviewRenderTarget target() {
        return target;
    }

    public boolean matches(String candidateUpstreamKey) {
        return rendered && upstreamKey.equals(candidateUpstreamKey);
    }

    public void markRendered(String candidateUpstreamKey) {
        this.upstreamKey = candidateUpstreamKey;
        this.errorMessage = "";
        this.rendered = true;
    }

    public void markFailed(String candidateUpstreamKey, String message) {
        this.upstreamKey = candidateUpstreamKey;
        this.errorMessage = message;
        this.rendered = true;
    }

    public boolean animationDue(long nowNanos, long intervalNanos) {
        return rendered && nowNanos - lastFrameNanos >= intervalNanos;
    }

    public void stampFrame(long nowNanos) {
        lastFrameNanos = nowNanos;
    }

    public boolean hasError() {
        return !errorMessage.isEmpty();
    }

    public Optional<String> errorMessage() {
        return errorMessage.isEmpty() ? Optional.empty() : Optional.of(errorMessage);
    }
}
