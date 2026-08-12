package fr.epistudio.epysia.project;

public record SteamSettings(int appId, boolean required, boolean relayAllowed) {

    public static final int DISABLED_APP_ID = 0;
    public static final int MAXIMUM_APP_ID = 2_000_000_000;

    public static SteamSettings defaults() {
        return new SteamSettings(DISABLED_APP_ID, false, true);
    }

    public SteamSettings clamped() {
        return new SteamSettings(Math.clamp(appId, DISABLED_APP_ID, MAXIMUM_APP_ID),
                required, relayAllowed);
    }

    public boolean enabled() {
        return appId > DISABLED_APP_ID;
    }
}
