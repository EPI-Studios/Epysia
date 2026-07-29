package fr.epistudio.epysia.assets;

import fr.epistudio.epysia.assets.source.AssetSource;

import java.util.Map;
import java.util.Optional;

public record AssetLoadRequest(AssetUri uri, AssetVariant variant) {

    public static AssetLoadRequest of(AssetUri uri) {
        return new AssetLoadRequest(uri, AssetVariant.none());
    }

    public Optional<AssetSource> source(AssetLocator locator) {
        return locator.open(uri);
    }

    public Map<String, Object> settings(AssetLocator locator) {
        return AssetMetaFile.settingsFor(locator, uri);
    }

    public AssetLoadRequest sibling(String fileName) {
        return new AssetLoadRequest(uri.sibling(fileName), variant);
    }
}
