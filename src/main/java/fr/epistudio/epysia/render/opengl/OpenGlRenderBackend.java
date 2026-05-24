package fr.epistudio.epysia.render.opengl;

import fr.epistudio.epysia.render.backend.*;

import fr.epistudio.epysia.exceptions.EpysiaException;
import org.lwjgl.opengl.GL;
import org.lwjgl.opengl.GLCapabilities;
import org.lwjgl.opengl.GLDebugMessageCallback;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

import static org.lwjgl.opengl.GL11.GL_BACK;
import static org.lwjgl.opengl.GL11.GL_BLEND;
import static org.lwjgl.opengl.GL11.GL_COLOR_BUFFER_BIT;
import static org.lwjgl.opengl.GL11.GL_CULL_FACE;
import static org.lwjgl.opengl.GL11.GL_DEPTH_BUFFER_BIT;
import static org.lwjgl.opengl.GL11.GL_DEPTH_TEST;
import static org.lwjgl.opengl.GL11.GL_FALSE;
import static org.lwjgl.opengl.GL11.GL_FLOAT;
import static org.lwjgl.opengl.GL11.GL_FRONT;
import static org.lwjgl.opengl.GL11.GL_LEQUAL;
import static org.lwjgl.opengl.GL11.GL_LESS;
import static org.lwjgl.opengl.GL11.GL_LINEAR;
import static org.lwjgl.opengl.GL11.GL_NEAREST;
import static org.lwjgl.opengl.GL11.GL_LINES;
import static org.lwjgl.opengl.GL11.GL_NONE;
import static org.lwjgl.opengl.GL11.GL_ONE_MINUS_SRC_ALPHA;
import static org.lwjgl.opengl.GL11.GL_RGBA;
import static org.lwjgl.opengl.GL11.GL_RGBA8;
import static org.lwjgl.opengl.GL21.GL_SRGB8_ALPHA8;
import static org.lwjgl.opengl.GL11.GL_SRC_ALPHA;
import static org.lwjgl.opengl.GL11.GL_TEXTURE_2D;
import static org.lwjgl.opengl.GL11.GL_TEXTURE_MAG_FILTER;
import static org.lwjgl.opengl.GL11.GL_TEXTURE_MIN_FILTER;
import static org.lwjgl.opengl.GL11.GL_TEXTURE_WRAP_S;
import static org.lwjgl.opengl.GL11.GL_TEXTURE_WRAP_T;
import static org.lwjgl.opengl.GL11.GL_TRIANGLES;
import static org.lwjgl.opengl.GL11.GL_TRIANGLE_STRIP;
import static org.lwjgl.opengl.GL11.GL_UNSIGNED_BYTE;
import static org.lwjgl.opengl.GL11.GL_UNSIGNED_INT;
import static org.lwjgl.opengl.GL11.GL_UNSIGNED_SHORT;
import static org.lwjgl.opengl.GL11.GL_VERSION;
import static org.lwjgl.opengl.GL11.glBindTexture;
import static org.lwjgl.opengl.GL11.glClear;
import static org.lwjgl.opengl.GL11.glClearColor;
import static org.lwjgl.opengl.GL11.glClearDepth;
import static org.lwjgl.opengl.GL11.glCullFace;
import static org.lwjgl.opengl.GL11.glDeleteTextures;
import static org.lwjgl.opengl.GL11.glDepthFunc;
import static org.lwjgl.opengl.GL11.glDisable;
import static org.lwjgl.opengl.GL11.glDrawElements;
import static org.lwjgl.opengl.GL11.glEnable;
import static org.lwjgl.opengl.GL11.glGenTextures;
import static org.lwjgl.opengl.GL11.glGetString;
import static org.lwjgl.opengl.GL11.glTexImage2D;
import static org.lwjgl.opengl.GL11.glTexParameteri;
import static org.lwjgl.opengl.GL11.glTexSubImage2D;
import static org.lwjgl.opengl.GL11.glViewport;
import static org.lwjgl.opengl.GL12.GL_CLAMP_TO_EDGE;
import static org.lwjgl.opengl.GL13.GL_TEXTURE0;
import static org.lwjgl.opengl.GL13.glActiveTexture;
import static org.lwjgl.opengl.GL14.glBlendFuncSeparate;
import static org.lwjgl.opengl.GL30.GL_DEPTH_COMPONENT32F;
import static org.lwjgl.opengl.GL15.GL_ARRAY_BUFFER;
import static org.lwjgl.opengl.GL15.GL_DYNAMIC_DRAW;
import static org.lwjgl.opengl.GL15.GL_ELEMENT_ARRAY_BUFFER;
import static org.lwjgl.opengl.GL15.GL_STATIC_DRAW;
import static org.lwjgl.opengl.GL15.GL_QUERY_RESULT;
import static org.lwjgl.opengl.GL15.glBeginQuery;
import static org.lwjgl.opengl.GL15.glBindBuffer;
import static org.lwjgl.opengl.GL15.glDeleteQueries;
import static org.lwjgl.opengl.GL15.glEndQuery;
import static org.lwjgl.opengl.GL15.glGenQueries;
import static org.lwjgl.opengl.GL33.GL_TIME_ELAPSED;
import static org.lwjgl.opengl.GL33.glGetQueryObjectui64;
import static org.lwjgl.opengl.GL15.glBufferData;
import static org.lwjgl.opengl.GL15.glBufferSubData;
import static org.lwjgl.opengl.GL15.glDeleteBuffers;
import static org.lwjgl.opengl.GL15.glGenBuffers;
import static org.lwjgl.opengl.GL20.GL_COMPILE_STATUS;
import static org.lwjgl.opengl.GL20.GL_FRAGMENT_SHADER;
import static org.lwjgl.opengl.GL20.GL_LINK_STATUS;
import static org.lwjgl.opengl.GL20.GL_VERTEX_SHADER;
import static org.lwjgl.opengl.GL20.glAttachShader;
import static org.lwjgl.opengl.GL20.glCompileShader;
import static org.lwjgl.opengl.GL20.glCreateProgram;
import static org.lwjgl.opengl.GL20.glCreateShader;
import static org.lwjgl.opengl.GL20.glDeleteProgram;
import static org.lwjgl.opengl.GL20.glDeleteShader;
import static org.lwjgl.opengl.GL20.glDetachShader;
import static org.lwjgl.opengl.GL20.glEnableVertexAttribArray;
import static org.lwjgl.opengl.GL20.glGetProgramInfoLog;
import static org.lwjgl.opengl.GL20.glGetProgrami;
import static org.lwjgl.opengl.GL20.glGetShaderInfoLog;
import static org.lwjgl.opengl.GL20.glGetShaderi;
import static org.lwjgl.opengl.GL20.glLinkProgram;
import static org.lwjgl.opengl.GL20.glShaderSource;
import static org.lwjgl.opengl.GL20.glUseProgram;
import static org.lwjgl.opengl.GL30.GL_COLOR_ATTACHMENT0;
import static org.lwjgl.opengl.GL30.GL_DEPTH_ATTACHMENT;
import static org.lwjgl.opengl.GL30.GL_DEPTH_COMPONENT;
import static org.lwjgl.opengl.GL30.GL_FRAMEBUFFER;
import static org.lwjgl.opengl.GL30.GL_FRAMEBUFFER_COMPLETE;
import static org.lwjgl.opengl.GL30.glBindFramebuffer;
import static org.lwjgl.opengl.GL30.glBindVertexArray;
import static org.lwjgl.opengl.GL30.glCheckFramebufferStatus;
import static org.lwjgl.opengl.GL30.glDeleteFramebuffers;
import static org.lwjgl.opengl.GL30.glDeleteVertexArrays;
import static org.lwjgl.opengl.GL30.glFramebufferTexture2D;
import static org.lwjgl.opengl.GL30.glGenFramebuffers;
import static org.lwjgl.opengl.GL30.glGenVertexArrays;
import static org.lwjgl.opengl.GL11.glDrawBuffer;
import static org.lwjgl.opengl.GL11.glReadBuffer;
import static org.lwjgl.opengl.GL30.GL_TEXTURE_COMPARE_MODE;
import static org.lwjgl.opengl.GL30.GL_TEXTURE_COMPARE_FUNC;
import static org.lwjgl.opengl.GL30.GL_COMPARE_REF_TO_TEXTURE;
import static org.lwjgl.opengl.GL31.GL_UNIFORM_BUFFER;
import static org.lwjgl.opengl.GL30.glBindBufferRange;
import static org.lwjgl.opengl.GL43.GL_DEBUG_OUTPUT;
import static org.lwjgl.opengl.GL43.GL_DEBUG_OUTPUT_SYNCHRONOUS;
import static org.lwjgl.opengl.GL43.GL_DEBUG_SEVERITY_HIGH;
import static org.lwjgl.opengl.GL43.glBindVertexBuffer;
import static org.lwjgl.opengl.GL43.glDebugMessageCallback;
import static org.lwjgl.opengl.GL43.glVertexAttribBinding;
import static org.lwjgl.opengl.GL43.glVertexAttribFormat;

public final class OpenGlRenderBackend implements RenderBackend {

    private static final int VERTEX_BINDING_INDEX = 0;

    private final AtomicLong nextId = new AtomicLong(1);
    private final Map<Long, PipelineResource> pipelines = new HashMap<>();
    private final Map<Long, MeshResource> meshes = new HashMap<>();
    private final Map<Long, BufferResource> buffers = new HashMap<>();
    private final Map<Long, TextureResource> textures = new HashMap<>();
    private final Map<Long, RenderTargetResource> renderTargets = new HashMap<>();
    private final Map<Long, BindingSetResource> bindingSets = new HashMap<>();

    private PipelineResource currentPipeline;
    private long currentBindingSetId;
    private int currentVertexBufferId;
    private int currentIndexBufferId;
    private GLDebugMessageCallback debugCallback;
    private int screenWidth;
    private int screenHeight;

    private static final int PROFILE_FRAME_LAG = 2;
    private static final int MAX_PROFILE_SECTIONS = 16;
    private final int[][] profileQueries = new int[PROFILE_FRAME_LAG][MAX_PROFILE_SECTIONS];
    private final String[][] profileSectionNames = new String[PROFILE_FRAME_LAG][MAX_PROFILE_SECTIONS];
    private final int[] profileSectionCounts = new int[PROFILE_FRAME_LAG];
    private final Map<String, Long> latestTimings = new LinkedHashMap<>();
    private int profileFrameSlot;
    private int currentProfileSection;
    private boolean profileSectionActive;
    private boolean profileQueriesAllocated;

    @Override
    public void initialize(RenderSurface surface) {
        GLCapabilities capabilities = GL.createCapabilities();
        verifyMinimumGlVersion();
        installDebugCallback(capabilities);
        screenWidth = surface.framebufferWidth();
        screenHeight = surface.framebufferHeight();
        glViewport(0, 0, screenWidth, screenHeight);
        allocateProfileQueries();
    }

    private void allocateProfileQueries() {
        for (int frame = 0; frame < PROFILE_FRAME_LAG; frame++) {
            for (int section = 0; section < MAX_PROFILE_SECTIONS; section++) {
                profileQueries[frame][section] = glGenQueries();
            }
        }
        profileQueriesAllocated = true;
    }

    private void verifyMinimumGlVersion() {
        String version = glGetString(GL_VERSION);
        if (version == null) {
            throw new EpysiaException("Could not read GL_VERSION.");
        }
        if (!version.matches("^([4-9]|[1-9][0-9]+)\\..*")) {
            throw new EpysiaException("Epysia requires OpenGL 4.3 or newer; got: " + version);
        }
    }

    private void installDebugCallback(GLCapabilities capabilities) {
        if (!capabilities.OpenGL43) {
            return;
        }
        glEnable(GL_DEBUG_OUTPUT);
        glEnable(GL_DEBUG_OUTPUT_SYNCHRONOUS);
        debugCallback = GLDebugMessageCallback.create((source, type, id, severity, length, message, userParam) -> {
            if (severity == GL_DEBUG_SEVERITY_HIGH) {
                throw new EpysiaException("OpenGL error: " + GLDebugMessageCallback.getMessage(length, message));
            }
        });
        glDebugMessageCallback(debugCallback, 0L);
    }

    @Override
    public void shutdown() {
        renderTargets.values().forEach(target -> glDeleteFramebuffers(target.fboId()));
        renderTargets.clear();
        textures.values().forEach(texture -> glDeleteTextures(texture.textureId()));
        textures.clear();
        bindingSets.clear();
        pipelines.values().forEach(this::deletePipelineResource);
        pipelines.clear();
        meshes.clear();
        buffers.values().forEach(buffer -> glDeleteBuffers(buffer.bufferId()));
        buffers.clear();
        if (debugCallback != null) {
            debugCallback.free();
            debugCallback = null;
        }
        if (profileQueriesAllocated) {
            for (int frame = 0; frame < PROFILE_FRAME_LAG; frame++) {
                for (int section = 0; section < MAX_PROFILE_SECTIONS; section++) {
                    glDeleteQueries(profileQueries[frame][section]);
                }
            }
            profileQueriesAllocated = false;
        }
    }

    @Override
    public BufferHandle createBuffer(BufferDescriptor descriptor) {
        int glTarget = glTargetFor(descriptor.usage());
        int glUsage = descriptor.usage() == BufferUsage.UNIFORM ? GL_DYNAMIC_DRAW : GL_STATIC_DRAW;
        int bufferId = glGenBuffers();
        glBindBuffer(glTarget, bufferId);
        glBufferData(glTarget, descriptor.data(), glUsage);
        long id = nextId.getAndIncrement();
        buffers.put(id, new BufferResource(bufferId, glTarget));
        return new BufferHandle(id);
    }

    @Override
    public MeshHandle createMesh(MeshDescriptor descriptor) {
        BufferResource vertex = requireBuffer(descriptor.vertexBuffer());
        BufferResource index = requireBuffer(descriptor.indexBuffer());
        long id = nextId.getAndIncrement();
        meshes.put(id, new MeshResource(
                vertex.bufferId(),
                index.bufferId(),
                descriptor.firstIndex(),
                descriptor.indexCount(),
                descriptor.indexFormat()
        ));
        return new MeshHandle(id);
    }

    @Override
    public PipelineHandle createPipeline(PipelineDescriptor descriptor) {
        int program = compileProgram(descriptor.shaders());
        int vao = createVertexArrayObject(descriptor.vertexLayout());
        long id = nextId.getAndIncrement();
        pipelines.put(id, new PipelineResource(
                program,
                vao,
                descriptor.state(),
                descriptor.vertexLayout().byteStride()
        ));
        return new PipelineHandle(id);
    }

    private int createVertexArrayObject(VertexLayout layout) {
        int vao = glGenVertexArrays();
        glBindVertexArray(vao);
        for (VertexAttribute attribute : layout.attributes()) {
            glEnableVertexAttribArray(attribute.location());
            glVertexAttribFormat(
                    attribute.location(),
                    attribute.format().componentCount(),
                    GL_FLOAT,
                    false,
                    attribute.byteOffset()
            );
            glVertexAttribBinding(attribute.location(), VERTEX_BINDING_INDEX);
        }
        glBindVertexArray(0);
        return vao;
    }

    private int compileProgram(ShaderSource source) {
        int vertex = compileShader(GL_VERTEX_SHADER, source.vertexSource());
        int fragment = compileShader(GL_FRAGMENT_SHADER, source.fragmentSource());
        int program = glCreateProgram();
        glAttachShader(program, vertex);
        glAttachShader(program, fragment);
        glLinkProgram(program);
        checkProgramLink(program);
        glDetachShader(program, vertex);
        glDetachShader(program, fragment);
        glDeleteShader(vertex);
        glDeleteShader(fragment);
        return program;
    }

    private int compileShader(int shaderType, String source) {
        int shader = glCreateShader(shaderType);
        glShaderSource(shader, source);
        glCompileShader(shader);
        if (glGetShaderi(shader, GL_COMPILE_STATUS) == GL_FALSE) {
            String log = glGetShaderInfoLog(shader);
            glDeleteShader(shader);
            throw new EpysiaException("Shader compile failed: " + log);
        }
        return shader;
    }

    private void checkProgramLink(int program) {
        if (glGetProgrami(program, GL_LINK_STATUS) == GL_FALSE) {
            String log = glGetProgramInfoLog(program);
            glDeleteProgram(program);
            throw new EpysiaException("Shader program link failed: " + log);
        }
    }

    @Override
    public TextureHandle createTexture(TextureDescriptor descriptor) {
        int textureId = glGenTextures();
        glBindTexture(GL_TEXTURE_2D, textureId);
        configureTextureStorage(descriptor);
        configureTextureSamplerState(descriptor);
        long id = nextId.getAndIncrement();
        textures.put(id, new TextureResource(textureId, descriptor.width(), descriptor.height(), descriptor.format()));
        return new TextureHandle(id);
    }

    private void configureTextureStorage(TextureDescriptor descriptor) {
        switch (descriptor.format()) {
            case RGBA8 -> glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA8, descriptor.width(), descriptor.height(),
                    0, GL_RGBA, GL_UNSIGNED_BYTE, (ByteBuffer) null);
            case SRGB8_ALPHA8 -> glTexImage2D(GL_TEXTURE_2D, 0, GL_SRGB8_ALPHA8, descriptor.width(), descriptor.height(),
                    0, GL_RGBA, GL_UNSIGNED_BYTE, (ByteBuffer) null);
            case DEPTH32F -> glTexImage2D(GL_TEXTURE_2D, 0, GL_DEPTH_COMPONENT32F, descriptor.width(), descriptor.height(),
                    0, GL_DEPTH_COMPONENT, GL_FLOAT, (ByteBuffer) null);
        }
    }

    private void configureTextureSamplerState(TextureDescriptor descriptor) {
        int filter = descriptor.samplerFilter() == SamplerFilter.NEAREST ? GL_NEAREST : GL_LINEAR;
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, filter);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, filter);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE);
        if (descriptor.format() == TextureFormat.DEPTH32F && descriptor.usage() == TextureUsage.SAMPLED_DEPTH_SHADOW) {
            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_COMPARE_MODE, GL_COMPARE_REF_TO_TEXTURE);
            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_COMPARE_FUNC, GL_LEQUAL);
        }
    }

    @Override
    public RenderTargetHandle createRenderTarget(RenderTargetDescriptor descriptor) {
        int fboId = glGenFramebuffers();
        glBindFramebuffer(GL_FRAMEBUFFER, fboId);
        attachColorTextures(descriptor);
        descriptor.depthAttachment().ifPresent(this::attachDepthTexture);
        if (descriptor.colorAttachments().isEmpty()) {
            glDrawBuffer(GL_NONE);
            glReadBuffer(GL_NONE);
        }
        if (glCheckFramebufferStatus(GL_FRAMEBUFFER) != GL_FRAMEBUFFER_COMPLETE) {
            throw new EpysiaException("Render target framebuffer is incomplete.");
        }
        glBindFramebuffer(GL_FRAMEBUFFER, 0);
        long id = nextId.getAndIncrement();
        renderTargets.put(id, new RenderTargetResource(fboId, descriptor.width(), descriptor.height()));
        return new RenderTargetHandle(id);
    }

    private void attachColorTextures(RenderTargetDescriptor descriptor) {
        int attachmentIndex = 0;
        for (TextureHandle color : descriptor.colorAttachments()) {
            TextureResource resource = requireTexture(color);
            glFramebufferTexture2D(GL_FRAMEBUFFER, GL_COLOR_ATTACHMENT0 + attachmentIndex, GL_TEXTURE_2D, resource.textureId(), 0);
            attachmentIndex++;
        }
    }

    private void attachDepthTexture(TextureHandle depth) {
        TextureResource resource = requireTexture(depth);
        glFramebufferTexture2D(GL_FRAMEBUFFER, GL_DEPTH_ATTACHMENT, GL_TEXTURE_2D, resource.textureId(), 0);
    }

    @Override
    public BindingSetHandle createBindingSet(BindingSetDescriptor descriptor) {
        List<ResolvedBinding> resolved = new ArrayList<>(descriptor.bindings().size());
        for (Binding binding : descriptor.bindings()) {
            resolved.add(resolveBinding(binding));
        }
        long id = nextId.getAndIncrement();
        bindingSets.put(id, new BindingSetResource(resolved));
        return new BindingSetHandle(id);
    }

    private ResolvedBinding resolveBinding(Binding binding) {
        return switch (binding.resource()) {
            case UniformBufferBinding ubo -> new ResolvedUbo(
                    binding.slotIndex(),
                    requireBuffer(ubo.buffer()).bufferId(),
                    ubo.byteOffset(),
                    ubo.byteSize()
            );
            case SampledTextureBinding texture -> new ResolvedTexture(
                    binding.slotIndex(),
                    requireTexture(texture.texture()).textureId()
            );
        };
    }

    @Override
    public void writeBuffer(BufferHandle handle, ByteBuffer data, long byteOffset) {
        BufferResource resource = requireBuffer(handle);
        glBindBuffer(resource.glTarget(), resource.bufferId());
        glBufferSubData(resource.glTarget(), byteOffset, data);
    }

    @Override
    public void writeTexture(TextureHandle handle, ByteBuffer rgbaPixels) {
        TextureResource resource = requireTexture(handle);
        glBindTexture(GL_TEXTURE_2D, resource.textureId());
        glTexSubImage2D(GL_TEXTURE_2D, 0, 0, 0, resource.width(), resource.height(), GL_RGBA, GL_UNSIGNED_BYTE, rgbaPixels);
    }

    @Override
    public void updatePipelineShaders(PipelineHandle handle, ShaderSource shaders) {
        PipelineResource existing = requirePipeline(handle);
        int newProgram = compileProgram(shaders);
        glDeleteProgram(existing.programId());
        pipelines.put(handle.id(), new PipelineResource(newProgram, existing.vaoId(), existing.state(), existing.vertexStride()));
        currentPipeline = null;
        currentBindingSetId = 0L;
    }

    @Override
    public void destroy(PipelineHandle handle) {
        PipelineResource resource = pipelines.remove(handle.id());
        if (resource != null) {
            deletePipelineResource(resource);
        }
    }

    @Override
    public void destroy(MeshHandle handle) {
        meshes.remove(handle.id());
    }

    @Override
    public void destroy(BufferHandle handle) {
        BufferResource resource = buffers.remove(handle.id());
        if (resource != null) {
            glDeleteBuffers(resource.bufferId());
        }
    }

    @Override
    public void destroy(TextureHandle handle) {
        TextureResource resource = textures.remove(handle.id());
        if (resource != null) {
            glDeleteTextures(resource.textureId());
        }
    }

    @Override
    public void destroy(RenderTargetHandle handle) {
        RenderTargetResource resource = renderTargets.remove(handle.id());
        if (resource != null) {
            glDeleteFramebuffers(resource.fboId());
        }
    }

    @Override
    public void destroy(BindingSetHandle handle) {
        bindingSets.remove(handle.id());
    }

    private void deletePipelineResource(PipelineResource resource) {
        glDeleteVertexArrays(resource.vaoId());
        glDeleteProgram(resource.programId());
    }

    @Override
    public void onViewportResize(int width, int height) {
        if (width <= 0 || height <= 0) {
            return;
        }
        screenWidth = width;
        screenHeight = height;
        glViewport(0, 0, width, height);
    }

    @Override
    public void beginFrame() {
        drainProfileQueries();
        currentProfileSection = 0;
    }

    private void drainProfileQueries() {
        if (!profileQueriesAllocated) {
            return;
        }
        int readSlot = (profileFrameSlot + 1) % PROFILE_FRAME_LAG;
        int sectionCount = profileSectionCounts[readSlot];
        if (sectionCount == 0) {
            return;
        }
        latestTimings.clear();
        for (int i = 0; i < sectionCount; i++) {
            long nanos = glGetQueryObjectui64(profileQueries[readSlot][i], GL_QUERY_RESULT);
            String name = profileSectionNames[readSlot][i];
            latestTimings.merge(name, nanos, Long::sum);
        }
    }

    @Override
    public void beginProfileSection(String name) {
        if (!profileQueriesAllocated || profileSectionActive || currentProfileSection >= MAX_PROFILE_SECTIONS) {
            return;
        }
        profileSectionNames[profileFrameSlot][currentProfileSection] = name;
        glBeginQuery(GL_TIME_ELAPSED, profileQueries[profileFrameSlot][currentProfileSection]);
        profileSectionActive = true;
    }

    @Override
    public void endProfileSection() {
        if (!profileSectionActive) {
            return;
        }
        glEndQuery(GL_TIME_ELAPSED);
        currentProfileSection++;
        profileSectionActive = false;
    }

    @Override
    public Map<String, Long> latestProfileTimingsNanos() {
        return latestTimings;
    }

    @Override
    public void beginPass(RenderTargetHandle target, PassClear clear) {
        if (target.id() == 0L) {
            glBindFramebuffer(GL_FRAMEBUFFER, 0);
            glViewport(0, 0, screenWidth, screenHeight);
        } else {
            RenderTargetResource resource = requireRenderTarget(target);
            glBindFramebuffer(GL_FRAMEBUFFER, resource.fboId());
            glViewport(0, 0, resource.width(), resource.height());
        }
        currentPipeline = null;
        currentBindingSetId = 0L;
        currentVertexBufferId = 0;
        currentIndexBufferId = 0;
        applyClear(clear);
    }

    private void applyClear(PassClear clear) {
        int mask = 0;
        if (clear.clearColor()) {
            glClearColor(clear.red(), clear.green(), clear.blue(), clear.alpha());
            mask |= GL_COLOR_BUFFER_BIT;
        }
        if (clear.clearDepth()) {
            glClearDepth(clear.depth());
            mask |= GL_DEPTH_BUFFER_BIT;
        }
        if (mask != 0) {
            glClear(mask);
        }
    }

    @Override
    public void execute(DrawCommand command) {
        PipelineResource pipeline = requirePipeline(command.pipeline());
        MeshResource mesh = requireMesh(command.mesh());
        if (currentPipeline != pipeline) {
            applyPipeline(pipeline);
            currentPipeline = pipeline;
        }
        if (currentBindingSetId != command.bindings().id()) {
            applyBindings(command.bindings());
            currentBindingSetId = command.bindings().id();
        }
        if (currentVertexBufferId != mesh.vertexBufferId()) {
            glBindVertexBuffer(VERTEX_BINDING_INDEX, mesh.vertexBufferId(), 0L, pipeline.vertexStride());
            currentVertexBufferId = mesh.vertexBufferId();
        }
        if (currentIndexBufferId != mesh.indexBufferId()) {
            glBindBuffer(GL_ELEMENT_ARRAY_BUFFER, mesh.indexBufferId());
            currentIndexBufferId = mesh.indexBufferId();
        }
        int indexCount = command.indexCountOverride() == DrawCommand.USE_MESH_INDEX_COUNT
                ? mesh.indexCount()
                : command.indexCountOverride();
        glDrawElements(
                topologyToGl(pipeline.state().topology()),
                indexCount,
                indexFormatToGl(mesh.indexFormat()),
                (long) mesh.firstIndex() * mesh.indexFormat().byteSize()
        );
    }

    @Override
    public void endPass() {
    }

    @Override
    public void endFrame() {
        profileSectionCounts[profileFrameSlot] = currentProfileSection;
        profileFrameSlot = (profileFrameSlot + 1) % PROFILE_FRAME_LAG;
    }

    private void applyPipeline(PipelineResource pipeline) {
        glUseProgram(pipeline.programId());
        glBindVertexArray(pipeline.vaoId());
        applyDepthTest(pipeline.state().depthTest());
        applyBlendMode(pipeline.state().blendMode());
        applyCullMode(pipeline.state().cullMode());
    }

    private void applyBindings(BindingSetHandle handle) {
        if (handle.id() == 0L) {
            return;
        }
        BindingSetResource resource = requireBindingSet(handle);
        for (ResolvedBinding binding : resource.bindings()) {
            applyResolvedBinding(binding);
        }
    }

    private void applyResolvedBinding(ResolvedBinding binding) {
        switch (binding) {
            case ResolvedUbo ubo -> glBindBufferRange(GL_UNIFORM_BUFFER, ubo.slot(), ubo.bufferId(), ubo.offset(), ubo.size());
            case ResolvedTexture texture -> {
                glActiveTexture(GL_TEXTURE0 + texture.slot());
                glBindTexture(GL_TEXTURE_2D, texture.textureId());
            }
        }
    }

    private void applyDepthTest(DepthTest depthTest) {
        switch (depthTest) {
            case DISABLED -> glDisable(GL_DEPTH_TEST);
            case LESS -> {
                glEnable(GL_DEPTH_TEST);
                glDepthFunc(GL_LESS);
            }
            case LESS_EQUAL -> {
                glEnable(GL_DEPTH_TEST);
                glDepthFunc(GL_LEQUAL);
            }
        }
    }

    private void applyBlendMode(BlendMode blendMode) {
        switch (blendMode) {
            case OPAQUE -> glDisable(GL_BLEND);
            case ALPHA_BLEND -> {
                glEnable(GL_BLEND);
                glBlendFuncSeparate(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA, GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);
            }
        }
    }

    private void applyCullMode(CullMode cullMode) {
        switch (cullMode) {
            case NONE -> glDisable(GL_CULL_FACE);
            case BACK -> {
                glEnable(GL_CULL_FACE);
                glCullFace(GL_BACK);
            }
            case FRONT -> {
                glEnable(GL_CULL_FACE);
                glCullFace(GL_FRONT);
            }
        }
    }

    private static int glTargetFor(BufferUsage usage) {
        return switch (usage) {
            case VERTEX -> GL_ARRAY_BUFFER;
            case INDEX -> GL_ELEMENT_ARRAY_BUFFER;
            case UNIFORM -> GL_UNIFORM_BUFFER;
        };
    }

    private static int topologyToGl(Topology topology) {
        return switch (topology) {
            case TRIANGLES -> GL_TRIANGLES;
            case TRIANGLE_STRIP -> GL_TRIANGLE_STRIP;
            case LINES -> GL_LINES;
        };
    }

    private static int indexFormatToGl(IndexFormat format) {
        return switch (format) {
            case UINT16 -> GL_UNSIGNED_SHORT;
            case UINT32 -> GL_UNSIGNED_INT;
        };
    }

    private BufferResource requireBuffer(BufferHandle handle) {
        BufferResource resource = buffers.get(handle.id());
        if (resource == null) {
            throw new EpysiaException("Unknown buffer handle: " + handle.id());
        }
        return resource;
    }

    private MeshResource requireMesh(MeshHandle handle) {
        MeshResource resource = meshes.get(handle.id());
        if (resource == null) {
            throw new EpysiaException("Unknown mesh handle: " + handle.id());
        }
        return resource;
    }

    private PipelineResource requirePipeline(PipelineHandle handle) {
        PipelineResource resource = pipelines.get(handle.id());
        if (resource == null) {
            throw new EpysiaException("Unknown pipeline handle: " + handle.id());
        }
        return resource;
    }

    public int glTextureName(TextureHandle handle) {
        return requireTexture(handle).textureId();
    }

    private TextureResource requireTexture(TextureHandle handle) {
        TextureResource resource = textures.get(handle.id());
        if (resource == null) {
            throw new EpysiaException("Unknown texture handle: " + handle.id());
        }
        return resource;
    }

    private RenderTargetResource requireRenderTarget(RenderTargetHandle handle) {
        RenderTargetResource resource = renderTargets.get(handle.id());
        if (resource == null) {
            throw new EpysiaException("Unknown render target handle: " + handle.id());
        }
        return resource;
    }

    private BindingSetResource requireBindingSet(BindingSetHandle handle) {
        BindingSetResource resource = bindingSets.get(handle.id());
        if (resource == null) {
            throw new EpysiaException("Unknown binding set handle: " + handle.id());
        }
        return resource;
    }

    private record PipelineResource(int programId, int vaoId, RenderState state, int vertexStride) {
    }

    private record MeshResource(int vertexBufferId, int indexBufferId, int firstIndex, int indexCount, IndexFormat indexFormat) {
    }

    private record BufferResource(int bufferId, int glTarget) {
    }

    private record TextureResource(int textureId, int width, int height, TextureFormat format) {
    }

    private record RenderTargetResource(int fboId, int width, int height) {
    }

    private record BindingSetResource(List<ResolvedBinding> bindings) {
    }

    private sealed interface ResolvedBinding permits ResolvedUbo, ResolvedTexture {
    }

    private record ResolvedUbo(int slot, int bufferId, long offset, long size) implements ResolvedBinding {
    }

    private record ResolvedTexture(int slot, int textureId) implements ResolvedBinding {
    }
}
