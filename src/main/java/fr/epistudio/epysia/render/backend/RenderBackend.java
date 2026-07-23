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

    TextureHandle createTexture(TextureDescriptor descriptor);

    RenderTargetHandle createRenderTarget(RenderTargetDescriptor descriptor);

    BindingSetHandle createBindingSet(BindingSetDescriptor descriptor);

    void writeBuffer(BufferHandle handle, ByteBuffer data, long byteOffset);

    void setUniformSlotOverride(int slot, BufferHandle buffer, long byteOffset, long byteSize);

    void clearUniformSlotOverride();

    void readBuffer(BufferHandle handle, ByteBuffer destination, long byteOffset);

    void writeTexture(TextureHandle handle, ByteBuffer rgbaPixels);

    int textureWidth(TextureHandle handle);

    int textureHeight(TextureHandle handle);

    void copyTextureLayer(TextureHandle source, int sourceLayer, TextureHandle destination, int destinationLayer);

    void updatePipelineShaders(PipelineHandle handle, ShaderSource shaders);

    void destroy(PipelineHandle handle);

    void destroy(MeshHandle handle);

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

    void execute(DrawCommand command);

    void endPass();

    void endFrame();

    int readPixelArgb(RenderTargetHandle target, int x, int y);

    PixelColor readPixelFloat(RenderTargetHandle target, int x, int y);
}
