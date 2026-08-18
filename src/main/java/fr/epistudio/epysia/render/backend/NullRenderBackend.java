package fr.epistudio.epysia.render.backend;

import java.nio.ByteBuffer;
import java.nio.FloatBuffer;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

public final class NullRenderBackend implements RenderBackend {
    private final AtomicLong nextHandleId = new AtomicLong(1L);
    private final Map<Long, MeshShape> meshShapes = new HashMap<>();
    private final Map<Long, TextureShape> textureShapes = new HashMap<>();
    private final DrawStatistics drawStatistics = new DrawStatistics();

    @Override
    public void initialize(RenderSurface surface) {
    }

    @Override
    public void shutdown() {
        meshShapes.clear();
        textureShapes.clear();
    }

    private long allocate() {
        return nextHandleId.getAndIncrement();
    }

    @Override
    public PipelineHandle createPipeline(PipelineDescriptor descriptor) {
        return new PipelineHandle(allocate());
    }

    @Override
    public PipelineHandle createComputePipeline(ComputePipelineDescriptor descriptor) {
        return new PipelineHandle(allocate());
    }

    @Override
    public void dispatchCompute(ComputeDispatch dispatch) {
    }

    @Override
    public void computeBarrier(ComputeBarrier barrier) {
    }

    @Override
    public MeshHandle createMesh(MeshDescriptor descriptor) {
        long id = allocate();
        meshShapes.put(id, new MeshShape(descriptor.firstIndex(), descriptor.indexCount(),
                descriptor.indexFormat()));
        return new MeshHandle(id);
    }

    @Override
    public BufferHandle createBuffer(BufferDescriptor descriptor) {
        return new BufferHandle(allocate());
    }

    @Override
    public TextureHandle createTexture(TextureDescriptor descriptor) {
        long id = allocate();
        textureShapes.put(id, new TextureShape(descriptor.width(), descriptor.height()));
        return new TextureHandle(id);
    }

    @Override
    public void generateMipmaps(TextureHandle handle) {
    }

    @Override
    public RenderTargetHandle createRenderTarget(RenderTargetDescriptor descriptor) {
        return new RenderTargetHandle(allocate());
    }

    @Override
    public BindingSetHandle createBindingSet(BindingSetDescriptor descriptor) {
        return new BindingSetHandle(allocate());
    }

    @Override
    public void writeBuffer(BufferHandle handle, ByteBuffer data, long byteOffset) {
    }

    @Override
    public void setUniformSlotOverride(int slot, BufferHandle buffer, long byteOffset, long byteSize) {
    }

    @Override
    public void clearUniformSlotOverride() {
    }

    @Override
    public void readBuffer(BufferHandle handle, ByteBuffer destination, long byteOffset) {
    }

    @Override
    public void writeTexture(TextureHandle handle, ByteBuffer rgbaPixels) {
    }

    @Override
    public void writeTexture(TextureHandle handle, FloatBuffer rgbaPixels) {
    }

    @Override
    public void readTextureLevel(TextureHandle handle, int mipLevel, FloatBuffer destination) {
    }

    @Override
    public int meshIndexCount(MeshHandle handle) {
        return shapeOf(handle).indexCount();
    }

    @Override
    public int meshFirstIndex(MeshHandle handle) {
        return shapeOf(handle).firstIndex();
    }

    @Override
    public IndexFormat meshIndexFormat(MeshHandle handle) {
        return shapeOf(handle).indexFormat();
    }

    private MeshShape shapeOf(MeshHandle handle) {
        return meshShapes.getOrDefault(handle.id(), MeshShape.UNKNOWN);
    }

    @Override
    public void updateMeshRange(MeshHandle handle, int firstIndex, int indexCount) {
        MeshShape existing = shapeOf(handle);
        meshShapes.put(handle.id(), new MeshShape(firstIndex, indexCount, existing.indexFormat()));
    }

    @Override
    public boolean hasTexture(TextureHandle handle) {
        return true;
    }

    @Override
    public int textureWidth(TextureHandle handle) {
        return textureShapes.getOrDefault(handle.id(), TextureShape.UNKNOWN).width();
    }

    @Override
    public int textureHeight(TextureHandle handle) {
        return textureShapes.getOrDefault(handle.id(), TextureShape.UNKNOWN).height();
    }

    @Override
    public void copyTextureLayer(TextureHandle source, int sourceLayer,
                                 TextureHandle destination, int destinationLayer) {
    }

    @Override
    public void copyTextureRegion(TextureHandle source, int sourceLayer, int sourceX, int sourceY,
                                  TextureHandle destination, int destinationLayer,
                                  int destinationX, int destinationY, int width, int height) {
    }

    @Override
    public void updatePipelineShaders(PipelineHandle handle, ShaderSource shaders) {
    }

    @Override
    public void destroy(PipelineHandle handle) {
    }

    @Override
    public void destroy(MeshHandle handle) {
        meshShapes.remove(handle.id());
    }

    @Override
    public void destroy(BufferHandle handle) {
    }

    @Override
    public void destroy(TextureHandle handle) {
        textureShapes.remove(handle.id());
    }

    @Override
    public void destroy(RenderTargetHandle handle) {
    }

    @Override
    public void destroy(BindingSetHandle handle) {
    }

    @Override
    public void onViewportResize(int width, int height) {
    }

    @Override
    public void beginFrame() {
        drawStatistics.reset();
    }

    @Override
    public void beginProfileSection(String name) {
    }

    @Override
    public void endProfileSection() {
    }

    @Override
    public Map<String, Long> latestProfileTimingsNanos() {
        return Map.of();
    }

    @Override
    public DrawStatistics drawStatistics() {
        return drawStatistics;
    }

    @Override
    public void beginPass(RenderTargetHandle target, PassClear clear) {
    }

    @Override
    public void execute(DrawCommand command) {
    }

    @Override
    public void endPass() {
    }

    @Override
    public void endFrame() {
    }

    @Override
    public int readPixelArgb(RenderTargetHandle target, int x, int y) {
        return 0;
    }

    @Override
    public PixelColor readPixelFloat(RenderTargetHandle target, int x, int y) {
        return new PixelColor(0.0f, 0.0f, 0.0f, 0.0f);
    }

    @Override
    public void readPixelsRgba(RenderTargetHandle target, int x, int y,
                               int width, int height, ByteBuffer destination) {
    }

    private record MeshShape(int firstIndex, int indexCount, IndexFormat indexFormat) {
        private static final MeshShape UNKNOWN = new MeshShape(0, 0, IndexFormat.UINT32);
    }

    private record TextureShape(int width, int height) {
        private static final TextureShape UNKNOWN = new TextureShape(1, 1);
    }
}
