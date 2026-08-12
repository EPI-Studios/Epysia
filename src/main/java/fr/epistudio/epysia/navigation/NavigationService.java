package fr.epistudio.epysia.navigation;

import fr.epistudio.epysia.scene.Scene;
import org.joml.Vector3f;
import org.recast4j.detour.MeshData;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

public final class NavigationService {

    private record TileKey(int x, int z) {
    }

    private static final NavigationService DETACHED = new NavigationService();

    public static NavigationService detached() {
        return DETACHED;
    }

    private static final float[] EMPTY_VERTICES = new float[0];

    private final NavGeometrySource source = new NavGeometrySource();
    private final Set<TileKey> loadedTiles = new HashSet<>();
    private Optional<NavigationMesh> mesh = Optional.empty();
    private NavMeshSettings settings = NavMeshSettings.walkingCharacter();
    private float originX;
    private float originZ;
    private int bakedTriangleCount;

    public boolean baked() {
        return mesh.isPresent() && !loadedTiles.isEmpty();
    }

    public NavMeshSettings settings() {
        return settings;
    }

    public int bakedTriangleCount() {
        return bakedTriangleCount;
    }

    public float[] debugTriangleVertices() {
        return mesh.map(NavigationMesh::debugTriangleVertices).orElse(EMPTY_VERTICES);
    }

    public int loadedTileCount() {
        return loadedTiles.size();
    }

    public void reset(NavMeshSettings requested, float worldOriginX, float worldOriginZ) {
        settings = requested;
        originX = worldOriginX;
        originZ = worldOriginZ;
        loadedTiles.clear();
        bakedTriangleCount = 0;
        mesh = Optional.of(new NavigationMesh(requested, worldOriginX, worldOriginZ));
    }

    public boolean bake(Scene scene, NavMeshSettings requested) {
        reset(requested, 0.0f, 0.0f);
        source.refresh(scene);
        bakedTriangleCount = source.geometry().triangleCount();
        for (TileKey key : tilesCovering(source)) {
            bakeTile(key.x(), key.z());
        }
        return baked();
    }

    public int refreshAround(Scene scene, Vector3f focus, float radius, int tileBudget) {
        if (mesh.isEmpty()) {
            return 0;
        }
        if (!source.matches(scene)) {
            source.refresh(scene);
        }
        dropTilesBeyond(focus, radius);
        return bakeMissingTiles(focus, radius, tileBudget);
    }

    private int bakeMissingTiles(Vector3f focus, float radius, int tileBudget) {
        int baked = 0;
        for (TileKey key : tilesWithin(focus, radius)) {
            if (baked >= tileBudget) {
                break;
            }
            if (loadedTiles.contains(key)) {
                continue;
            }
            loadedTiles.add(key);
            if (bakeTile(key.x(), key.z())) {
                baked++;
            }
        }
        return baked;
    }

    private boolean bakeTile(int tileX, int tileZ) {
        Optional<MeshData> data = NavMeshBaker.bakeTile(source, settings, originX, originZ, tileX, tileZ);
        if (data.isEmpty()) {
            return false;
        }
        mesh.get().removeTile(tileX, tileZ);
        mesh.get().addTile(data.get());
        loadedTiles.add(new TileKey(tileX, tileZ));
        return true;
    }

    private void dropTilesBeyond(Vector3f focus, float radius) {
        float tileSize = settings.tileWorldSize();
        float limit = radius + tileSize;
        List<TileKey> removals = new ArrayList<>();
        for (TileKey key : loadedTiles) {
            float centreX = originX + (key.x() + 0.5f) * tileSize;
            float centreZ = originZ + (key.z() + 0.5f) * tileSize;
            if (Math.abs(centreX - focus.x) > limit || Math.abs(centreZ - focus.z) > limit) {
                removals.add(key);
            }
        }
        for (TileKey key : removals) {
            mesh.get().removeTile(key.x(), key.z());
            loadedTiles.remove(key);
        }
    }

    private List<TileKey> tilesWithin(Vector3f focus, float radius) {
        float tileSize = settings.tileWorldSize();
        int lowX = (int) Math.floor((focus.x - radius - originX) / tileSize);
        int highX = (int) Math.floor((focus.x + radius - originX) / tileSize);
        int lowZ = (int) Math.floor((focus.z - radius - originZ) / tileSize);
        int highZ = (int) Math.floor((focus.z + radius - originZ) / tileSize);
        List<TileKey> keys = new ArrayList<>();
        for (int tileX = lowX; tileX <= highX; tileX++) {
            for (int tileZ = lowZ; tileZ <= highZ; tileZ++) {
                keys.add(new TileKey(tileX, tileZ));
            }
        }
        return keys;
    }

    private List<TileKey> tilesCovering(NavGeometrySource geometrySource) {
        NavGeometry geometry = geometrySource.geometry();
        if (geometry.isEmpty()) {
            return List.of();
        }
        float[] vertices = geometry.vertexArray();
        float minimumX = Float.MAX_VALUE;
        float minimumZ = Float.MAX_VALUE;
        float maximumX = -Float.MAX_VALUE;
        float maximumZ = -Float.MAX_VALUE;
        for (int offset = 0; offset + 2 < vertices.length; offset += 3) {
            minimumX = Math.min(minimumX, vertices[offset]);
            maximumX = Math.max(maximumX, vertices[offset]);
            minimumZ = Math.min(minimumZ, vertices[offset + 2]);
            maximumZ = Math.max(maximumZ, vertices[offset + 2]);
        }
        Vector3f centre = new Vector3f((minimumX + maximumX) * 0.5f, 0.0f, (minimumZ + maximumZ) * 0.5f);
        float radius = Math.max(maximumX - minimumX, maximumZ - minimumZ) * 0.5f;
        return tilesWithin(centre, radius);
    }

    public void clear() {
        mesh = Optional.empty();
        loadedTiles.clear();
        bakedTriangleCount = 0;
    }

    public Optional<NavigationMesh> mesh() {
        return mesh;
    }

    public Optional<Vector3f> nearestPoint(Vector3f position) {
        return mesh.flatMap(built -> built.nearestPoint(position));
    }

    public List<Vector3f> findPath(Vector3f from, Vector3f to) {
        return mesh.map(built -> built.findPath(from, to)).orElseGet(List::of);
    }
}
