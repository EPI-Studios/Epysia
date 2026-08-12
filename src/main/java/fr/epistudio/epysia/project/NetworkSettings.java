package fr.epistudio.epysia.project;

public record NetworkSettings(int port, int maximumPeers, int networkTickRate, int snapshotRate,
                              int interpolationDelayTicks, float timeoutSeconds, String joinSecret) {

    public static final int MINIMUM_PORT = 1;
    public static final int MAXIMUM_PORT = 65_535;
    public static final int MINIMUM_TICK_RATE = 10;
    public static final int MAXIMUM_TICK_RATE = 240;
    public static final int MINIMUM_SNAPSHOT_RATE = 5;
    public static final int MINIMUM_PEERS = 1;
    public static final int MAXIMUM_PEERS = 256;
    public static final int MAXIMUM_INTERPOLATION_DELAY = 10;
    public static final float MINIMUM_TIMEOUT_SECONDS = 0.5f;
    public static final float MAXIMUM_TIMEOUT_SECONDS = 60.0f;

    private static final int DEFAULT_PORT = 7777;
    private static final int DEFAULT_PEERS = 16;
    private static final int DEFAULT_TICK_RATE = 60;
    private static final int DEFAULT_SNAPSHOT_RATE = 30;
    private static final int DEFAULT_INTERPOLATION_DELAY = 2;
    private static final float DEFAULT_TIMEOUT_SECONDS = 5.0f;

    public static NetworkSettings defaults() {
        return new NetworkSettings(DEFAULT_PORT, DEFAULT_PEERS, DEFAULT_TICK_RATE, DEFAULT_SNAPSHOT_RATE,
                DEFAULT_INTERPOLATION_DELAY, DEFAULT_TIMEOUT_SECONDS, "");
    }

    public NetworkSettings clamped() {
        int tickRate = Math.clamp(networkTickRate, MINIMUM_TICK_RATE, MAXIMUM_TICK_RATE);
        return new NetworkSettings(
                Math.clamp(port, MINIMUM_PORT, MAXIMUM_PORT),
                Math.clamp(maximumPeers, MINIMUM_PEERS, MAXIMUM_PEERS),
                tickRate,
                Math.clamp(snapshotRate, MINIMUM_SNAPSHOT_RATE, tickRate),
                Math.clamp(interpolationDelayTicks, 0, MAXIMUM_INTERPOLATION_DELAY),
                Math.clamp(timeoutSeconds, MINIMUM_TIMEOUT_SECONDS, MAXIMUM_TIMEOUT_SECONDS),
                joinSecret == null ? "" : joinSecret);
    }

    public boolean joinSecretConfigured() {
        return !joinSecret.isBlank();
    }
}
