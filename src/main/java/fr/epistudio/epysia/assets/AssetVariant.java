package fr.epistudio.epysia.assets;

import java.util.Map;
import java.util.Optional;
import java.util.SortedMap;
import java.util.TreeMap;

public final class AssetVariant {

    private static final AssetVariant NONE = new AssetVariant(new TreeMap<>());

    private final SortedMap<String, String> overrides;
    private final String fingerprint;

    private AssetVariant(SortedMap<String, String> overrides) {
        this.overrides = overrides;
        this.fingerprint = buildFingerprint(overrides);
    }

    public static AssetVariant none() {
        return NONE;
    }

    public static AssetVariant of(String key, String value) {
        return NONE.with(key, value);
    }

    public AssetVariant with(String key, String value) {
        SortedMap<String, String> merged = new TreeMap<>(overrides);
        merged.put(key, value);
        return new AssetVariant(merged);
    }

    public Optional<String> value(String key) {
        return Optional.ofNullable(overrides.get(key));
    }

    public Map<String, String> overrides() {
        return Map.copyOf(overrides);
    }

    public boolean isEmpty() {
        return overrides.isEmpty();
    }

    public String fingerprint() {
        return fingerprint;
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof AssetVariant variant && overrides.equals(variant.overrides);
    }

    @Override
    public int hashCode() {
        return overrides.hashCode();
    }

    @Override
    public String toString() {
        return fingerprint;
    }

    private static String buildFingerprint(SortedMap<String, String> entries) {
        StringBuilder builder = new StringBuilder();
        for (Map.Entry<String, String> entry : entries.entrySet()) {
            if (!builder.isEmpty()) {
                builder.append(';');
            }
            builder.append(entry.getKey()).append('=').append(entry.getValue());
        }
        return builder.toString();
    }
}
