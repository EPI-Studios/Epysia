package fr.epistudio.epysia.physics.components;

import fr.epistudio.epysia.EngineServices;
import fr.epistudio.epysia.assets.AssetRef;
import fr.epistudio.epysia.assets.epytilemap.SpriteTilemap;
import fr.epistudio.epysia.assets.epytilemap.TilemapSolidRectangles;
import fr.epistudio.epysia.components.EpysiaComponent;
import fr.epistudio.epysia.components.Export;
import fr.epistudio.epysia.components.RequiresComponent;
import fr.epistudio.epysia.components.transforms.Transform2D;
import fr.epistudio.epysia.physics.api.ShapeDescriptor;
import org.joml.Vector2f;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@EpysiaComponent(name = "Tilemap Collider 2D", category = "Physics")
@RequiresComponent(Transform2D.class)
public final class TilemapCollider2D extends Collider2D {

    @Export(label = "Tilemap")
    private final AssetRef<SpriteTilemap> tilemap = new AssetRef<>(SpriteTilemap.class);

    private transient long builtVersion = Long.MIN_VALUE;

    public AssetRef<SpriteTilemap> tilemapRef() {
        return tilemap;
    }

    public TilemapCollider2D setTilemapPath(String path) {
        tilemap.setPath(path);
        return this;
    }

    public TilemapCollider2D setTilemap(SpriteTilemap value) {
        tilemap.setDirect(value);
        return this;
    }

    public Optional<SpriteTilemap> tilemapValue() {
        return tilemap.direct();
    }

    @Override
    public void onLoad(EngineServices services) {
        tilemap.resolve(services.assets());
    }

    @Override
    public ShapeDescriptor shape() {
        Vector2f halfExtents = tilemap.direct().map(TilemapCollider2D::mapHalfExtents)
                .orElseGet(() -> new Vector2f(PLANE_HALF_DEPTH, PLANE_HALF_DEPTH));
        return new ShapeDescriptor.Box(new Vector3f(halfExtents.x, halfExtents.y, PLANE_HALF_DEPTH));
    }

    private static Vector2f mapHalfExtents(SpriteTilemap map) {
        return new Vector2f(map.width() * map.cellWidth() * 0.5f, map.height() * map.cellHeight() * 0.5f);
    }

    @Override
    public List<ShapePlacement> shapePlacements() {
        Optional<SpriteTilemap> resolved = tilemap.direct();
        if (resolved.isEmpty()) {
            return List.of();
        }
        SpriteTilemap map = resolved.get();
        builtVersion = map.version();
        List<ShapePlacement> placements = new ArrayList<>();
        for (TilemapSolidRectangles.TileRectangle rectangle : TilemapSolidRectangles.merge(map)) {
            placements.add(placementOf(map, rectangle));
        }
        return placements;
    }

    private ShapePlacement placementOf(SpriteTilemap map, TilemapSolidRectangles.TileRectangle rectangle) {
        float halfWidth = rectangle.widthCells() * map.cellWidth() * 0.5f;
        float halfHeight = rectangle.heightCells() * map.cellHeight() * 0.5f;
        Vector2f center = offset().add(
                rectangle.cellX() * map.cellWidth() + halfWidth,
                rectangle.cellY() * map.cellHeight() + halfHeight);
        return new ShapePlacement(new ShapeDescriptor.Box(new Vector3f(halfWidth, halfHeight, PLANE_HALF_DEPTH)), center);
    }

    @Override
    public boolean requiresRebuild() {
        return isRegistered() && tilemap.direct().map(map -> map.version() != builtVersion).orElse(false);
    }
}
