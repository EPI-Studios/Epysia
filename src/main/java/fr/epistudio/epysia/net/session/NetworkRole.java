package fr.epistudio.epysia.net.session;

public enum NetworkRole {
    OFFLINE,
    SERVER,
    LISTEN_SERVER,
    CLIENT;

    public boolean isServer() {
        return this == SERVER || this == LISTEN_SERVER;
    }

    public boolean isClient() {
        return this == CLIENT || this == LISTEN_SERVER;
    }

    public boolean rendersLocally() {
        return this != SERVER;
    }

    public boolean isActive() {
        return this != OFFLINE;
    }
}
