package fr.epistudio.epysia.physics.components;

import fr.epistudio.epysia.EngineServices;
import fr.epistudio.epysia.assets.AssetRef;
import fr.epistudio.epysia.assets.epytilemap.CellBounds;
import fr.epistudio.epysia.assets.epytilemap.SpriteTilemap;
import fr.epistudio.epysia.assets.epytilemap.TileCollisionShape;
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
        CellBounds bounds = map.usedBounds();
        return new Vector2f(Math.max(1, bounds.widthCells()) * map.cellWidth() * 0.5f,
                Math.max(1, bounds.heightCells()) * map.cellHeight() * 0.5f);
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
        appendPolygonPlacements(map, placements);
        return placements;
    }

    private void appendPolygonPlacements(SpriteTilemap map, List<ShapePlacement> placements) {
        CellBounds bounds = map.collisionBounds();
        for (int cellY = bounds.minY(); cellY <= bounds.maxY(); cellY++) {
            for (int cellX = bounds.minX(); cellX <= bounds.maxX(); cellX++) {
                for (TileCollisionShape shape : map.cellCollisionShapes(cellX, cellY)) {
                    placements.add(polygonPlacement(map, shape, cellX, cellY));
                }
            }
        }
    }

    private ShapePlacement polygonPlacement(SpriteTilemap map, TileCollisionShape shape, int cellX, int cellY) {
        Vector2f origin = offset().add(cellX * map.cellWidth(), cellY * map.cellHeight(), new Vector2f());
        return new ShapePlacement(new ShapeDescriptor.ConvexHull(extrude(shape, map)), origin);
    }

    private static float[] extrude(TileCollisionShape shape, SpriteTilemap map) {
        List<Vector2f> points = shape.points();
        float[] vertices = new float[points.size() * 6];
        for (int index = 0; index < points.size(); index++) {
            Vector2f point = points.get(index);
            float x = point.x * map.cellWidth();
            float y = point.y * map.cellHeight();
            writeVertex(vertices, index * 6, x, y, -PLANE_HALF_DEPTH);
            writeVertex(vertices, index * 6 + 3, x, y, PLANE_HALF_DEPTH);
        }
        return vertices;
    }

    private static void writeVertex(float[] vertices, int position, float x, float y, float z) {
        vertices[position] = x;
        vertices[position + 1] = y;
        vertices[position + 2] = z;
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
