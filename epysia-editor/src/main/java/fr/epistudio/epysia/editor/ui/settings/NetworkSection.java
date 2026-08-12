package fr.epistudio.epysia.editor.ui.settings;

import fr.epistudio.epysia.editor.ui.kit.Notices;
import fr.epistudio.epysia.i18n.I18n;
import fr.epistudio.epysia.i18n.TextKey;
import fr.epistudio.epysia.project.NetworkSettings;
import imgui.ImGui;
import imgui.type.ImInt;
import imgui.type.ImString;

public final class NetworkSection {

    private static final int JOIN_SECRET_CAPACITY = 128;

    private final SettingsChrome chrome;
    private final ImInt port = new ImInt();
    private final ImInt maximumPeers = new ImInt();
    private final ImInt tickRate = new ImInt();
    private final ImInt snapshotRate = new ImInt();
    private final ImInt interpolationDelay = new ImInt();
    private final float[] timeout = new float[1];
    private final ImString joinSecret = new ImString(JOIN_SECRET_CAPACITY);

    public NetworkSection(SettingsChrome chrome) {
        this.chrome = chrome;
    }

    public void load(NetworkSettings settings) {
        port.set(settings.port());
        maximumPeers.set(settings.maximumPeers());
        tickRate.set(settings.networkTickRate());
        snapshotRate.set(settings.snapshotRate());
        interpolationDelay.set(settings.interpolationDelayTicks());
        timeout[0] = settings.timeoutSeconds();
        joinSecret.set(settings.joinSecret());
    }

    public NetworkSettings build() {
        return new NetworkSettings(port.get(), maximumPeers.get(), tickRate.get(),
                snapshotRate.get(), interpolationDelay.get(), timeout[0], joinSecret.get()).clamped();
    }

    public void render() {
        chrome.row(I18n.translate(TextKey.EDITOR_SETTINGS_NETWORK_PORT),
                () -> ImGui.inputInt("##value", port));
        chrome.row(I18n.translate(TextKey.EDITOR_SETTINGS_NETWORK_PEERS),
                () -> ImGui.inputInt("##value", maximumPeers));
        chrome.row(I18n.translate(TextKey.EDITOR_SETTINGS_NETWORK_TICK_RATE),
                () -> ImGui.dragInt("##value", tickRate.getData(), 1.0f,
                        NetworkSettings.MINIMUM_TICK_RATE, NetworkSettings.MAXIMUM_TICK_RATE));
        chrome.row(I18n.translate(TextKey.EDITOR_SETTINGS_NETWORK_SNAPSHOT_RATE),
                () -> ImGui.dragInt("##value", snapshotRate.getData(), 1.0f,
                        NetworkSettings.MINIMUM_SNAPSHOT_RATE,
                        Math.max(NetworkSettings.MINIMUM_SNAPSHOT_RATE, tickRate.get())));
        chrome.row(I18n.translate(TextKey.EDITOR_SETTINGS_NETWORK_INTERPOLATION),
                () -> ImGui.dragInt("##value", interpolationDelay.getData(), 1.0f,
                        0, NetworkSettings.MAXIMUM_INTERPOLATION_DELAY));
        chrome.row(I18n.translate(TextKey.EDITOR_SETTINGS_NETWORK_TIMEOUT),
                () -> ImGui.dragFloat("##value", timeout, 0.1f,
                        NetworkSettings.MINIMUM_TIMEOUT_SECONDS,
                        NetworkSettings.MAXIMUM_TIMEOUT_SECONDS, "%.1f"));
        chrome.row(I18n.translate(TextKey.EDITOR_SETTINGS_NETWORK_JOIN_SECRET),
                () -> ImGui.inputText("##value", joinSecret));
        renderJoinSecretNotice();
    }

    private void renderJoinSecretNotice() {
        if (chrome.filtering()) {
            return;
        }
        if (joinSecret.get().isBlank()) {
            Notices.warning(I18n.translate(TextKey.EDITOR_SETTINGS_NETWORK_JOIN_SECRET_EMPTY));
            return;
        }
        Notices.info(I18n.translate(TextKey.EDITOR_SETTINGS_NETWORK_JOIN_SECRET_SET));
    }
}
