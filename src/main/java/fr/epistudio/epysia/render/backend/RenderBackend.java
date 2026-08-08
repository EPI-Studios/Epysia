package fr.epistudio.epysia.render.backend;

import java.nio.ByteBuffer;
import java.util.Map;

public interface RenderBackend {
    void initialize(RenderSurface surface);

    void shutdown();

    PipelineHandle createPipeline(PipelineDescriptor descriptor);

    PipelineHandle createComputePipeline(ComputePipelineDescriptor descriptor);

    void dispatchCompute(ComputeDispatch dispatch);

    void computeBarrier(ComputeBarrier barrier);

    MeshHandle createMesh(MeshDescriptor descriptor);

    BufferHandle createBuffer(BufferDescriptor descriptor);

    default int perFrameBufferSlots() {
        return 1;
    }

    default int uniformBufferOffsetAlignment() {
        return 256;
    }

    TextureHandle createTexture(TextureDescriptor descriptor);

    void generateMipmaps(TextureHandle handle);

    RenderTargetHandle createRenderTarget(RenderTargetDescriptor descriptor);

    BindingSetHandle createBindingSet(BindingSetDescriptor descriptor);

    void writeBuffer(BufferHandle handle, ByteBuffer data, long byteOffset);

    void setUniformSlotOverride(int slot, BufferHandle buffer, long byteOffset, long byteSize);

    void clearUniformSlotOverride();

    void readBuffer(BufferHandle handle, ByteBuffer destination, long byteOffset);

    void writeTexture(TextureHandle handle, ByteBuffer rgbaPixels);

    void writeTexture(TextureHandle handle, java.nio.FloatBuffer rgbaPixels);

    void readTextureLevel(TextureHandle handle, int mipLevel, java.nio.FloatBuffer destination);

    int meshIndexCount(MeshHandle handle);

    int meshFirstIndex(MeshHandle handle);

    IndexFormat meshIndexFormat(MeshHandle handle);

    void updateMeshRange(MeshHandle handle, int firstIndex, int indexCount);

    int textureWidth(TextureHandle handle);

    int textureHeight(TextureHandle handle);

    void copyTextureLayer(TextureHandle source, int sourceLayer, TextureHandle destination, int destinationLayer);

    void copyTextureRegion(TextureHandle source, int sourceLayer, int sourceX, int sourceY,
                           TextureHandle destination, int destinationLayer, int destinationX, int destinationY,
                           int width, int height);

    void updatePipelineShaders(PipelineHandle handle, ShaderSource shaders);

    void destroy(PipelineHandle handle);

    void destroy(MeshHandle handle);

    default boolean isAlive(MeshHandle handle) {
        return true;
    }

    default boolean isAlive(PipelineHandle handle) {
        return true;
    }

    void destroy(BufferHandle handle);

    void destroy(TextureHandle handle);

    void destroy(RenderTargetHandle handle);

    void destroy(BindingSetHandle handle);

    void onViewportResize(int width, int height);

    void beginFrame();

    void beginProfileSection(String name);

    void endProfileSection();

    Map<String, Long> latestProfileTimingsNanos();

    DrawStatistics drawStatistics();

    void beginPass(RenderTargetHandle target, PassClear clear);

    default void setPassViewport(int x, int y, int width, int height) {
    }

    void execute(DrawCommand command);

    void endPass();

    void endFrame();

    int readPixelArgb(RenderTargetHandle target, int x, int y);

    PixelColor readPixelFloat(RenderTargetHandle target, int x, int y);

    void readPixelsRgba(RenderTargetHandle target, int x, int y, int width, int height, ByteBuffer destination);
}
