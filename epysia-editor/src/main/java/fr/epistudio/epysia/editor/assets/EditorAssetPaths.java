package fr.epistudio.epysia.editor.assets;

import fr.epistudio.epysia.assets.AssetLocator;
import fr.epistudio.epysia.assets.LegacyAssetReferences;

public final class EditorAssetPaths {

    private EditorAssetPaths() {
    }

    public static String stored(AssetLocator locator, String droppedPath) {
        return LegacyAssetReferences.interpretWithoutMigration(droppedPath, locator).toString();
    }
}
