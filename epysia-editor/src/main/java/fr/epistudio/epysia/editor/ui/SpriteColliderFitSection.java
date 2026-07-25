package fr.epistudio.epysia.editor.ui;

import fr.epistudio.epysia.assets.epyatlas.SpriteAtlas;
import fr.epistudio.epysia.assets.epyatlas.SpriteAtlasGrid;
import fr.epistudio.epysia.assets.loaders.TexturePathPrefixes;
import fr.epistudio.epysia.components.IComponent;
import fr.epistudio.epysia.components.SpriteRenderer;
import fr.epistudio.epysia.editor.assets.SpriteOpaqueBounds;
import fr.epistudio.epysia.gameobjects.GameObject;
import fr.epistudio.epysia.physics.components.BoxCollider2D;
import fr.epistudio.epysia.physics.components.CharacterController2D;
import fr.epistudio.epysia.physics.components.CircleCollider2D;
import imgui.ImGui;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

public final class SpriteColliderFitSection {

    private final SpriteOpaqueBounds opaqueBounds = new SpriteOpaqueBounds();

    public boolean render(GameObject gameObject, IComponent component) {
        if (!fittable(component)) {
            return false;
        }
        Optional<SpriteOpaqueBounds.UnitBounds> bounds = spriteBounds(gameObject);
        ImGui.beginDisabled(bounds.isEmpty());
        boolean clicked = ImGui.button("Fit to sprite");
        ImGui.endDisabled();
        renderTooltip(bounds);
        return clicked && bounds.isPresent() && apply(gameObject, component, bounds.get());
    }

    private static boolean fittable(IComponent component) {
        return component instanceof BoxCollider2D
                || component instanceof CircleCollider2D
                || component instanceof CharacterController2D;
    }

    private static void renderTooltip(Optional<SpriteOpaqueBounds.UnitBounds> bounds) {
        if (!ImGui.isItemHovered()) {
            return;
        }
        ImGui.setTooltip(bounds.isEmpty()
                ? "Needs a Sprite Renderer with an atlas whose texture is on disk."
                : "Resize and offset this shape to wrap the visible pixels of the current sprite frame.");
    }

    private boolean apply(GameObject gameObject, IComponent component, SpriteOpaqueBounds.UnitBounds bounds) {
        float unitsPerCell = cellSizeInUnits(gameObject);
        float halfWidth = (bounds.maxX() - bounds.minX()) * 0.5f * unitsPerCell;
        float halfHeight = (bounds.maxY() - bounds.minY()) * 0.5f * unitsPerCell;
        float centerX = ((bounds.minX() + bounds.maxX()) * 0.5f - 0.5f) * unitsPerCell;
        float centerY = ((bounds.minY() + bounds.maxY()) * 0.5f - 0.5f) * unitsPerCell;
        return applyToComponent(component, halfWidth, halfHeight, centerX, centerY);
    }

    private static boolean applyToComponent(IComponent component, float halfWidth, float halfHeight,
                                            float centerX, float centerY) {
        switch (component) {
            case BoxCollider2D box -> box.setHalfExtents(halfWidth, halfHeight).setOffset(centerX, centerY);
            case CircleCollider2D circle ->
                    circle.setRadius(Math.max(halfWidth, halfHeight)).setOffset(centerX, centerY);
            case CharacterController2D controller -> fitController(controller, halfWidth, halfHeight,
                    centerX, centerY);
            default -> {
                return false;
            }
        }
        return true;
    }

    private static void fitController(CharacterController2D controller, float halfWidth, float halfHeight,
                                      float centerX, float centerY) {
        float radius = Math.min(halfWidth, halfHeight);
        controller.setCapsule(radius, Math.max(0.01f, (halfHeight - radius) * 2.0f));
        controller.setCapsuleOffset(centerX, centerY);
    }

    private static float cellSizeInUnits(GameObject gameObject) {
        SpriteRenderer sprite = gameObject.getComponentOrNull(SpriteRenderer.class);
        if (sprite == null) {
            return 1.0f;
        }
        return cellPixels(sprite) / Math.max(1.0f, sprite.pixelsPerUnit());
    }

    private static float cellPixels(SpriteRenderer sprite) {
        return sprite.atlasRef().direct().flatMap(SpriteAtlas::grid)
                .map(SpriteAtlasGrid::cellHeight).map(Integer::floatValue).orElse(1.0f);
    }

    private Optional<SpriteOpaqueBounds.UnitBounds> spriteBounds(GameObject gameObject) {
        SpriteRenderer sprite = gameObject.getComponentOrNull(SpriteRenderer.class);
        if (sprite == null) {
            return Optional.empty();
        }
        Optional<SpriteAtlas> atlas = sprite.atlasRef().direct();
        Optional<SpriteAtlasGrid> grid = atlas.flatMap(SpriteAtlas::grid);
        Optional<Path> texture = atlas.flatMap(found -> textureFile(sprite, found));
        if (grid.isEmpty() || texture.isEmpty()) {
            return Optional.empty();
        }
        return opaqueBounds.boundsOf(texture.get(), grid.get().columns(), grid.get().rows(),
                currentCellIndex(sprite));
    }

    private static int currentCellIndex(SpriteRenderer sprite) {
        try {
            return Integer.parseInt(sprite.regionName());
        } catch (NumberFormatException notAGridRegion) {
            return 0;
        }
    }

    private static Optional<Path> textureFile(SpriteRenderer sprite, SpriteAtlas atlas) {
        if (atlas.texturePath().isEmpty()) {
            return Optional.empty();
        }
        String stripped = TexturePathPrefixes.stripPrefixes(atlas.texturePath());
        Path texture = Path.of(stripped);
        if (!texture.isAbsolute()) {
            texture = atlasParent(sprite).resolve(stripped).normalize();
        }
        return Files.isRegularFile(texture) ? Optional.of(texture) : Optional.empty();
    }

    private static Path atlasParent(SpriteRenderer sprite) {
        Path atlasFile = Path.of(TexturePathPrefixes.stripPrefixes(sprite.atlasRef().path())).toAbsolutePath();
        Path parent = atlasFile.getParent();
        return parent == null ? Path.of("") : parent;
    }
}
