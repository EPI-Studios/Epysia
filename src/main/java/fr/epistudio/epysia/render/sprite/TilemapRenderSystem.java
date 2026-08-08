package fr.epistudio.epysia.render.sprite;

import fr.epistudio.epysia.assets.epyatlas.SpriteAtlas;
import fr.epistudio.epysia.assets.epyatlas.SpriteAtlasRegion;
import fr.epistudio.epysia.assets.epytilemap.CellBounds;
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
import fr.epistudio.epysia.render.backend.BindingSetHandle;
import fr.epistudio.epysia.render.backend.PipelineHandle;
import fr.epistudio.epysia.render.backend.IndexFormat;
import fr.epistudio.epysia.render.backend.MeshDescriptor;
import fr.epistudio.epysia.render.backend.MeshHandle;
import fr.epistudio.epysia.render.backend.RenderBackend;
import fr.epistudio.epysia.render.backend.TextureHandle;
import fr.epistudio.epysia.scene.Scene;
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
import java.util.Objects;
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
        private final Vector3f builtTint = new Vector3f();
        private Object2dUniform objectUniform;
        private BufferHandle vertexBuffer;
        private BufferHandle indexBuffer;
        private SpriteTilemap builtTilemap;
        private long builtVersion;
        private long builtTextureId;
        private float builtOpacity;
        private int builtSurfaceHash;
    }

    private final SpriteRenderSystem spriteRenderSystem;
    private final Logger logger;
    private final Map<TilemapRenderer, RendererGeometry> geometryByRenderer = new IdentityHashMap<>();
    private final Set<TilemapRenderer> seenRenderers = Collections.newSetFromMap(new IdentityHashMap<>());
    private final Set<TilemapRenderer> warnedEmptyRenderers = Collections.newSetFromMap(new IdentityHashMap<>());
    private final Vector4f scratchModulate = new Vector4f();

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
        for (TilemapRenderer renderer : scene.componentsOf(TilemapRenderer.class)) {
            renderer.owner().ifPresent(owner -> collectGameObject(frame, owner));
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
        if (geometry.objectUniform == null) {
            geometry.objectUniform = new Object2dUniform(backend);
        }
        geometry.objectUniform.write(backend, transform.worldMatrix());
        if (requiresRebuild(geometry, renderer, tilemap, texture)) {
            rebuildGeometry(geometry, renderer, tilemap, atlas, texture);
        }
        boolean lit = renderer.lit();
        PipelineHandle pipeline = lit
                ? spriteRenderSystem.sharedLitPipeline() : spriteRenderSystem.sharedPipeline();
        BindingSetHandle bindings = lit
                ? spriteRenderSystem.sharedLitBindings(texture,
                        renderer.normalMapRef().directOrNull(),
                        renderer.metallicRoughnessMapRef().directOrNull(),
                        renderer.emissiveMapRef().directOrNull(),
                        geometry.objectUniform.handle())
                : spriteRenderSystem.bindingsFor(texture, geometry.objectUniform.handle());
        for (ChunkMesh chunk : geometry.chunks) {
            long sortKey = SpriteSortKeys.compose(renderer.sortingLayer(), renderer.orderInLayer(),
                    SpriteSortKeys.KIND_TILEMAP, submitSequence++);
            frame.submit(RenderPasses.OVERLAY_2D,
                    new DrawCommand(pipeline, chunk.mesh(), bindings, sortKey, 1));
        }
    }

    private static int surfaceHashOf(TilemapRenderer renderer) {
        return Objects.hash(renderer.metallic(), renderer.roughness(),
                renderer.normalStrength(), renderer.emissiveStrength(), renderer.lightLayers());
    }

    private static boolean requiresRebuild(RendererGeometry geometry,
                                           TilemapRenderer renderer, SpriteTilemap tilemap, TextureHandle texture) {
        return geometry.builtTilemap != tilemap
                || geometry.builtVersion != tilemap.version()
                || geometry.builtTextureId != texture.id()
                || geometry.builtOpacity != renderer.opacity()
                || geometry.builtSurfaceHash != surfaceHashOf(renderer)
                || !geometry.builtTint.equals(renderer.tint());
    }

    private void rebuildGeometry(RendererGeometry geometry, TilemapRenderer renderer,
                                 SpriteTilemap tilemap, SpriteAtlas atlas, TextureHandle texture) {
        destroyChunks(geometry);
        uploadChunks(geometry, renderer, tilemap, atlas);
        geometry.builtTilemap = tilemap;
        geometry.builtVersion = tilemap.version();
        geometry.builtTextureId = texture.id();
        geometry.builtOpacity = renderer.opacity();
        geometry.builtSurfaceHash = surfaceHashOf(renderer);
        geometry.builtTint.set(renderer.tint());
        logger.info("[TilemapRenderSystem] rebuilt " + geometry.chunks.size() + " chunk(s) for tilemap version "
                + tilemap.version());
    }

    private void uploadChunks(RendererGeometry geometry, TilemapRenderer renderer,
                              SpriteTilemap tilemap, SpriteAtlas atlas) {
        List<Integer> chunkQuadCounts = new ArrayList<>();
        ByteBuffer vertices = buildChunkVertices(renderer, tilemap, atlas, chunkQuadCounts);
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

    private ByteBuffer buildChunkVertices(TilemapRenderer renderer,
                                          SpriteTilemap tilemap, SpriteAtlas atlas, List<Integer> chunkQuadCounts) {
        ByteBuffer vertices = BufferUtils.createByteBuffer(Math.max(1, countFilledCells(tilemap))
                * SpriteRenderSystem.VERTICES_PER_QUAD * SpriteRenderSystem.VERTEX_BYTES);
        CellBounds bounds = tilemap.usedBounds();
        if (bounds.isEmpty()) {
            return vertices.flip();
        }
        int chunkColumns = (bounds.widthCells() + CHUNK_SIZE - 1) / CHUNK_SIZE;
        int chunkRows = (bounds.heightCells() + CHUNK_SIZE - 1) / CHUNK_SIZE;
        for (int layerIndex : drawOrder(tilemap)) {
            for (int chunkY = 0; chunkY < chunkRows; chunkY++) {
                for (int chunkX = 0; chunkX < chunkColumns; chunkX++) {
                    int quadCount = appendChunk(vertices, renderer, tilemap, atlas,
                            layerIndex, bounds, chunkX, chunkY);
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
            filled += tilemap.layer(layerIndex).paintedCellCount();
        }
        return filled;
    }

    private int appendChunk(ByteBuffer vertices, TilemapRenderer renderer,
                            SpriteTilemap tilemap, SpriteAtlas atlas, int layerIndex,
                            CellBounds bounds, int chunkX, int chunkY) {
        int quadCount = 0;
        int startX = bounds.minX() + chunkX * CHUNK_SIZE;
        int startY = bounds.minY() + chunkY * CHUNK_SIZE;
        int endX = Math.min(startX + CHUNK_SIZE, bounds.maxX() + 1);
        int endY = Math.min(startY + CHUNK_SIZE, bounds.maxY() + 1);
        for (int cellY = startY; cellY < endY; cellY++) {
            for (int cellX = startX; cellX < endX; cellX++) {
                if (appendCell(vertices, renderer, tilemap, atlas, layerIndex, cellX, cellY)) {
                    quadCount++;
                }
            }
        }
        return quadCount;
    }

    private boolean appendCell(ByteBuffer vertices, TilemapRenderer renderer,
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
        appendCellQuad(vertices, renderer, tilemap, region.get(), data,
                tilemap.layer(layerIndex).modulate(), cellX, cellY);
        return true;
    }

    private void appendCellQuad(ByteBuffer vertices, TilemapRenderer renderer,
                                SpriteTilemap tilemap, SpriteAtlasRegion region, TileData data,
                                Vector4f layerModulate, int cellX, int cellY) {
        float left = cellX * tilemap.cellWidth();
        float bottom = cellY * tilemap.cellHeight();
        scratchModulate.set(layerModulate).mul(data.modulate());
        for (int corner = 0; corner < CORNERS_X.length; corner++) {
            float cornerX = left + CORNERS_X[corner] * tilemap.cellWidth();
            float cornerY = bottom + CORNERS_Y[corner] * tilemap.cellHeight();
            appendVertex(vertices, cornerX, cornerY,
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

    private void appendVertex(ByteBuffer vertices, float cornerX, float cornerY,
                              float u, float v, TilemapRenderer renderer) {
        Vector3f tint = renderer.tint();
        vertices.putFloat(cornerX).putFloat(cornerY);
        vertices.putFloat(u).putFloat(v);
        vertices.putFloat(tint.x * scratchModulate.x)
                .putFloat(tint.y * scratchModulate.y)
                .putFloat(tint.z * scratchModulate.z)
                .putFloat(renderer.opacity() * scratchModulate.w);
        vertices.putFloat(renderer.metallic()).putFloat(renderer.roughness())
                .putFloat(renderer.normalStrength()).putFloat(renderer.emissiveStrength());
        vertices.putFloat(1.0f).putFloat(1.0f).putFloat(renderer.lightLayers()).putFloat(0.0f);
        putVector4(vertices, renderer.shaderParams0());
        putVector4(vertices, renderer.shaderParams1());
    }

    private static void putVector4(ByteBuffer vertices, org.joml.Vector4f value) {
        vertices.putFloat(value.x).putFloat(value.y).putFloat(value.z).putFloat(value.w);
    }

    private void releaseUnseenGeometry() {
        Iterator<Map.Entry<TilemapRenderer, RendererGeometry>> entries = geometryByRenderer.entrySet().iterator();
        while (entries.hasNext()) {
            Map.Entry<TilemapRenderer, RendererGeometry> entry = entries.next();
            if (!seenRenderers.contains(entry.getKey())) {
                releaseGeometry(entry.getValue());
                entries.remove();
            }
        }
    }

    private void releaseGeometry(RendererGeometry geometry) {
        destroyChunks(geometry);
        if (geometry.objectUniform != null) {
            geometry.objectUniform.destroy(backend);
            geometry.objectUniform = null;
        }
    }

    private void destroyChunks(RendererGeometry geometry) {
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
        geometryByRenderer.values().forEach(this::releaseGeometry);
        geometryByRenderer.clear();
    }
}
