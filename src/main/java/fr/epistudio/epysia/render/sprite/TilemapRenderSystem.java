package fr.epistudio.epysia.render.sprite;

import fr.epistudio.epysia.assets.epyatlas.SpriteAtlas;
import fr.epistudio.epysia.assets.epyatlas.SpriteAtlasRegion;
import fr.epistudio.epysia.assets.epytilemap.SpriteTilemap;
import fr.epistudio.epysia.assets.epytilemap.TileData;
import fr.epistudio.epysia.components.TilemapRenderer;
import fr.epistudio.epysia.components.transforms.Transform2D;
import fr.epistudio.epysia.gameobjects.GameObject;
import fr.epistudio.epysia.logging.Logger;
import fr.epistudio.epysia.render.FrameBuilder;
import fr.epistudio.epysia.render.RenderContext;
import fr.epistudio.epysia.render.RenderPasses;
import fr.epistudio.epysia.render.RenderSystem;
import fr.epistudio.epysia.render.StageConfigurer;
import fr.epistudio.epysia.render.backend.BufferDescriptor;
import fr.epistudio.epysia.render.backend.BufferHandle;
import fr.epistudio.epysia.render.backend.BufferUsage;
import fr.epistudio.epysia.render.backend.DrawCommand;
import fr.epistudio.epysia.render.backend.IndexFormat;
import fr.epistudio.epysia.render.backend.MeshDescriptor;
import fr.epistudio.epysia.render.backend.MeshHandle;
import fr.epistudio.epysia.render.backend.RenderBackend;
import fr.epistudio.epysia.render.backend.TextureHandle;
import fr.epistudio.epysia.scene.Scene;
import org.joml.Matrix3x2f;
import org.joml.Vector2f;
import org.joml.Vector3f;
import org.joml.Vector4f;
import org.lwjgl.BufferUtils;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

public final class TilemapRenderSystem implements RenderSystem {

    static final int CHUNK_SIZE = 16;

    private static final float[] CORNERS_X = {0.0f, 1.0f, 1.0f, 0.0f};
    private static final float[] CORNERS_Y = {0.0f, 0.0f, 1.0f, 1.0f};

    private record ChunkMesh(MeshHandle mesh) {
    }

    private static final class RendererGeometry {
        private final List<ChunkMesh> chunks = new ArrayList<>();
        private final Matrix3x2f builtMatrix = new Matrix3x2f();
        private final Vector3f builtTint = new Vector3f();
        private BufferHandle vertexBuffer;
        private BufferHandle indexBuffer;
        private SpriteTilemap builtTilemap;
        private long builtVersion;
        private long builtTextureId;
        private float builtOpacity;
    }

    private final SpriteRenderSystem spriteRenderSystem;
    private final Logger logger;
    private final Map<TilemapRenderer, RendererGeometry> geometryByRenderer = new IdentityHashMap<>();
    private final Set<TilemapRenderer> seenRenderers = Collections.newSetFromMap(new IdentityHashMap<>());
    private final Set<TilemapRenderer> warnedEmptyRenderers = Collections.newSetFromMap(new IdentityHashMap<>());
    private final Vector4f scratchModulate = new Vector4f();
    private final Vector2f scratchCorner = new Vector2f();

    private RenderBackend backend;
    private long submitSequence;

    public TilemapRenderSystem(SpriteRenderSystem spriteRenderSystem, Logger logger) {
        this.spriteRenderSystem = spriteRenderSystem;
        this.logger = logger;
    }

    @Override
    public void initialize(RenderBackend backend, StageConfigurer configurer) {
        this.backend = backend;
    }

    @Override
    public void collect(Scene scene, FrameBuilder frame, RenderContext context) {
        seenRenderers.clear();
        submitSequence = 0L;
        for (GameObject gameObject : scene.gameObjects()) {
            collectGameObject(frame, gameObject);
        }
        releaseUnseenGeometry();
    }

    private void collectGameObject(FrameBuilder frame, GameObject gameObject) {
        Transform2D transform = gameObject.getComponentOrNull(Transform2D.class);
        TilemapRenderer renderer = gameObject.getComponentOrNull(TilemapRenderer.class);
        if (transform == null || renderer == null || !transform.visible()) {
            return;
        }
        Optional<SpriteTilemap> tilemap = renderer.tilemapValue();
        Optional<SpriteAtlas> atlas = renderer.atlasValue();
        Optional<TextureHandle> texture = renderer.texture();
        if (tilemap.isEmpty() || atlas.isEmpty() || texture.isEmpty()) {
            warnEmptyOnce(gameObject, renderer, tilemap, atlas, texture);
            return;
        }
        seenRenderers.add(renderer);
        submitRenderer(frame, transform, renderer, tilemap.get(), atlas.get(), texture.get());
    }

    private void warnEmptyOnce(GameObject gameObject, TilemapRenderer renderer, Optional<SpriteTilemap> tilemap,
                               Optional<SpriteAtlas> atlas, Optional<TextureHandle> texture) {
        if (!warnedEmptyRenderers.add(renderer)) {
            return;
        }
        logger.warn("[TilemapRenderSystem] skipped '" + gameObject.name() + "': "
                + firstEmptyReason(tilemap, atlas, texture) + " unresolved");
    }

    private static String firstEmptyReason(Optional<SpriteTilemap> tilemap, Optional<SpriteAtlas> atlas,
                                           Optional<TextureHandle> texture) {
        if (tilemap.isEmpty()) {
            return "tilemap";
        }
        if (atlas.isEmpty()) {
            return "atlas";
        }
        return "texture";
    }

    private void submitRenderer(FrameBuilder frame, Transform2D transform, TilemapRenderer renderer,
                                SpriteTilemap tilemap, SpriteAtlas atlas, TextureHandle texture) {
        RendererGeometry geometry = geometryByRenderer.computeIfAbsent(renderer,
                unused -> new RendererGeometry());
        if (requiresRebuild(geometry, transform, renderer, tilemap, texture)) {
            rebuildGeometry(geometry, transform, renderer, tilemap, atlas, texture);
        }
        for (ChunkMesh chunk : geometry.chunks) {
            long sortKey = SpriteSortKeys.compose(renderer.sortingLayer(), renderer.orderInLayer(),
                    SpriteSortKeys.KIND_TILEMAP, submitSequence++);
            frame.submit(RenderPasses.OVERLAY_2D, new DrawCommand(spriteRenderSystem.sharedPipeline(),
                    chunk.mesh(), spriteRenderSystem.bindingsFor(texture), sortKey, 1));
        }
    }

    private static boolean requiresRebuild(RendererGeometry geometry, Transform2D transform,
                                           TilemapRenderer renderer, SpriteTilemap tilemap, TextureHandle texture) {
        return geometry.builtTilemap != tilemap
                || geometry.builtVersion != tilemap.version()
                || geometry.builtTextureId != texture.id()
                || geometry.builtOpacity != renderer.opacity()
                || !geometry.builtTint.equals(renderer.tint())
                || !geometry.builtMatrix.equals(transform.localMatrix());
    }

    private void rebuildGeometry(RendererGeometry geometry, Transform2D transform, TilemapRenderer renderer,
                                 SpriteTilemap tilemap, SpriteAtlas atlas, TextureHandle texture) {
        destroyGeometry(geometry);
        uploadChunks(geometry, transform, renderer, tilemap, atlas);
        geometry.builtTilemap = tilemap;
        geometry.builtVersion = tilemap.version();
        geometry.builtTextureId = texture.id();
        geometry.builtOpacity = renderer.opacity();
        geometry.builtTint.set(renderer.tint());
        geometry.builtMatrix.set(transform.localMatrix());
        logger.info("[TilemapRenderSystem] rebuilt " + geometry.chunks.size() + " chunk(s) for tilemap version "
                + tilemap.version());
    }

    private void uploadChunks(RendererGeometry geometry, Transform2D transform, TilemapRenderer renderer,
                              SpriteTilemap tilemap, SpriteAtlas atlas) {
        List<Integer> chunkQuadCounts = new ArrayList<>();
        ByteBuffer vertices = buildChunkVertices(transform, renderer, tilemap, atlas, chunkQuadCounts);
        int totalQuads = vertices.limit() / (SpriteRenderSystem.VERTICES_PER_QUAD * SpriteRenderSystem.VERTEX_BYTES);
        if (totalQuads == 0) {
            return;
        }
        geometry.vertexBuffer = backend.createBuffer(new BufferDescriptor(BufferUsage.VERTEX, vertices));
        geometry.indexBuffer = backend.createBuffer(new BufferDescriptor(BufferUsage.INDEX,
                SpriteRenderSystem.buildIndexPattern(totalQuads)));
        createChunkMeshes(geometry, chunkQuadCounts);
    }

    private void createChunkMeshes(RendererGeometry geometry, List<Integer> chunkQuadCounts) {
        int firstQuad = 0;
        for (int quadCount : chunkQuadCounts) {
            MeshHandle mesh = backend.createMesh(new MeshDescriptor(geometry.vertexBuffer, geometry.indexBuffer,
                    firstQuad * SpriteRenderSystem.INDICES_PER_QUAD,
                    quadCount * SpriteRenderSystem.INDICES_PER_QUAD, IndexFormat.UINT32));
            geometry.chunks.add(new ChunkMesh(mesh));
            firstQuad += quadCount;
        }
    }

    private ByteBuffer buildChunkVertices(Transform2D transform, TilemapRenderer renderer,
                                          SpriteTilemap tilemap, SpriteAtlas atlas, List<Integer> chunkQuadCounts) {
        ByteBuffer vertices = BufferUtils.createByteBuffer(Math.max(1, countFilledCells(tilemap))
                * SpriteRenderSystem.VERTICES_PER_QUAD * SpriteRenderSystem.VERTEX_BYTES);
        int chunkColumns = (tilemap.width() + CHUNK_SIZE - 1) / CHUNK_SIZE;
        int chunkRows = (tilemap.height() + CHUNK_SIZE - 1) / CHUNK_SIZE;
        for (int layerIndex : drawOrder(tilemap)) {
            for (int chunkY = 0; chunkY < chunkRows; chunkY++) {
                for (int chunkX = 0; chunkX < chunkColumns; chunkX++) {
                    int quadCount = appendChunk(vertices, transform, renderer, tilemap, atlas,
                            layerIndex, chunkX, chunkY);
                    if (quadCount > 0) {
                        chunkQuadCounts.add(quadCount);
                    }
                }
            }
        }
        return vertices.flip();
    }

    private static List<Integer> drawOrder(SpriteTilemap tilemap) {
        List<Integer> order = new ArrayList<>();
        for (int layerIndex = 0; layerIndex < tilemap.layerCount(); layerIndex++) {
            if (tilemap.layer(layerIndex).visible()) {
                order.add(layerIndex);
            }
        }
        order.sort(Comparator.comparingInt(index -> tilemap.layer(index).sortingOrder()));
        return order;
    }

    private static int countFilledCells(SpriteTilemap tilemap) {
        int filled = 0;
        for (int layerIndex = 0; layerIndex < tilemap.layerCount(); layerIndex++) {
            for (int cellY = 0; cellY < tilemap.height(); cellY++) {
                for (int cellX = 0; cellX < tilemap.width(); cellX++) {
                    if (tilemap.tileIndex(layerIndex, cellX, cellY) != SpriteTilemap.EMPTY_TILE_INDEX) {
                        filled++;
                    }
                }
            }
        }
        return filled;
    }

    private int appendChunk(ByteBuffer vertices, Transform2D transform, TilemapRenderer renderer,
                            SpriteTilemap tilemap, SpriteAtlas atlas, int layerIndex, int chunkX, int chunkY) {
        int quadCount = 0;
        int endX = Math.min((chunkX + 1) * CHUNK_SIZE, tilemap.width());
        int endY = Math.min((chunkY + 1) * CHUNK_SIZE, tilemap.height());
        for (int cellY = chunkY * CHUNK_SIZE; cellY < endY; cellY++) {
            for (int cellX = chunkX * CHUNK_SIZE; cellX < endX; cellX++) {
                if (appendCell(vertices, transform, renderer, tilemap, atlas, layerIndex, cellX, cellY)) {
                    quadCount++;
                }
            }
        }
        return quadCount;
    }

    private boolean appendCell(ByteBuffer vertices, Transform2D transform, TilemapRenderer renderer,
                               SpriteTilemap tilemap, SpriteAtlas atlas, int layerIndex, int cellX, int cellY) {
        int tileIndex = tilemap.tileIndex(layerIndex, cellX, cellY);
        if (tileIndex == SpriteTilemap.EMPTY_TILE_INDEX) {
            return false;
        }
        Optional<SpriteAtlasRegion> region = atlas.region(Integer.toString(tileIndex));
        if (region.isEmpty()) {
            return false;
        }
        TileData data = tilemap.existingTileData(tileIndex).orElseGet(TileData::new);
        appendCellQuad(vertices, transform, renderer, tilemap, region.get(), data,
                tilemap.layer(layerIndex).modulate(), cellX, cellY);
        return true;
    }

    private void appendCellQuad(ByteBuffer vertices, Transform2D transform, TilemapRenderer renderer,
                                SpriteTilemap tilemap, SpriteAtlasRegion region, TileData data,
                                Vector4f layerModulate, int cellX, int cellY) {
        float left = cellX * tilemap.cellWidth();
        float bottom = cellY * tilemap.cellHeight();
        Matrix3x2f matrix = transform.localMatrix();
        scratchModulate.set(layerModulate).mul(data.modulate());
        for (int corner = 0; corner < CORNERS_X.length; corner++) {
            float cornerX = left + CORNERS_X[corner] * tilemap.cellWidth();
            float cornerY = bottom + CORNERS_Y[corner] * tilemap.cellHeight();
            appendVertex(vertices, matrix, cornerX, cornerY,
                    sampleU(region, data, corner), sampleV(region, data, corner), renderer);
        }
    }

    private static float sampleU(SpriteAtlasRegion region, TileData data, int corner) {
        float unit = data.transpose() ? CORNERS_Y[corner] : CORNERS_X[corner];
        return region.minU() + (data.flipHorizontal() ? 1.0f - unit : unit) * (region.maxU() - region.minU());
    }

    private static float sampleV(SpriteAtlasRegion region, TileData data, int corner) {
        float unit = data.transpose() ? CORNERS_X[corner] : CORNERS_Y[corner];
        return region.minV() + (data.flipVertical() ? 1.0f - unit : unit) * (region.maxV() - region.minV());
    }

    private void appendVertex(ByteBuffer vertices, Matrix3x2f matrix, float cornerX, float cornerY,
                              float u, float v, TilemapRenderer renderer) {
        scratchCorner.set(cornerX, cornerY);
        matrix.transformPosition(scratchCorner);
        Vector3f tint = renderer.tint();
        vertices.putFloat(scratchCorner.x).putFloat(scratchCorner.y);
        vertices.putFloat(u).putFloat(v);
        vertices.putFloat(tint.x * scratchModulate.x)
                .putFloat(tint.y * scratchModulate.y)
                .putFloat(tint.z * scratchModulate.z)
                .putFloat(renderer.opacity() * scratchModulate.w);
    }

    private void releaseUnseenGeometry() {
        Iterator<Map.Entry<TilemapRenderer, RendererGeometry>> entries = geometryByRenderer.entrySet().iterator();
        while (entries.hasNext()) {
            Map.Entry<TilemapRenderer, RendererGeometry> entry = entries.next();
            if (!seenRenderers.contains(entry.getKey())) {
                destroyGeometry(entry.getValue());
                entries.remove();
            }
        }
    }

    private void destroyGeometry(RendererGeometry geometry) {
        for (ChunkMesh chunk : geometry.chunks) {
            backend.destroy(chunk.mesh());
        }
        geometry.chunks.clear();
        if (geometry.vertexBuffer != null) {
            backend.destroy(geometry.vertexBuffer);
            geometry.vertexBuffer = null;
        }
        if (geometry.indexBuffer != null) {
            backend.destroy(geometry.indexBuffer);
            geometry.indexBuffer = null;
        }
    }

    @Override
    public void shutdown(RenderBackend backend) {
        geometryByRenderer.values().forEach(this::destroyGeometry);
        geometryByRenderer.clear();
    }
}
