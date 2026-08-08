package fr.epistudio.epysia.net.session;

import fr.epistudio.epysia.net.voice.VoiceConfig;

public final class NetworkConfig {
    public static final int DEFAULT_PORT = 7777;

    private final VoiceConfig voice = new VoiceConfig();
    private int port = DEFAULT_PORT;
    private int maximumPeers = 16;
    private int transmissionUnit = 1200;
    private int snapshotByteCeiling = 8192;
    private float timeoutSeconds = 5.0f;
    private int networkTickRate = 60;
    private int snapshotRate = 30;
    private float interestRadiusMeters;
    private int interpolationDelayTicks = 2;
    private int redundantInputSamples = 3;
    private TransportKind transport = TransportKind.UDP;
    private float simulatedLatencySeconds;
    private float simulatedJitterSeconds;
    private float simulatedLossProbability;
    private long latencySeed = 1L;
    private String displayName = "player";
    private String joinSecret = "";
    private int maximumPacketsPerSecond = 400;
    private int maximumBytesPerSecond = 512_000;
    private float handshakeTimeoutSeconds = 5.0f;
    private float reconnectGraceSeconds = 30.0f;

    public VoiceConfig voice() {
        return voice;
    }

    public int port() {
        return port;
    }

    public NetworkConfig setPort(int value) {
        this.port = Math.clamp(value, 1, 65_535);
        return this;
    }

    public int maximumPeers() {
        return maximumPeers;
    }

    public NetworkConfig setMaximumPeers(int value) {
        this.maximumPeers = Math.max(1, value);
        return this;
    }

    public int transmissionUnit() {
        return transmissionUnit;
    }

    public NetworkConfig setTransmissionUnit(int value) {
        this.transmissionUnit = Math.clamp(value, 576, 9_000);
        return this;
    }

    public int snapshotByteCeiling() {
        return snapshotByteCeiling;
    }

    public NetworkConfig setSnapshotByteCeiling(int value) {
        this.snapshotByteCeiling = Math.max(transmissionUnit, value);
        return this;
    }

    public float timeoutSeconds() {
        return timeoutSeconds;
    }

    public NetworkConfig setTimeoutSeconds(float value) {
        this.timeoutSeconds = Math.max(0.5f, value);
        return this;
    }

    public int networkTickRate() {
        return networkTickRate;
    }

    public NetworkConfig setNetworkTickRate(int value) {
        this.networkTickRate = Math.clamp(value, 10, 240);
        return this;
    }

    public int snapshotRate() {
        return snapshotRate;
    }

    public NetworkConfig setSnapshotRate(int value) {
        this.snapshotRate = Math.clamp(value, 5, networkTickRate);
        return this;
    }

    public int snapshotIntervalTicks() {
        return Math.max(1, networkTickRate / Math.max(1, snapshotRate));
    }

    public float interestRadiusMeters() {
        return interestRadiusMeters;
    }

    public NetworkConfig setInterestRadiusMeters(float value) {
        this.interestRadiusMeters = Math.max(0.0f, value);
        return this;
    }

    public int interpolationDelayTicks() {
        return interpolationDelayTicks;
    }

    public NetworkConfig setInterpolationDelayTicks(int value) {
        this.interpolationDelayTicks = Math.clamp(value, 0, 10);
        return this;
    }

    public int redundantInputSamples() {
        return redundantInputSamples;
    }

    public NetworkConfig setRedundantInputSamples(int value) {
        this.redundantInputSamples = Math.clamp(value, 1, 16);
        return this;
    }

    public TransportKind transport() {
        return transport;
    }

    public NetworkConfig setTransport(TransportKind value) {
        this.transport = value == null ? TransportKind.UDP : value;
        return this;
    }

    public float simulatedLatencySeconds() {
        return simulatedLatencySeconds;
    }

    public float simulatedJitterSeconds() {
        return simulatedJitterSeconds;
    }

    public float simulatedLossProbability() {
        return simulatedLossProbability;
    }

    public NetworkConfig simulateNetwork(float latencySeconds, float jitterSeconds, float lossProbability) {
        this.simulatedLatencySeconds = Math.max(0.0f, latencySeconds);
        this.simulatedJitterSeconds = Math.max(0.0f, jitterSeconds);
        this.simulatedLossProbability = Math.clamp(lossProbability, 0.0f, 1.0f);
        return this;
    }

    public boolean simulationEnabled() {
        return simulatedLatencySeconds > 0.0f || simulatedJitterSeconds > 0.0f || simulatedLossProbability > 0.0f;
    }

    public long latencySeed() {
        return latencySeed;
    }

    public NetworkConfig setLatencySeed(long value) {
        this.latencySeed = value;
        return this;
    }

    public String joinSecret() {
        return joinSecret;
    }

    public NetworkConfig setJoinSecret(String value) {
        this.joinSecret = value == null ? "" : value;
        return this;
    }

    public int maximumPacketsPerSecond() {
        return maximumPacketsPerSecond;
    }

    public NetworkConfig setMaximumPacketsPerSecond(int value) {
        this.maximumPacketsPerSecond = Math.max(10, value);
        return this;
    }

    public int maximumBytesPerSecond() {
        return maximumBytesPerSecond;
    }

    public NetworkConfig setMaximumBytesPerSecond(int value) {
        this.maximumBytesPerSecond = Math.max(4_096, value);
        return this;
    }

    public float handshakeTimeoutSeconds() {
        return handshakeTimeoutSeconds;
    }

    public NetworkConfig setHandshakeTimeoutSeconds(float value) {
        this.handshakeTimeoutSeconds = Math.max(0.5f, value);
        return this;
    }

    public float reconnectGraceSeconds() {
        return reconnectGraceSeconds;
    }

    public NetworkConfig setReconnectGraceSeconds(float value) {
        this.reconnectGraceSeconds = Math.max(0.0f, value);
        return this;
    }

    public String displayName() {
        return displayName;
    }

    public NetworkConfig setDisplayName(String value) {
        this.displayName = value == null || value.isBlank() ? "player" : value;
        return this;
    }
}
