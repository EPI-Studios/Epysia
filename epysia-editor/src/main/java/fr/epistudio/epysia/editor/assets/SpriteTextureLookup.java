package fr.epistudio.epysia.editor.assets;

import fr.epistudio.epysia.assets.AssetLocator;
import fr.epistudio.epysia.assets.LegacyAssetReferences;
import fr.epistudio.epysia.assets.epyatlas.SpriteAtlas;
import fr.epistudio.epysia.components.SpriteFlipbook;
import fr.epistudio.epysia.components.SpriteRenderer;
import fr.epistudio.epysia.gameobjects.GameObject;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

public final class SpriteTextureLookup {

    private final AssetLocator locator;

    public SpriteTextureLookup(AssetLocator locator) {
        this.locator = locator;
    }

    public Optional<Path> textureFileOf(GameObject gameObject) {
        Optional<Path> fromAtlas = atlasTexturePath(gameObject).flatMap(this::existingFile);
        if (fromAtlas.isPresent()) {
            return fromAtlas;
        }
        SpriteRenderer sprite = gameObject.getComponentOrNull(SpriteRenderer.class);
        return sprite == null ? Optional.empty() : existingFile(sprite.textureRef().path());
    }

    public static Optional<SpriteAtlas> atlasOf(GameObject gameObject) {
        SpriteRenderer sprite = gameObject.getComponentOrNull(SpriteRenderer.class);
        if (sprite != null && sprite.atlasRef().direct().isPresent()) {
            return sprite.atlasRef().direct();
        }
        SpriteFlipbook flipbook = gameObject.getComponentOrNull(SpriteFlipbook.class);
        if (flipbook != null && flipbook.atlasRef().direct().isPresent()) {
            return flipbook.atlasRef().direct();
        }
        return Optional.empty();
    }

    private static Optional<String> atlasTexturePath(GameObject gameObject) {
        return atlasOf(gameObject).map(SpriteAtlas::texturePath);
    }

    public Optional<Path> existingFile(String storedPath) {
        if (storedPath == null || storedPath.isEmpty()) {
            return Optional.empty();
        }
        return locator.file(LegacyAssetReferences.interpretWithoutMigration(storedPath, locator))
                .filter(Files::isRegularFile);
    }
}
