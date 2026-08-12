package fr.epistudio.epysia.steam;

import fr.epistudio.epysia.project.SteamSettings;

import java.util.Optional;

public final class SteamStartup {

    public static final String APP_ID_PROPERTY = "epysia.steam.appId";
    public static final String APP_ID_VARIABLE = "EPYSIA_STEAM_APP_ID";
    public static final String REQUIRED_PROPERTY = "epysia.steam.required";

    private SteamStartup() {
    }

    public static Optional<SteamConfig> requested() {
        return declaredAppId().map(appId -> new SteamConfig()
                .setAppId(appId)
                .setRequired(Boolean.getBoolean(REQUIRED_PROPERTY)));
    }

    public static Optional<SteamConfig> requested(SteamSettings settings) {
        int appId = declaredAppId().orElse(settings.appId());
        if (appId <= SteamSettings.DISABLED_APP_ID) {
            return Optional.empty();
        }
        return Optional.of(new SteamConfig()
                .setAppId(appId)
                .setRequired(settings.required() || Boolean.getBoolean(REQUIRED_PROPERTY))
                .setRelayAllowed(settings.relayAllowed()));
    }

    private static Optional<Integer> declaredAppId() {
        return firstPresent(System.getProperty(APP_ID_PROPERTY), System.getenv(APP_ID_VARIABLE))
                .flatMap(SteamStartup::parse);
    }

    private static Optional<String> firstPresent(String property, String variable) {
        if (property != null && !property.isBlank()) {
            return Optional.of(property);
        }
        if (variable != null && !variable.isBlank()) {
            return Optional.of(variable);
        }
        return Optional.empty();
    }

    private static Optional<Integer> parse(String text) {
        try {
            return Optional.of(Integer.parseInt(text.trim()));
        } catch (NumberFormatException notANumber) {
            return Optional.empty();
        }
    }
}
