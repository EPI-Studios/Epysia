package fr.epistudio.epysia.editor.play;

public final class NetworkPlaySettings {

    public enum EditorRole {
        SINGLE_PLAYER,
        LISTEN_SERVER,
        CLIENT,
        DEDICATED_SERVER
    }

    private EditorRole editorRole = EditorRole.SINGLE_PLAYER;
    private int extraClients;
    private int port = 7777;

    public EditorRole editorRole() {
        return editorRole;
    }

    public NetworkPlaySettings setEditorRole(EditorRole value) {
        this.editorRole = value == null ? EditorRole.SINGLE_PLAYER : value;
        return this;
    }

    public int extraClients() {
        return extraClients;
    }

    public NetworkPlaySettings setExtraClients(int value) {
        this.extraClients = Math.clamp(value, 0, 7);
        return this;
    }

    public int port() {
        return port;
    }

    public NetworkPlaySettings setPort(int value) {
        this.port = Math.clamp(value, 1, 65_535);
        return this;
    }

    public boolean networked() {
        return editorRole != EditorRole.SINGLE_PLAYER;
    }

    public boolean needsDedicatedServerProcess() {
        return editorRole == EditorRole.DEDICATED_SERVER;
    }

    public int totalClientProcesses() {
        int connectingEditor = editorRole == EditorRole.CLIENT ? 1 : 0;
        return extraClients + (needsDedicatedServerProcess() ? connectingEditor : 0);
    }
}
