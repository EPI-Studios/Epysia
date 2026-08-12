package fr.epistudio.epysia.steam;

public final class SteamConfig {

    public static final int SPACEWAR_APP_ID = 480;

    private int appId = SPACEWAR_APP_ID;
    private boolean writeAppIdFile = true;
    private boolean relayAllowed = true;
    private boolean required;

    public int appId() {
        return appId;
    }

    public SteamConfig setAppId(int value) {
        appId = Math.max(0, value);
        return this;
    }

    public boolean writeAppIdFile() {
        return writeAppIdFile;
    }

    public SteamConfig setWriteAppIdFile(boolean value) {
        writeAppIdFile = value;
        return this;
    }

    public boolean relayAllowed() {
        return relayAllowed;
    }

    public SteamConfig setRelayAllowed(boolean value) {
        relayAllowed = value;
        return this;
    }

    public boolean required() {
        return required;
    }

    public SteamConfig setRequired(boolean value) {
        required = value;
        return this;
    }
}
