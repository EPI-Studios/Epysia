package fr.epistudio.epysia.net.session;

public final class ConnectionBudget {
    private static final float WINDOW_SECONDS = 1.0f;
    private static final int SUSTAINED_BREACHES_BEFORE_DROP = 3;

    private final int maximumPacketsPerSecond;
    private final int maximumBytesPerSecond;
    private float windowSeconds;
    private int packetsThisWindow;
    private int bytesThisWindow;
    private int consecutiveBreaches;
    private long droppedPackets;

    public ConnectionBudget(int maximumPacketsPerSecond, int maximumBytesPerSecond) {
        this.maximumPacketsPerSecond = maximumPacketsPerSecond;
        this.maximumBytesPerSecond = maximumBytesPerSecond;
    }

    public boolean accept(int byteCount) {
        packetsThisWindow++;
        bytesThisWindow += byteCount;
        if (withinBudget()) {
            return true;
        }
        droppedPackets++;
        return false;
    }

    private boolean withinBudget() {
        return packetsThisWindow <= maximumPacketsPerSecond && bytesThisWindow <= maximumBytesPerSecond;
    }

    public void advance(float deltaTimeSeconds) {
        windowSeconds += deltaTimeSeconds;
        if (windowSeconds < WINDOW_SECONDS) {
            return;
        }
        consecutiveBreaches = withinBudget() ? 0 : consecutiveBreaches + 1;
        packetsThisWindow = 0;
        bytesThisWindow = 0;
        windowSeconds = 0.0f;
    }

    public boolean isAbusive() {
        return consecutiveBreaches >= SUSTAINED_BREACHES_BEFORE_DROP;
    }

    public long droppedPackets() {
        return droppedPackets;
    }
}
