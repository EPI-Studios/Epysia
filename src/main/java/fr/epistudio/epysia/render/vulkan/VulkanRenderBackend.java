package fr.epistudio.epysia.render.vulkan;

import fr.epistudio.epysia.exceptions.EpysiaException;
import fr.epistudio.epysia.logging.ConsoleLogger;
import fr.epistudio.epysia.render.backend.BindingSetDescriptor;
import fr.epistudio.epysia.render.backend.BindingSetHandle;
import fr.epistudio.epysia.render.backend.BufferDescriptor;
import fr.epistudio.epysia.render.backend.BufferHandle;
import fr.epistudio.epysia.render.backend.BufferUsage;
import fr.epistudio.epysia.render.backend.ComputeBarrier;
import fr.epistudio.epysia.render.backend.ComputeDispatch;
import fr.epistudio.epysia.render.backend.ComputePipelineDescriptor;
import fr.epistudio.epysia.render.backend.DrawCommand;
import fr.epistudio.epysia.render.backend.DrawStatistics;
import fr.epistudio.epysia.render.backend.IndexFormat;
import fr.epistudio.epysia.render.backend.MeshDescriptor;
import fr.epistudio.epysia.render.backend.MeshHandle;
import fr.epistudio.epysia.render.backend.PassClear;
import fr.epistudio.epysia.render.backend.PipelineDescriptor;
import fr.epistudio.epysia.render.backend.PipelineHandle;
import fr.epistudio.epysia.render.backend.PixelColor;
import fr.epistudio.epysia.render.backend.RenderBackend;
import fr.epistudio.epysia.render.backend.RenderSurface;
import fr.epistudio.epysia.render.backend.RenderTargetDescriptor;
import fr.epistudio.epysia.render.backend.RenderTargetHandle;
import fr.epistudio.epysia.render.backend.ScissorRect;
import fr.epistudio.epysia.render.backend.ShaderSource;
import fr.epistudio.epysia.render.backend.TextureDescriptor;
import fr.epistudio.epysia.render.backend.TextureFormat;
import fr.epistudio.epysia.render.backend.TextureHandle;
import org.lwjgl.glfw.GLFWVulkan;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.MemoryUtil;
import org.lwjgl.vulkan.KHRSurface;
import org.lwjgl.vulkan.KHRSwapchain;
import org.lwjgl.vulkan.VK10;
import org.lwjgl.vulkan.VK13;
import org.lwjgl.vulkan.VkBufferCopy;
import org.lwjgl.vulkan.VkCommandBuffer;
import org.lwjgl.vulkan.VkComputePipelineCreateInfo;
import org.lwjgl.vulkan.VkShaderModuleCreateInfo;

import java.nio.ByteBuffer;
import java.nio.FloatBuffer;
import java.nio.LongBuffer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;

public final class VulkanRenderBackend implements RenderBackend {

    private final AtomicLong nextId = new AtomicLong(1);
    private final Map<Long, VulkanPipeline> pipelines = new HashMap<>();
    private final Map<Long, VulkanMesh> meshes = new HashMap<>();
    private final Map<Long, VulkanBuffer> buffers = new HashMap<>();
    private final Map<Long, VulkanTexture> textures = new HashMap<>();
    private final Map<Long, VulkanRenderTarget> renderTargets = new HashMap<>();
    private final Map<Long, VulkanBindingSet> bindingSets = new HashMap<>();
    private final DrawStatistics drawStatistics = new DrawStatistics();
    private final VulkanGlslRewriter rewriter = new VulkanGlslRewriter();
    private final List<List<Runnable>> pendingDeletions = new ArrayList<>();

    private VulkanInstance instance;
    private VulkanDevice device;
    private VulkanSwapchain swapchain;
    private VulkanFrameRing frameRing;
    private VulkanImmediateCommands immediateCommands;
    private VulkanBufferFactory bufferFactory;
    private VulkanTextureFactory textureFactory;
    private VulkanTextureUploader textureUploader;
    private VulkanDescriptorLayoutCache layoutCache;
    private VulkanDescriptorAllocator descriptorAllocator;
    private VulkanPipelineFactory pipelineFactory;
    private VulkanShaderCompiler shaderCompiler;
    private VulkanPipelineCache pipelineCache;
    private VulkanProfiler profiler;
    private VulkanBindingSetBuilder bindingSetBuilder;

    private long surface = VK10.VK_NULL_HANDLE;
    private long windowHandle;
    private int[] swapchainLayouts = new int[0];
    private int currentImageIndex = -1;
    private boolean frameActive;
    private boolean passActive;
    private int surfaceWidth;
    private int surfaceHeight;
    private RenderingFormats currentFormats = new RenderingFormats(List.of(), 0, 0);
    private int currentTargetHeight;
    private boolean currentTargetIsScreen;
    private Optional<VulkanRenderTarget> currentTarget = Optional.empty();
    private Optional<int[]> pendingViewport = Optional.empty();
    private long boundPipelineVariant = VK10.VK_NULL_HANDLE;
    private long boundBindingSetId = -1L;
    private int lastAllocationCount;
    private int lastFlushCount;
    private int lastStagingCount;
    private int sectionsOutsideFrame;
    private int uniformOverrideSlot = -1;
    private Optional<DynamicBufferBinding> uniformOverride = Optional.empty();

    @Override
    public void initialize(RenderSurface renderSurface) {
        this.windowHandle = renderSurface.nativeWindowHandle();
        this.surfaceWidth = renderSurface.framebufferWidth();
        this.surfaceHeight = renderSurface.framebufferHeight();
        this.instance = new VulkanInstance();
        this.surface = createSurface();
        this.device = new VulkanDevice(instance, surface);
        createDeviceScopedResources(renderSurface.vsyncRequested());
    }

    private void createDeviceScopedResources(boolean vsync) {
        this.swapchain = new VulkanSwapchain(device, surface, vsync, surfaceWidth, surfaceHeight);
        this.swapchainLayouts = new int[swapchain.imageCount()];
        this.frameRing = new VulkanFrameRing(device, swapchain.imageCount());
        this.immediateCommands = new VulkanImmediateCommands(device);
        this.bufferFactory = new VulkanBufferFactory(device);
        this.textureFactory = new VulkanTextureFactory(device);
        this.textureUploader = new VulkanTextureUploader(bufferFactory, immediateCommands,
                this::recordOutsidePass, this::retireStagingBuffer);
        this.layoutCache = new VulkanDescriptorLayoutCache(device);
        this.descriptorAllocator = new VulkanDescriptorAllocator(device);
        this.pipelineCache = new VulkanPipelineCache(device);
        this.pipelineFactory = new VulkanPipelineFactory(device, pipelineCache.handle(),
                VulkanPipelineStatistics.enabled()
                        ? Optional.of(new VulkanPipelineStatistics(device, new ConsoleLogger()))
                        : Optional.empty());
        this.shaderCompiler = new VulkanShaderCompiler(new SpirvDiskCache());
        this.profiler = new VulkanProfiler(device);
        this.bindingSetBuilder = new VulkanBindingSetBuilder(device, descriptorAllocator, textureFactory);
        for (int slot = 0; slot < VulkanFrameRing.FRAMES_IN_FLIGHT; slot++) {
            pendingDeletions.add(new ArrayList<>());
        }
    }

    private long createSurface() {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            LongBuffer created = stack.mallocLong(1);
            VulkanResult.check(GLFWVulkan.glfwCreateWindowSurface(instance.handle(), windowHandle,
                    null, created), "glfwCreateWindowSurface");
            return created.get(0);
        }
    }

    @Override
    public void shutdown() {
        device.waitIdle();
        pendingDeletions.forEach(slot -> slot.forEach(Runnable::run));
        pendingDeletions.clear();
        destroyTrackedResources();
        shaderCompiler.close();
        profiler.close();
        pipelineCache.close();
        descriptorAllocator.close();
        layoutCache.close();
        immediateCommands.close();
        frameRing.close();
        swapchain.close();
        KHRSurface.vkDestroySurfaceKHR(instance.handle(), surface, null);
        device.close();
        instance.close();
    }

    private void destroyTrackedResources() {
        textures.values().forEach(textureFactory::destroy);
        textures.clear();
        buffers.values().forEach(bufferFactory::destroy);
        buffers.clear();
        pipelines.values().forEach(this::destroyPipelineResource);
        pipelines.clear();
        renderTargets.clear();
        bindingSets.values().forEach(this::freeDescriptorSets);
        bindingSets.clear();
        meshes.clear();
    }

    @Override
    public int uniformBufferOffsetAlignment() {
        return Math.max(1, device.limits().uniformBufferOffsetAlignment());
    }

    @Override
    public int perFrameBufferSlots() {
        return VulkanFrameRing.FRAMES_IN_FLIGHT;
    }

    @Override
    public BufferHandle createBuffer(BufferDescriptor descriptor) {
        int slices = descriptor.perFrame() ? VulkanFrameRing.FRAMES_IN_FLIGHT : 1;
        long sliceSize = alignSlice(Math.max(1, descriptor.data().remaining()));
        VulkanBuffer buffer = createBackingStore(descriptor, sliceSize, slices);
        long id = nextId.getAndIncrement();
        buffers.put(id, buffer);
        reportPlacement(descriptor, buffer);
        return new BufferHandle(id);
    }

    private void reportPlacement(BufferDescriptor descriptor, VulkanBuffer buffer) {
        if (System.getProperty("epysia.vulkan.diagnosePlacement") == null
                || buffer.totalByteSize() < 65536L) {
            return;
        }
        new ConsoleLogger().info("[placement] " + descriptor.usage()
                + (descriptor.perFrame() ? " perFrame" : "")
                + " " + (buffer.totalByteSize() / 1024) + "KB -> "
                + bufferFactory.describePlacement(buffer));
    }

    private VulkanBuffer createBackingStore(BufferDescriptor descriptor, long sliceSize, int slices) {
        if (!prefersDeviceLocal(descriptor)) {
            VulkanBuffer mapped = bufferFactory.createMapped(descriptor.usage(), sliceSize, slices);
            writeInitialContents(mapped, descriptor.data(), slices);
            return mapped;
        }
        VulkanBuffer deviceLocal = bufferFactory.createDeviceLocal(descriptor.usage(), sliceSize);
        uploadThroughStaging(deviceLocal, descriptor.data(), 0L);
        return deviceLocal;
    }

    private static boolean prefersDeviceLocal(BufferDescriptor descriptor) {
        return !descriptor.perFrame()
                && (descriptor.usage() == BufferUsage.VERTEX || descriptor.usage() == BufferUsage.INDEX);
    }

    private void uploadThroughStaging(VulkanBuffer destination, ByteBuffer data, long byteOffset) {
        if (data.remaining() == 0) {
            return;
        }
        VulkanBuffer staging = bufferFactory.createReadback(data.remaining());
        MemoryUtil.memCopy(MemoryUtil.memAddress(data), staging.mappedAddress(), data.remaining());
        long byteCount = data.remaining();
        recordOutsidePass(commandBuffer -> recordBufferCopy(commandBuffer, staging, destination,
                byteOffset, byteCount));
        pendingDeletions.get(frameRing.frameSlot()).add(() -> bufferFactory.destroy(staging));
    }

    private static void recordBufferCopy(VkCommandBuffer commandBuffer, VulkanBuffer source,
                                         VulkanBuffer destination, long byteOffset, long byteCount) {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            VkBufferCopy.Buffer region = VkBufferCopy.calloc(1, stack)
                    .srcOffset(0L).dstOffset(byteOffset).size(byteCount);
            VK10.vkCmdCopyBuffer(commandBuffer, source.handle(), destination.handle(), region);
        }
    }

    private long alignSlice(long sliceSize) {
        int alignment = Math.max(device.limits().uniformBufferOffsetAlignment(),
                device.limits().storageBufferOffsetAlignment());
        return ((sliceSize + alignment - 1) / alignment) * alignment;
    }

    private static void writeInitialContents(VulkanBuffer buffer, ByteBuffer data, int slices) {
        if (!buffer.isMapped() || data.remaining() == 0) {
            return;
        }
        for (int slice = 0; slice < slices; slice++) {
            MemoryUtil.memCopy(MemoryUtil.memAddress(data),
                    buffer.mappedAddress() + buffer.sliceByteSize() * slice, data.remaining());
        }
    }

    @Override
    public void writeBuffer(BufferHandle handle, ByteBuffer data, long byteOffset) {
        VulkanBuffer buffer = requireBuffer(handle);
        if (!buffer.isMapped()) {
            uploadThroughStaging(buffer, data, byteOffset);
            return;
        }
        long destination = buffer.mappedAddress() + buffer.sliceOffset(frameRing.frameSlot()) + byteOffset;
        MemoryUtil.memCopy(MemoryUtil.memAddress(data), destination, data.remaining());
    }

    @Override
    public void readBuffer(BufferHandle handle, ByteBuffer destination, long byteOffset) {
        VulkanBuffer buffer = requireBuffer(handle);
        long source = buffer.mappedAddress() + buffer.sliceOffset(frameRing.frameSlot()) + byteOffset;
        MemoryUtil.memCopy(source, MemoryUtil.memAddress(destination), destination.remaining());
    }

    @Override
    public MeshHandle createMesh(MeshDescriptor descriptor) {
        long id = nextId.getAndIncrement();
        meshes.put(id, new VulkanMesh(requireBuffer(descriptor.vertexBuffer()),
                requireBuffer(descriptor.indexBuffer()), descriptor.firstIndex(),
                descriptor.indexCount(), descriptor.indexFormat()));
        return new MeshHandle(id);
    }

    @Override
    public void updateMeshRange(MeshHandle handle, int firstIndex, int indexCount) {
        VulkanMesh existing = requireMesh(handle);
        meshes.put(handle.id(), new VulkanMesh(existing.vertexBuffer(), existing.indexBuffer(),
                firstIndex, indexCount, existing.indexFormat()));
    }

    @Override
    public PipelineHandle createPipeline(PipelineDescriptor descriptor) {
        VulkanPipelineLayout layout = layoutCache.layoutFor(descriptor.bindingLayout());
        VulkanPipeline pipeline = new VulkanPipeline(layout, Optional.of(descriptor.state()),
                Optional.of(descriptor.vertexLayout()), Optional.ofNullable(descriptor.instanceLayout()));
        compileGraphicsModules(pipeline, descriptor.shaders());
        long id = nextId.getAndIncrement();
        pipelines.put(id, pipeline);
        return new PipelineHandle(id);
    }

    private void compileGraphicsModules(VulkanPipeline pipeline, ShaderSource shaders) {
        String vertexSource = rewriter.rewriteVertex(shaders.vertexSource(), shaders.fragmentSource());
        String fragmentSource = rewriter.rewriteFragment(shaders.vertexSource(), shaders.fragmentSource());
        long vertexModule = createShaderModule(shaderCompiler.compile(VulkanShaderStage.VERTEX,
                vertexSource, "vertex"));
        long fragmentModule = createShaderModule(shaderCompiler.compile(VulkanShaderStage.FRAGMENT,
                fragmentSource, "fragment"));
        pipeline.useModules(vertexModule, fragmentModule);
    }

    private long createShaderModule(byte[] spirv) {
        ByteBuffer code = MemoryUtil.memAlloc(spirv.length);
        code.put(spirv).flip();
        try (MemoryStack stack = MemoryStack.stackPush()) {
            VkShaderModuleCreateInfo createInfo =
                    VkShaderModuleCreateInfo.calloc(stack).sType$Default().pCode(code);
            LongBuffer created = stack.mallocLong(1);
            VulkanResult.check(VK10.vkCreateShaderModule(device.handle(), createInfo, null, created),
                    "vkCreateShaderModule");
            return created.get(0);
        } finally {
            MemoryUtil.memFree(code);
        }
    }

    @Override
    public PipelineHandle createComputePipeline(ComputePipelineDescriptor descriptor) {
        VulkanPipelineLayout layout = layoutCache.layoutFor(descriptor.bindingLayout());
        VulkanPipeline pipeline = new VulkanPipeline(layout, Optional.empty(),
                Optional.empty(), Optional.empty());
        byte[] spirv = shaderCompiler.compile(VulkanShaderStage.COMPUTE,
                rewriter.rewriteCompute(descriptor.computeSource()), "compute");
        pipeline.useComputePipeline(createComputePipelineObject(layout, createShaderModule(spirv)));
        long id = nextId.getAndIncrement();
        pipelines.put(id, pipeline);
        return new PipelineHandle(id);
    }

    private long createComputePipelineObject(VulkanPipelineLayout layout, long module) {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            VkComputePipelineCreateInfo.Buffer createInfo =
                    VkComputePipelineCreateInfo.calloc(1, stack)
                            .sType$Default()
                            .layout(layout.pipelineLayout());
            createInfo.stage().sType$Default()
                    .stage(VK10.VK_SHADER_STAGE_COMPUTE_BIT)
                    .module(module)
                    .pName(stack.UTF8("main"));
            LongBuffer created = stack.mallocLong(1);
            VulkanResult.check(VK10.vkCreateComputePipelines(device.handle(), pipelineCache.handle(),
                    createInfo, null, created), "vkCreateComputePipelines");
            return created.get(0);
        }
    }

    @Override
    public TextureHandle createTexture(TextureDescriptor descriptor) {
        VulkanTexture texture = textureFactory.create(descriptor);
        recordOutsidePass(commandBuffer -> VulkanImageBarriers.transition(commandBuffer,
                texture, VK10.VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL));
        long id = nextId.getAndIncrement();
        textures.put(id, texture);
        return new TextureHandle(id);
    }

    @Override
    public void writeTexture(TextureHandle handle, ByteBuffer rgbaPixels) {
        textureUploader.upload(requireTexture(handle), rgbaPixels);
    }

    @Override
    public void writeTexture(TextureHandle handle, FloatBuffer rgbaPixels) {
        ByteBuffer bytes = MemoryUtil.memByteBuffer(MemoryUtil.memAddress(rgbaPixels),
                rgbaPixels.remaining() * Float.BYTES);
        textureUploader.upload(requireTexture(handle), bytes);
    }

    @Override
    public void generateMipmaps(TextureHandle handle) {
        textureUploader.generateMipmaps(requireTexture(handle));
    }

    @Override
    public void copyTextureLayer(TextureHandle source, int sourceLayer,
                                 TextureHandle destination, int destinationLayer) {
        VulkanTexture from = requireTexture(source);
        copyTextureRegion(source, sourceLayer, 0, 0, destination, destinationLayer, 0, 0,
                from.width(), from.height());
    }

    @Override
    public void copyTextureRegion(TextureHandle source, int sourceLayer, int sourceX, int sourceY,
                                  TextureHandle destination, int destinationLayer,
                                  int destinationX, int destinationY, int width, int height) {
        VulkanTexture from = requireTexture(source);
        VulkanTexture to = requireTexture(destination);
        if (from.format() != to.format() || from.width() != to.width() || from.height() != to.height()) {
            throw new EpysiaException("copyTextureRegion requires matching format and dimensions.");
        }
        textureUploader.copyRegion(from, sourceLayer, sourceX, sourceY, to, destinationLayer,
                destinationX, destinationY, width, height);
    }

    @Override
    public RenderTargetHandle createRenderTarget(RenderTargetDescriptor descriptor) {
        List<VulkanTexture> colors = descriptor.colorAttachments().stream()
                .map(this::requireTexture).toList();
        List<Long> colorViews = colors.stream()
                .map(texture -> textureFactory.attachmentView(texture,
                        descriptor.colorMipLevel(), descriptor.colorLayer())).toList();
        Optional<VulkanTexture> depth = descriptor.depthAttachment().map(this::requireTexture);
        Optional<Long> depthView = depth.map(texture ->
                textureFactory.attachmentView(texture, 0, descriptor.depthLayer()));
        long id = nextId.getAndIncrement();
        renderTargets.put(id, new VulkanRenderTarget(descriptor.width(), descriptor.height(),
                colors, colorViews, depth, depthView, formatsOf(colors, depth),
                descriptor.colorLayer(), descriptor.depthLayer()));
        return new RenderTargetHandle(id);
    }

    private static RenderingFormats formatsOf(List<VulkanTexture> colors, Optional<VulkanTexture> depth) {
        List<Integer> colorFormats = colors.stream().map(VulkanTexture::vulkanFormat).toList();
        int depthFormat = depth.map(VulkanTexture::vulkanFormat).orElse(0);
        int stencilFormat = depth
                .filter(texture -> texture.format() == TextureFormat.DEPTH32F_STENCIL8)
                .map(VulkanTexture::vulkanFormat).orElse(0);
        return new RenderingFormats(colorFormats, depthFormat, stencilFormat);
    }

    @Override
    public BindingSetHandle createBindingSet(BindingSetDescriptor descriptor) {
        VulkanBindingSet created = bindingSetBuilder.describe(descriptor, this::requireBuffer,
                this::requireTexture);
        long id = nextId.getAndIncrement();
        bindingSets.put(id, created);
        return new BindingSetHandle(id);
    }

    @Override
    public void setUniformSlotOverride(int slot, BufferHandle buffer, long byteOffset, long byteSize) {
        uniformOverrideSlot = slot;
        uniformOverride = Optional.of(new DynamicBufferBinding(slot, requireBuffer(buffer),
                byteOffset, byteSize));
        boundBindingSetId = -1L;
    }

    @Override
    public void clearUniformSlotOverride() {
        uniformOverrideSlot = -1;
        uniformOverride = Optional.empty();
        boundBindingSetId = -1L;
    }

    @Override
    public void beginFrame() {
        immediateCommands.flushScoped();
        frameRing.waitForSlot();
        runPendingDeletions();
        drawStatistics.reset();
        acquireSwapchainImage();
        frameRing.beginRecording();
        profiler.beginFrame(frameRing.commandBuffer(), frameRing.frameSlot());
        frameActive = true;
        boundPipelineVariant = VK10.VK_NULL_HANDLE;
        boundBindingSetId = -1L;
    }

    private void acquireSwapchainImage() {
        currentImageIndex = swapchain.acquireNextImage(frameRing.imageAvailableSemaphore());
        if (currentImageIndex >= 0) {
            return;
        }
        recreateSwapchain();
        currentImageIndex = swapchain.acquireNextImage(frameRing.imageAvailableSemaphore());
    }

    private void recreateSwapchain() {
        swapchain.recreate(surfaceWidth, surfaceHeight);
        swapchainLayouts = new int[swapchain.imageCount()];
        frameRing.resizePresentSemaphores(swapchain.imageCount());
    }

    private void runPendingDeletions() {
        List<Runnable> slot = pendingDeletions.get(frameRing.frameSlot());
        slot.forEach(Runnable::run);
        slot.clear();
    }

    @Override
    public void endFrame() {
        transitionSwapchainForPresent();
        profiler.endFrame(frameRing.frameSlot());
        frameRing.submit(currentImageIndex, currentImageIndex >= 0);
        presentAcquiredImage();
        frameRing.advance();
        frameActive = false;
        reportValidationMessages();
        reportFrameSyncs();
    }

    private void reportDescriptorChurn() {
        if (System.getProperty("epysia.vulkan.diagnoseDescriptors") == null) {
            return;
        }
        int now = VulkanDescriptorAllocator.diagnosticAllocations();
        new ConsoleLogger().info("[descriptors] allocated this frame=" + (now - lastAllocationCount)
                + " total=" + now + " liveBindingSets=" + bindingSets.size());
        lastAllocationCount = now;
    }

    private void reportFrameSyncs() {
        if (System.getProperty("epysia.vulkan.diagnoseSyncs") == null) {
            return;
        }
        int flushes = VulkanImmediateCommands.diagnosticFlushes();
        int staging = VulkanBufferFactory.diagnosticStagingBuffers();
        new ConsoleLogger().info("[syncs] gpuStalls=" + (flushes - lastFlushCount)
                + " stagingBuffers=" + (staging - lastStagingCount));
        lastFlushCount = flushes;
        lastStagingCount = staging;
    }

    private void reportValidationMessages() {
        List<String> messages = instance.drainValidationMessages();
        if (!messages.isEmpty()) {
            throw new EpysiaException("Vulkan validation:\n" + String.join("\n", messages));
        }
    }

    private void presentAcquiredImage() {
        if (currentImageIndex < 0) {
            return;
        }
        int result = swapchain.present(currentImageIndex,
                frameRing.renderFinishedSemaphore(currentImageIndex));
        if (result != KHRSwapchain.VK_ERROR_OUT_OF_DATE_KHR) {
            return;
        }
        device.waitIdle();
        recreateSwapchain();
    }

    private void transitionSwapchainForPresent() {
        if (currentImageIndex < 0) {
            return;
        }
        VulkanImageBarriers.transitionRaw(currentCommandBuffer(), swapchain.image(currentImageIndex),
                VK10.VK_IMAGE_ASPECT_COLOR_BIT, 1, 1, swapchainLayouts[currentImageIndex],
                KHRSwapchain.VK_IMAGE_LAYOUT_PRESENT_SRC_KHR);
        swapchainLayouts[currentImageIndex] = KHRSwapchain.VK_IMAGE_LAYOUT_PRESENT_SRC_KHR;
    }

    @Override
    public void onViewportResize(int width, int height) {
        if (width <= 0 || height <= 0) {
            return;
        }
        surfaceWidth = width;
        surfaceHeight = height;
        device.waitIdle();
        recreateSwapchain();
    }

    @Override
    public void setPassViewport(int x, int y, int width, int height) {
        pendingViewport = width <= 0 || height <= 0
                ? Optional.empty() : Optional.of(new int[]{x, y, width, height});
    }

    @Override
    public void beginPass(RenderTargetHandle target, PassClear clear) {
        VulkanPassRecorder recorder = new VulkanPassRecorder(currentCommandBuffer());
        currentTargetIsScreen = target.id() == RenderTargetHandle.SCREEN.id();
        if (currentTargetIsScreen) {
            beginScreenPass(recorder, clear);
        } else {
            beginOffscreenPass(recorder, requireRenderTarget(target), clear);
        }
        passActive = true;
        drawStatistics.recordPass();
        boundPipelineVariant = VK10.VK_NULL_HANDLE;
        boundBindingSetId = -1L;
    }

    private void beginScreenPass(VulkanPassRecorder recorder, PassClear clear) {
        currentTarget = Optional.empty();
        currentFormats = new RenderingFormats(List.of(swapchain.format()), 0, 0);
        currentTargetHeight = swapchain.height();
        prepareSwapchainAttachment();
        recorder.beginScreen(swapchain.imageView(currentImageIndex), swapchain.width(),
                swapchain.height(), clear);
        applyViewportAndScissor(swapchain.width(), swapchain.height(), true);
    }

    private void prepareSwapchainAttachment() {
        VulkanImageBarriers.transitionRaw(currentCommandBuffer(), swapchain.image(currentImageIndex),
                VK10.VK_IMAGE_ASPECT_COLOR_BIT, 1, 1, VK10.VK_IMAGE_LAYOUT_UNDEFINED,
                VK10.VK_IMAGE_LAYOUT_COLOR_ATTACHMENT_OPTIMAL);
        swapchainLayouts[currentImageIndex] = VK10.VK_IMAGE_LAYOUT_COLOR_ATTACHMENT_OPTIMAL;
    }

    private void acquireAttachment(VulkanTexture texture, int layer, int layout, boolean discards) {
        if (discards) {
            VulkanImageBarriers.discardInto(currentCommandBuffer(), texture, layer, layout);
            return;
        }
        VulkanImageBarriers.transitionLayer(currentCommandBuffer(), texture, layer, layout);
    }

    private void beginOffscreenPass(VulkanPassRecorder recorder, VulkanRenderTarget target,
                                    PassClear clear) {
        currentTarget = Optional.of(target);
        currentFormats = target.formats();
        currentTargetHeight = target.height();
        target.colorAttachments().forEach(texture -> acquireAttachment(texture, target.colorLayer(),
                VK10.VK_IMAGE_LAYOUT_COLOR_ATTACHMENT_OPTIMAL, clear.clearColor()));
        target.depthAttachment().ifPresent(texture -> acquireAttachment(texture, target.depthLayer(),
                VK10.VK_IMAGE_LAYOUT_DEPTH_STENCIL_ATTACHMENT_OPTIMAL, clear.clearDepth()));
        recorder.beginOffscreen(target, clear);
        applyViewportAndScissor(target.width(), target.height(), false);
    }

    private void applyViewportAndScissor(int targetWidth, int targetHeight, boolean flipVertically) {
        int[] viewport = pendingViewport.orElse(new int[]{0, 0, targetWidth, targetHeight});
        pendingViewport = Optional.empty();
        new VulkanPassRecorder(currentCommandBuffer())
                .setViewport(viewport[0], viewport[1], viewport[2], viewport[3],
                        targetHeight, flipVertically);
        new VulkanPassRecorder(currentCommandBuffer())
                .setScissor(0, 0, targetWidth, targetHeight);
    }

    @Override
    public void endPass() {
        if (!passActive) {
            return;
        }
        VK13.vkCmdEndRendering(currentCommandBuffer());
        currentTarget.ifPresent(this::releaseAttachments);
        passActive = false;
        flushOutsideFrameWork();
    }

    private void releaseAttachments(VulkanRenderTarget target) {
        target.colorAttachments().forEach(texture -> VulkanImageBarriers.transitionLayer(
                currentCommandBuffer(), texture, target.colorLayer(),
                VK10.VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL));
        target.depthAttachment().ifPresent(texture -> VulkanImageBarriers.transitionLayer(
                currentCommandBuffer(), texture, target.depthLayer(),
                VK10.VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL));
    }

    @Override
    public void execute(DrawCommand command) {
        VulkanPipeline pipeline = pipelines.get(command.pipeline().id());
        VulkanMesh mesh = meshes.get(command.mesh().id());
        if (pipeline == null || mesh == null) {
            drawStatistics.recordDroppedDraw();
            return;
        }
        VkCommandBuffer commandBuffer = currentCommandBuffer();
        bindGraphicsPipeline(commandBuffer, pipeline);
        bindDescriptorSets(commandBuffer, pipeline, command.bindings(),
                VK10.VK_PIPELINE_BIND_POINT_GRAPHICS);
        bindGeometry(commandBuffer, pipeline, mesh, command);
        applyScissor(commandBuffer, command.scissor());
        new VulkanDrawRecorder(commandBuffer, drawStatistics)
                .record(command, pipeline, mesh, this::requireBuffer);
    }

    private void bindGraphicsPipeline(VkCommandBuffer commandBuffer, VulkanPipeline pipeline) {
        long variant = pipeline.graphicsVariants().computeIfAbsent(currentFormats,
                formats -> pipelineFactory.createGraphicsVariant(pipeline, formats));
        if (variant == boundPipelineVariant) {
            return;
        }
        VK10.vkCmdBindPipeline(commandBuffer, VK10.VK_PIPELINE_BIND_POINT_GRAPHICS, variant);
        boundPipelineVariant = variant;
        drawStatistics.recordPipelineSwitch();
    }

    private void bindGeometry(VkCommandBuffer commandBuffer, VulkanPipeline pipeline,
                              VulkanMesh mesh, DrawCommand command) {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            VK10.vkCmdBindVertexBuffers(commandBuffer, 0,
                    stack.longs(mesh.vertexBuffer().handle()), stack.longs(0L));
            bindInstanceBuffer(commandBuffer, pipeline, command, stack);
            VK10.vkCmdBindIndexBuffer(commandBuffer, mesh.indexBuffer().handle(), 0L,
                    VulkanFormats.of(mesh.indexFormat()));
        }
    }

    private void bindInstanceBuffer(VkCommandBuffer commandBuffer, VulkanPipeline pipeline,
                                    DrawCommand command, MemoryStack stack) {
        if (command.instanceBuffer() == null || pipeline.instanceStride() <= 0) {
            return;
        }
        VulkanBuffer instances = requireBuffer(command.instanceBuffer());
        VK10.vkCmdBindVertexBuffers(commandBuffer, 1, stack.longs(instances.handle()),
                stack.longs(instances.sliceOffset(frameRing.frameSlot())));
    }

    private void applyScissor(VkCommandBuffer commandBuffer, ScissorRect scissor) {
        VulkanPassRecorder recorder = new VulkanPassRecorder(commandBuffer);
        if (!scissor.enabled()) {
            recorder.setScissor(0, 0, currentPassWidth(), currentTargetHeight);
            return;
        }
        recorder.setScissor(scissor.x(), currentTargetHeight - scissor.y() - scissor.height(),
                scissor.width(), scissor.height());
    }

    private int currentPassWidth() {
        return currentTarget.map(VulkanRenderTarget::width).orElse(swapchain.width());
    }

    private void bindDescriptorSets(VkCommandBuffer commandBuffer, VulkanPipeline pipeline,
                                    BindingSetHandle handle, int bindPoint) {
        if (handle.id() == BindingSetHandle.EMPTY.id()) {
            return;
        }
        VulkanBindingSet set = requireBindingSet(handle);
        new VulkanDescriptorBinder(commandBuffer, frameRing.frameSlot(), uniformOverrideSlot,
                uniformOverride).bind(pipeline.layout(), resolveFor(set, pipeline.layout()), bindPoint);
        if (handle.id() != boundBindingSetId) {
            boundBindingSetId = handle.id();
            drawStatistics.recordBindingSetSwitch();
        }
    }

    @Override
    public void dispatchCompute(ComputeDispatch dispatch) {
        requireOutsidePass("dispatchCompute");
        VulkanPipeline pipeline = requirePipeline(dispatch.pipeline());
        VkCommandBuffer commandBuffer = currentCommandBuffer();
        prepareComputeResources(commandBuffer, dispatch.bindings());
        VK10.vkCmdBindPipeline(commandBuffer, VK10.VK_PIPELINE_BIND_POINT_COMPUTE,
                pipeline.computePipeline());
        bindDescriptorSets(commandBuffer, pipeline, dispatch.bindings(),
                VK10.VK_PIPELINE_BIND_POINT_COMPUTE);
        VK10.vkCmdDispatch(commandBuffer, dispatch.groupCountX(), dispatch.groupCountY(),
                dispatch.groupCountZ());
        boundPipelineVariant = VK10.VK_NULL_HANDLE;
        boundBindingSetId = -1L;
    }

    private void prepareComputeResources(VkCommandBuffer commandBuffer, BindingSetHandle handle) {
        if (handle.id() == BindingSetHandle.EMPTY.id()) {
            return;
        }
        VulkanBindingSet set = requireBindingSet(handle);
        set.storageImages().forEach(texture -> VulkanImageBarriers.transition(commandBuffer,
                texture, VK10.VK_IMAGE_LAYOUT_GENERAL));
        set.sampledTextures().forEach(texture -> VulkanImageBarriers.transition(commandBuffer,
                texture, VK10.VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL));
    }

    @Override
    public void computeBarrier(ComputeBarrier barrier) {
        requireOutsidePass("computeBarrier");
        new VulkanPassRecorder(currentCommandBuffer()).memoryBarrier();
    }

    private void retireStagingBuffer(VulkanBuffer staging) {
        pendingDeletions.get(frameRing.frameSlot()).add(() -> bufferFactory.destroy(staging));
    }

    private void recordOutsidePass(Consumer<VkCommandBuffer> recorder) {
        recorder.accept(currentCommandBuffer());
    }

    private VkCommandBuffer currentCommandBuffer() {
        return frameActive ? frameRing.commandBuffer() : immediateCommands.beginScoped();
    }

    private void flushOutsideFrameWork() {
        if (!frameActive) {
            immediateCommands.flushScoped();
        }
    }

    private void requireOutsidePass(String operation) {
        if (passActive) {
            throw new EpysiaException(operation + " is not allowed inside a render pass under Vulkan.");
        }
    }

    @Override
    public void beginProfileSection(String name) {
        if (!frameActive) {
            sectionsOutsideFrame++;
            return;
        }
        profiler.beginSection(frameRing.commandBuffer(), frameRing.frameSlot(), name);
    }

    @Override
    public void endProfileSection() {
        if (sectionsOutsideFrame > 0) {
            sectionsOutsideFrame--;
            return;
        }
        profiler.endSection(frameRing.commandBuffer(), frameRing.frameSlot());
    }

    @Override
    public Map<String, Long> latestProfileTimingsNanos() {
        return profiler.latestTimings();
    }

    @Override
    public DrawStatistics drawStatistics() {
        return drawStatistics;
    }

    @Override
    public void updatePipelineShaders(PipelineHandle handle, ShaderSource shaders) {
        VulkanPipeline pipeline = requirePipeline(handle);
        deferDestruction(collectVariants(pipeline));
        pipeline.graphicsVariants().clear();
        compileGraphicsModules(pipeline, shaders);
        boundPipelineVariant = VK10.VK_NULL_HANDLE;
    }

    private List<Long> collectVariants(VulkanPipeline pipeline) {
        return List.copyOf(pipeline.graphicsVariants().values());
    }

    private void deferDestruction(List<Long> variants) {
        pendingDeletions.get(frameRing.frameSlot()).add(() ->
                variants.forEach(variant -> VK10.vkDestroyPipeline(device.handle(), variant, null)));
    }

    @Override
    public int meshIndexCount(MeshHandle handle) {
        return requireMesh(handle).indexCount();
    }

    @Override
    public int meshFirstIndex(MeshHandle handle) {
        return requireMesh(handle).firstIndex();
    }

    @Override
    public IndexFormat meshIndexFormat(MeshHandle handle) {
        return requireMesh(handle).indexFormat();
    }

    @Override
    public int textureWidth(TextureHandle handle) {
        return requireTexture(handle).width();
    }

    @Override
    public int textureHeight(TextureHandle handle) {
        return requireTexture(handle).height();
    }

    @Override
    public boolean isAlive(MeshHandle handle) {
        return meshes.containsKey(handle.id());
    }

    @Override
    public boolean isAlive(PipelineHandle handle) {
        return pipelines.containsKey(handle.id());
    }

    @Override
    public void destroy(PipelineHandle handle) {
        VulkanPipeline pipeline = pipelines.remove(handle.id());
        if (pipeline != null) {
            pendingDeletions.get(frameRing.frameSlot()).add(() -> destroyPipelineResource(pipeline));
        }
    }

    private void destroyPipelineResource(VulkanPipeline pipeline) {
        pipeline.graphicsVariants().values()
                .forEach(variant -> VK10.vkDestroyPipeline(device.handle(), variant, null));
        pipeline.graphicsVariants().clear();
        if (pipeline.isCompute()) {
            VK10.vkDestroyPipeline(device.handle(), pipeline.computePipeline(), null);
        }
        VK10.vkDestroyShaderModule(device.handle(), pipeline.vertexModule(), null);
        VK10.vkDestroyShaderModule(device.handle(), pipeline.fragmentModule(), null);
    }

    @Override
    public void destroy(MeshHandle handle) {
        meshes.remove(handle.id());
    }

    @Override
    public void destroy(BufferHandle handle) {
        VulkanBuffer buffer = buffers.remove(handle.id());
        if (buffer != null) {
            pendingDeletions.get(frameRing.frameSlot()).add(() -> bufferFactory.destroy(buffer));
        }
    }

    @Override
    public void destroy(TextureHandle handle) {
        VulkanTexture texture = textures.remove(handle.id());
        if (texture != null) {
            pendingDeletions.get(frameRing.frameSlot()).add(() -> textureFactory.destroy(texture));
        }
    }

    @Override
    public void destroy(RenderTargetHandle handle) {
        renderTargets.remove(handle.id());
    }

    @Override
    public void destroy(BindingSetHandle handle) {
        VulkanBindingSet set = bindingSets.remove(handle.id());
        if (set != null) {
            pendingDeletions.get(frameRing.frameSlot()).add(() -> freeDescriptorSets(set));
        }
    }

    private VulkanDescriptorSets resolveFor(VulkanBindingSet set, VulkanPipelineLayout layout) {
        return set.resolved().computeIfAbsent(layout,
                pipelineLayout -> bindingSetBuilder.resolveFor(pipelineLayout, set.bindings()));
    }

    private void freeDescriptorSets(VulkanBindingSet set) {
        set.resolved().values().forEach(this::freeResolvedSets);
        set.resolved().clear();
    }

    private void freeResolvedSets(VulkanDescriptorSets resolved) {
        for (int index = 0; index < resolved.descriptorSets().length; index++) {
            descriptorAllocator.free(resolved.owningPools()[index], resolved.descriptorSets()[index]);
        }
    }

    @Override
    public void readTextureLevel(TextureHandle handle, int mipLevel, FloatBuffer destination) {
        VulkanTexture texture = requireTexture(handle);
        new VulkanReadback(device, bufferFactory, immediateCommands)
                .readTexture(texture, mipLevel, destination);
    }

    @Override
    public int readPixelArgb(RenderTargetHandle target, int x, int y) {
        ByteBuffer pixel = MemoryUtil.memAlloc(4);
        try {
            readPixelsRgba(target, x, y, 1, 1, pixel);
            return ((pixel.get(3) & 0xFF) << 24) | ((pixel.get(0) & 0xFF) << 16)
                    | ((pixel.get(1) & 0xFF) << 8) | (pixel.get(2) & 0xFF);
        } finally {
            MemoryUtil.memFree(pixel);
        }
    }

    @Override
    public PixelColor readPixelFloat(RenderTargetHandle target, int x, int y) {
        VulkanTexture texture = firstColorAttachment(target);
        return new VulkanReadback(device, bufferFactory, immediateCommands)
                .readPixelFloat(texture, x, y);
    }

    @Override
    public void readPixelsRgba(RenderTargetHandle target, int x, int y, int width, int height,
                               ByteBuffer destination) {
        VulkanTexture texture = firstColorAttachment(target);
        new VulkanReadback(device, bufferFactory, immediateCommands)
                .readPixels(texture, x, y, width, height, destination);
    }

    private VulkanTexture firstColorAttachment(RenderTargetHandle target) {
        return requireRenderTarget(target).colorAttachments().stream().findFirst()
                .orElseThrow(() -> new EpysiaException("Render target has no colour attachment to read."));
    }

    private VulkanBuffer requireBuffer(BufferHandle handle) {
        VulkanBuffer buffer = buffers.get(handle.id());
        if (buffer == null) {
            throw new EpysiaException("Unknown buffer handle: " + handle.id());
        }
        return buffer;
    }

    private VulkanMesh requireMesh(MeshHandle handle) {
        VulkanMesh mesh = meshes.get(handle.id());
        if (mesh == null) {
            throw new EpysiaException("Unknown mesh handle: " + handle.id());
        }
        return mesh;
    }

    private VulkanPipeline requirePipeline(PipelineHandle handle) {
        VulkanPipeline pipeline = pipelines.get(handle.id());
        if (pipeline == null) {
            throw new EpysiaException("Unknown pipeline handle: " + handle.id());
        }
        return pipeline;
    }

    private VulkanTexture requireTexture(TextureHandle handle) {
        VulkanTexture texture = textures.get(handle.id());
        if (texture == null) {
            throw new EpysiaException("Unknown texture handle: " + handle.id());
        }
        return texture;
    }

    private VulkanRenderTarget requireRenderTarget(RenderTargetHandle handle) {
        VulkanRenderTarget target = renderTargets.get(handle.id());
        if (target == null) {
            throw new EpysiaException("Unknown render target handle: " + handle.id());
        }
        return target;
    }

    private VulkanBindingSet requireBindingSet(BindingSetHandle handle) {
        VulkanBindingSet set = bindingSets.get(handle.id());
        if (set == null) {
            throw new EpysiaException("Unknown binding set handle: " + handle.id());
        }
        return set;
    }

    public String deviceName() {
        return device.deviceName();
    }
}
