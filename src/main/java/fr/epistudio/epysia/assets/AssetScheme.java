package fr.epistudio.epysia.assets;

public enum AssetScheme {

    NONE(""),
    PROJECT("res://"),
    ENGINE("engine://"),
    SYSTEM("file://");

    private final String prefix;

    AssetScheme(String prefix) {
        this.prefix = prefix;
    }

    public String prefix() {
        return prefix;
    }
}
