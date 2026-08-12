package fr.epistudio.epysia.editor.ui.settings;

import fr.epistudio.epysia.editor.ui.kit.Texts;
import fr.epistudio.epysia.project.SteamSettings;
import fr.epistudio.epysia.steam.SteamPresence;
import imgui.ImGui;
import imgui.type.ImInt;

public final class SteamSection {

    private final SettingsChrome chrome;
    private final ImInt appId = new ImInt();

    private boolean required;
    private boolean relayAllowed = true;

    public SteamSection(SettingsChrome chrome) {
        this.chrome = chrome;
    }

    public void load(SteamSettings settings) {
        appId.set(settings.appId());
        required = settings.required();
        relayAllowed = settings.relayAllowed();
    }

    public SteamSettings build() {
        return new SteamSettings(appId.get(), required, relayAllowed).clamped();
    }

    public void render() {
        chrome.row("App ID", () -> ImGui.inputInt("##value", appId));
        required = chrome.toggleRow("Steam required", required);
        relayAllowed = chrome.toggleRow("Allow relay", relayAllowed);
        if (chrome.filtering()) {
            return;
        }
        renderStatus();
    }

    private void renderStatus() {
        if (appId.get() <= SteamSettings.DISABLED_APP_ID) {
            Texts.muted("App ID 0 disables Steam. The exported game ships without a Steam dependency.");
            return;
        }
        if (!SteamPresence.librariesAvailable()) {
            Texts.muted("Steam native libraries are not available in this build.");
            return;
        }
        if (SteamPresence.clientRunning()) {
            Texts.muted("Steam client detected.");
            return;
        }
        Texts.muted("No Steam client running. The game still exports; Steam stays offline.");
    }
}
