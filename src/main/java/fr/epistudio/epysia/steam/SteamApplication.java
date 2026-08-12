package fr.epistudio.epysia.steam;

import com.codedisaster.steamworks.SteamApps;

public final class SteamApplication {

    private final SteamApps apps;

    public SteamApplication(SteamApps apps) {
        this.apps = apps;
    }

    public boolean owned() {
        return apps.isSubscribed();
    }

    public boolean ownedFromFreeWeekend() {
        return apps.isSubscribedFromFreeWeekend();
    }

    public boolean vacBanned() {
        return apps.isVACBanned();
    }

    public boolean lowViolence() {
        return apps.isLowViolence();
    }

    public String language() {
        return apps.getCurrentGameLanguage();
    }

    public String availableLanguages() {
        return apps.getAvailableGameLanguages();
    }

    public int buildId() {
        return apps.getAppBuildId();
    }

    public long ownerId() {
        return SteamIds.rawOf(apps.getAppOwner());
    }

    public int downloadableContentCount() {
        return apps.getDLCCount();
    }

    public boolean ownsDownloadableContent(int appId) {
        return apps.isSubscribedApp(appId);
    }

    public boolean downloadableContentInstalled(int appId) {
        return apps.isDlcInstalled(appId);
    }

    public void installDownloadableContent(int appId) {
        apps.installDLC(appId);
    }

    public void uninstallDownloadableContent(int appId) {
        apps.uninstallDLC(appId);
    }

    public int purchaseTimeSeconds(int appId) {
        return apps.getEarliestPurchaseUnixTime(appId);
    }
}
