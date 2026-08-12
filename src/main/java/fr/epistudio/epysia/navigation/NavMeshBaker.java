package fr.epistudio.epysia.navigation;

import org.recast4j.detour.MeshData;
import org.recast4j.detour.NavMeshBuilder;
import org.recast4j.detour.NavMeshDataCreateParams;
import org.recast4j.recast.AreaModification;
import org.recast4j.recast.PolyMesh;
import org.recast4j.recast.PolyMeshDetail;
import org.recast4j.recast.RecastBuilder;
import org.recast4j.recast.RecastBuilderConfig;
import org.recast4j.recast.RecastConfig;
import org.recast4j.recast.RecastConstants;
import org.recast4j.recast.geom.SimpleInputGeomProvider;

import java.util.Optional;

public final class NavMeshBaker {

    private static final AreaModification WALKABLE = new AreaModification(1);
    private static final int VERTICES_PER_POLYGON = 6;
    private static final int WALKABLE_FLAG = 1;
    private static final float VERTICAL_MARGIN = 1.0f;

    private NavMeshBaker() {
    }

    public static Optional<MeshData> bakeTile(NavGeometrySource source, NavMeshSettings settings,
                                              float originX, float originZ, int tileX, int tileZ) {
        RecastConfig config = tiledConfig(settings);
        float[] queryMinimum = tileMinimum(settings, config, originX, originZ, tileX, tileZ);
        float[] queryMaximum = tileMaximum(settings, config, queryMinimum);
        NavGeometry geometry = source.geometryWithin(queryMinimum, queryMaximum);
        if (geometry.isEmpty()) {
            return Optional.empty();
        }
        return buildTile(geometry, settings, config, originX, originZ, tileX, tileZ);
    }

    private static Optional<MeshData> buildTile(NavGeometry geometry, NavMeshSettings settings,
                                                RecastConfig config, float originX, float originZ,
                                                int tileX, int tileZ) {
        SimpleInputGeomProvider input =
                new SimpleInputGeomProvider(geometry.vertexArray(), geometry.indexArray());
        float[] bounds = new float[]{originX, input.getMeshBoundsMin()[1] - VERTICAL_MARGIN, originZ};
        float[] ceiling = new float[]{originX, input.getMeshBoundsMax()[1] + VERTICAL_MARGIN, originZ};
        RecastBuilderConfig builderConfig = new RecastBuilderConfig(config, bounds, ceiling, tileX, tileZ);
        RecastBuilder.RecastBuilderResult result = new RecastBuilder().build(input, builderConfig);
        return meshDataFrom(result, settings, tileX, tileZ);
    }

    private static float[] tileMinimum(NavMeshSettings settings, RecastConfig config,
                                       float originX, float originZ, int tileX, int tileZ) {
        float tileSize = settings.tileWorldSize();
        float border = config.borderSize * settings.cellSize();
        return new float[]{originX + tileX * tileSize - border, -Float.MAX_VALUE,
                originZ + tileZ * tileSize - border};
    }

    private static float[] tileMaximum(NavMeshSettings settings, RecastConfig config, float[] minimum) {
        float tileSize = settings.tileWorldSize();
        float border = config.borderSize * settings.cellSize();
        return new float[]{minimum[0] + tileSize + border * 2.0f, Float.MAX_VALUE,
                minimum[2] + tileSize + border * 2.0f};
    }

    private static RecastConfig tiledConfig(NavMeshSettings settings) {
        int border = RecastConfig.calcBorder(settings.agentRadius(), settings.cellSize());
        return new RecastConfig(true, settings.tileSizeCells(), settings.tileSizeCells(), border,
                RecastConstants.PartitionType.WATERSHED, settings.cellSize(), settings.cellHeight(),
                settings.agentMaximumSlopeDegrees(), true, true, true,
                settings.agentHeight(), settings.agentRadius(), settings.agentMaximumClimb(),
                settings.regionMinimumArea() * settings.regionMinimumArea()
                        * settings.cellSize() * settings.cellSize(),
                settings.regionMergeArea() * settings.regionMergeArea()
                        * settings.cellSize() * settings.cellSize(),
                settings.edgeMaximumLength(), settings.edgeMaximumError(), VERTICES_PER_POLYGON, true,
                settings.detailSampleDistance(), settings.detailSampleMaximumError(), WALKABLE);
    }

    private static Optional<MeshData> meshDataFrom(RecastBuilder.RecastBuilderResult result,
                                                   NavMeshSettings settings, int tileX, int tileZ) {
        PolyMesh polygons = result.getMesh();
        if (polygons == null || polygons.npolys == 0) {
            return Optional.empty();
        }
        markWalkable(polygons);
        NavMeshDataCreateParams params = paramsFrom(polygons, result.getMeshDetail(), settings);
        params.tileX = tileX;
        params.tileZ = tileZ;
        return Optional.ofNullable(NavMeshBuilder.createNavMeshData(params));
    }

    private static void markWalkable(PolyMesh polygons) {
        for (int polygon = 0; polygon < polygons.npolys; polygon++) {
            polygons.flags[polygon] = polygons.areas[polygon] == 0 ? 0 : WALKABLE_FLAG;
        }
    }

    private static NavMeshDataCreateParams paramsFrom(PolyMesh polygons, PolyMeshDetail detail,
                                                      NavMeshSettings settings) {
        NavMeshDataCreateParams params = new NavMeshDataCreateParams();
        params.verts = polygons.verts;
        params.vertCount = polygons.nverts;
        params.polys = polygons.polys;
        params.polyAreas = polygons.areas;
        params.polyFlags = polygons.flags;
        params.polyCount = polygons.npolys;
        params.nvp = polygons.nvp;
        applyDetail(params, detail);
        params.walkableHeight = settings.agentHeight();
        params.walkableRadius = settings.agentRadius();
        params.walkableClimb = settings.agentMaximumClimb();
        params.bmin = polygons.bmin;
        params.bmax = polygons.bmax;
        params.cs = settings.cellSize();
        params.ch = settings.cellHeight();
        params.buildBvTree = true;
        return params;
    }

    private static void applyDetail(NavMeshDataCreateParams params, PolyMeshDetail detail) {
        if (detail == null) {
            return;
        }
        params.detailMeshes = detail.meshes;
        params.detailVerts = detail.verts;
        params.detailVertsCount = detail.nverts;
        params.detailTris = detail.tris;
        params.detailTriCount = detail.ntris;
    }
}
