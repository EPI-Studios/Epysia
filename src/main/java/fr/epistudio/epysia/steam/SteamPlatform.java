package fr.epistudio.epysia.steam;

import com.codedisaster.steamworks.SteamUtils;

public final class SteamPlatform {

    private final SteamUtils utils;

    public SteamPlatform(SteamUtils utils) {
        this.utils = utils;
    }

    public boolean onSteamDeck() {
        return utils.isSteamRunningOnSteamDeck();
    }

    public boolean bigPictureMode() {
        return utils.isSteamInBigPictureMode();
    }

    public boolean chinaLauncher() {
        return utils.isSteamChinaLauncher();
    }

    public boolean overlayEnabled() {
        return utils.isOverlayEnabled();
    }

    public int appId() {
        return utils.getAppID();
    }

    public int serverRealTimeSeconds() {
        return utils.getServerRealTime();
    }

    public int secondsSinceAppActive() {
        return utils.getSecondsSinceAppActive();
    }

    public int secondsSinceComputerActive() {
        return utils.getSecondsSinceComputerActive();
    }
}
