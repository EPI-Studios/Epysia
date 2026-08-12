package fr.epistudio.epysia.navigation;

import org.joml.Vector3f;
import org.recast4j.detour.DefaultQueryFilter;
import org.recast4j.detour.FindNearestPolyResult;
import org.recast4j.detour.MeshData;
import org.recast4j.detour.NavMesh;
import org.recast4j.detour.NavMeshParams;
import org.recast4j.detour.MeshTile;
import org.recast4j.detour.NavMeshQuery;
import org.recast4j.detour.Poly;
import org.recast4j.detour.QueryFilter;
import org.recast4j.detour.Result;
import org.recast4j.detour.StraightPathItem;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public final class NavigationMesh {

    private static final int MAXIMUM_STRAIGHT_PATH = 256;
    private static final int VERTICES_PER_POLYGON = 6;
    private static final int MAXIMUM_TILES = 4096;
    private static final int MAXIMUM_POLYGONS_PER_TILE = 16384;
    private static final float[] DEFAULT_EXTENTS = {2.0f, 4.0f, 2.0f};

    private final NavMesh mesh;
    private final NavMeshQuery query;
    private final QueryFilter filter = new DefaultQueryFilter();

    NavigationMesh(NavMeshSettings settings, float originX, float originZ) {
        NavMeshParams params = new NavMeshParams();
        params.orig[0] = originX;
        params.orig[2] = originZ;
        params.tileWidth = settings.tileWorldSize();
        params.tileHeight = settings.tileWorldSize();
        params.maxTiles = MAXIMUM_TILES;
        params.maxPolys = MAXIMUM_POLYGONS_PER_TILE;
        this.mesh = new NavMesh(params, VERTICES_PER_POLYGON);
        this.query = new NavMeshQuery(mesh);
    }

    public void addTile(MeshData data) {
        mesh.addTile(data, 0, 0L);
    }

    public void removeTile(int tileX, int tileZ) {
        long reference = mesh.getTileRefAt(tileX, tileZ, 0);
        if (reference != 0L) {
            mesh.removeTile(reference);
        }
    }

    public boolean hasTile(int tileX, int tileZ) {
        return mesh.getTileRefAt(tileX, tileZ, 0) != 0L;
    }

    public float[] debugTriangleVertices() {
        List<Float> vertices = new ArrayList<>();
        for (int index = 0; index < mesh.getMaxTiles(); index++) {
            appendTile(mesh.getTile(index), vertices);
        }
        float[] packed = new float[vertices.size()];
        for (int index = 0; index < packed.length; index++) {
            packed[index] = vertices.get(index);
        }
        return packed;
    }

    private static void appendTile(MeshTile tile, List<Float> vertices) {
        if (tile == null || tile.data == null || tile.data.polys == null) {
            return;
        }
        MeshData data = tile.data;
        for (Poly polygon : data.polys) {
            if (polygon == null || polygon.getType() == Poly.DT_POLYTYPE_OFFMESH_CONNECTION) {
                continue;
            }
            appendPolygonFan(polygon, data.verts, vertices);
        }
    }

    private static void appendPolygonFan(Poly polygon, float[] source, List<Float> vertices) {
        for (int corner = 2; corner < polygon.vertCount; corner++) {
            appendVertex(source, polygon.verts[0], vertices);
            appendVertex(source, polygon.verts[corner - 1], vertices);
            appendVertex(source, polygon.verts[corner], vertices);
        }
    }

    private static void appendVertex(float[] source, int vertexIndex, List<Float> vertices) {
        int base = vertexIndex * 3;
        vertices.add(source[base]);
        vertices.add(source[base + 1]);
        vertices.add(source[base + 2]);
    }

    public Optional<Vector3f> nearestPoint(Vector3f position) {
        return nearestPolygon(position).map(found -> toVector(found.getNearestPos()));
    }

    public List<Vector3f> findPath(Vector3f from, Vector3f to) {
        Optional<FindNearestPolyResult> start = nearestPolygon(from);
        Optional<FindNearestPolyResult> end = nearestPolygon(to);
        if (start.isEmpty() || end.isEmpty()) {
            return List.of();
        }
        Result<List<Long>> polygons = query.findPath(start.get().getNearestRef(), end.get().getNearestRef(),
                start.get().getNearestPos(), end.get().getNearestPos(), filter);
        if (polygons.failed() || polygons.result == null || polygons.result.isEmpty()) {
            return List.of();
        }
        return straighten(start.get().getNearestPos(), end.get().getNearestPos(), polygons.result);
    }

    private List<Vector3f> straighten(float[] from, float[] to, List<Long> polygons) {
        Result<List<StraightPathItem>> straight =
                query.findStraightPath(from, to, polygons, MAXIMUM_STRAIGHT_PATH, 0);
        if (straight.failed() || straight.result == null) {
            return List.of();
        }
        List<Vector3f> corners = new ArrayList<>(straight.result.size());
        for (StraightPathItem item : straight.result) {
            corners.add(toVector(item.getPos()));
        }
        return corners;
    }

    private Optional<FindNearestPolyResult> nearestPolygon(Vector3f position) {
        Result<FindNearestPolyResult> found = query.findNearestPoly(
                new float[]{position.x, position.y, position.z}, DEFAULT_EXTENTS, filter);
        if (found.failed() || found.result == null || found.result.getNearestRef() == 0L) {
            return Optional.empty();
        }
        return Optional.of(found.result);
    }

    private static Vector3f toVector(float[] values) {
        return new Vector3f(values[0], values[1], values[2]);
    }

    public NavMesh detourMesh() {
        return mesh;
    }
}
