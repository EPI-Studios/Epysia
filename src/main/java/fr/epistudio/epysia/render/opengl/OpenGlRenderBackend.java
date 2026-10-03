package fr.epistudio.epysia.render.opengl;

import fr.epistudio.epysia.render.Frame;
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

import static org.lwjgl.opengl.EXTTextureFilterAnisotropic.GL_MAX_TEXTURE_MAX_ANISOTROPY_EXT;
import static org.lwjgl.opengl.EXTTextureFilterAnisotropic.GL_TEXTURE_MAX_ANISOTROPY_EXT;
import static org.lwjgl.opengl.GL11.GL_ALWAYS;
import static org.lwjgl.opengl.GL11.GL_BACK;
import static org.lwjgl.opengl.GL11.GL_DECR;
import static org.lwjgl.opengl.GL11.GL_EQUAL;
import static org.lwjgl.opengl.GL11.GL_GEQUAL;
import static org.lwjgl.opengl.GL11.GL_GREATER;
import static org.lwjgl.opengl.GL11.GL_INCR;
import static org.lwjgl.opengl.GL11.GL_INVERT;
import static org.lwjgl.opengl.GL11.GL_KEEP;
import static org.lwjgl.opengl.GL11.GL_NEVER;
import static org.lwjgl.opengl.GL11.GL_NOTEQUAL;
import static org.lwjgl.opengl.GL11.GL_REPLACE;
import static org.lwjgl.opengl.GL11.GL_STENCIL_BUFFER_BIT;
import static org.lwjgl.opengl.GL11.GL_STENCIL_TEST;
import static org.lwjgl.opengl.GL11.GL_ZERO;
import static org.lwjgl.opengl.GL11.glClearStencil;
import static org.lwjgl.opengl.GL11.glStencilFunc;
import static org.lwjgl.opengl.GL11.glStencilMask;
import static org.lwjgl.opengl.GL11.glStencilOp;
import static org.lwjgl.opengl.GL14.GL_DECR_WRAP;
import static org.lwjgl.opengl.GL14.GL_INCR_WRAP;
import static org.lwjgl.opengl.GL11.glGetInteger;
import static org.lwjgl.opengl.GL31.GL_UNIFORM_BUFFER_OFFSET_ALIGNMENT;
import static org.lwjgl.opengl.GL11.glTexParameterf;
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
import static org.lwjgl.opengl.GL11.GL_REPEAT;
import static org.lwjgl.opengl.GL12.GL_CLAMP_TO_EDGE;
import static org.lwjgl.opengl.GL14.GL_MIRRORED_REPEAT;
import static org.lwjgl.opengl.GL13.GL_TEXTURE0;
import static org.lwjgl.opengl.GL13.glActiveTexture;
import static org.lwjgl.opengl.GL14.glBlendFuncSeparate;
import static org.lwjgl.opengl.GL30.GL_DEPTH_COMPONENT32F;
import static org.lwjgl.opengl.GL15.GL_ARRAY_BUFFER;
import static org.lwjgl.opengl.GL15.GL_DYNAMIC_DRAW;
import static org.lwjgl.opengl.GL15.GL_ELEMENT_ARRAY_BUFFER;
import static org.lwjgl.opengl.GL15.GL_STATIC_DRAW;
import static org.lwjgl.opengl.GL11.GL_FALSE;
import static org.lwjgl.opengl.GL15.GL_QUERY_RESULT;
import static org.lwjgl.opengl.GL15.GL_QUERY_RESULT_AVAILABLE;
import static org.lwjgl.opengl.GL15.glGetQueryObjecti;
import static org.lwjgl.opengl.GL15.glBeginQuery;
import static org.lwjgl.opengl.GL15.glBindBuffer;
import static org.lwjgl.opengl.GL15.glDeleteQueries;
import static org.lwjgl.opengl.GL15.glEndQuery;
import static org.lwjgl.opengl.GL15.glGenQueries;
import static org.lwjgl.opengl.GL33.GL_TIME_ELAPSED;
import static org.lwjgl.opengl.GL33.GL_TIMESTAMP;
import static org.lwjgl.opengl.GL33.glGetQueryObjectui64;
import static org.lwjgl.opengl.GL33.glQueryCounter;
import static org.lwjgl.opengl.GL15.glBufferData;
import static org.lwjgl.opengl.GL15.glBufferSubData;
import static org.lwjgl.opengl.GL15.glDeleteBuffers;
import static org.lwjgl.opengl.GL15.glUnmapBuffer;
import static org.lwjgl.opengl.GL30.glMapBufferRange;
import static org.lwjgl.opengl.GL32.GL_SYNC_FLUSH_COMMANDS_BIT;
import static org.lwjgl.opengl.GL32.GL_SYNC_GPU_COMMANDS_COMPLETE;
import static org.lwjgl.opengl.GL32.glClientWaitSync;
import static org.lwjgl.opengl.GL32.glDeleteSync;
import static org.lwjgl.opengl.GL32.glFenceSync;
import static org.lwjgl.opengl.GL44.GL_MAP_COHERENT_BIT;
import static org.lwjgl.opengl.GL44.GL_MAP_PERSISTENT_BIT;
import static org.lwjgl.opengl.GL44.glBufferStorage;
import static org.lwjgl.opengl.GL45.glCreateBuffers;
import static org.lwjgl.opengl.GL45.glGetNamedBufferSubData;
import static org.lwjgl.opengl.GL45.glMapNamedBufferRange;
import static org.lwjgl.opengl.GL45.glNamedBufferData;
import static org.lwjgl.opengl.GL45.glNamedBufferStorage;
import static org.lwjgl.opengl.GL45.glNamedBufferSubData;
import static org.lwjgl.opengl.GL45.glUnmapNamedBuffer;
import static org.lwjgl.opengl.GL45.glCreateTextures;
import static org.lwjgl.opengl.GL45.glGenerateTextureMipmap;
import static org.lwjgl.opengl.GL45.glGetTextureImage;
import static org.lwjgl.opengl.GL45.glTextureParameterf;
import static org.lwjgl.opengl.GL45.glTextureParameteri;
import static org.lwjgl.opengl.GL45.glTextureStorage2D;
import static org.lwjgl.opengl.GL45.glTextureStorage3D;
import static org.lwjgl.opengl.GL45.glTextureSubImage2D;
import static org.lwjgl.opengl.GL45.glCheckNamedFramebufferStatus;
import static org.lwjgl.opengl.GL45.glCreateFramebuffers;
import static org.lwjgl.opengl.GL45.glCreateVertexArrays;
import static org.lwjgl.opengl.GL45.glEnableVertexArrayAttrib;
import static org.lwjgl.opengl.GL45.glNamedFramebufferDrawBuffer;
import static org.lwjgl.opengl.GL45.glNamedFramebufferReadBuffer;
import static org.lwjgl.opengl.GL45.glNamedFramebufferTexture;
import static org.lwjgl.opengl.GL45.glNamedFramebufferTextureLayer;
import static org.lwjgl.opengl.GL45.glVertexArrayAttribBinding;
import static org.lwjgl.opengl.GL45.glVertexArrayAttribFormat;
import static org.lwjgl.opengl.GL45.glVertexArrayAttribIFormat;
import static org.lwjgl.opengl.GL45.glVertexArrayBindingDivisor;
import static org.lwjgl.opengl.GL44.glBindTextures;
import org.lwjgl.opengl.NVShaderBufferLoad;
import org.lwjgl.opengl.NVUniformBufferUnifiedMemory;
import org.lwjgl.opengl.NVVertexBufferUnifiedMemory;
import static org.lwjgl.opengl.GL30.GL_MAP_WRITE_BIT;
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
import static org.lwjgl.opengl.GL30.GL_DEPTH32F_STENCIL8;
import static org.lwjgl.opengl.GL30.GL_DEPTH_ATTACHMENT;
import static org.lwjgl.opengl.GL30.GL_DEPTH_STENCIL;
import static org.lwjgl.opengl.GL30.GL_DEPTH_STENCIL_ATTACHMENT;
import static org.lwjgl.opengl.GL30.GL_FLOAT_32_UNSIGNED_INT_24_8_REV;
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
import static org.lwjgl.opengl.GL43.GL_SHADER_STORAGE_BUFFER;
import static org.lwjgl.opengl.GL43.GL_DEBUG_OUTPUT;
import static org.lwjgl.opengl.GL43.GL_DEBUG_OUTPUT_SYNCHRONOUS;
import static org.lwjgl.opengl.GL43.GL_DEBUG_SEVERITY_HIGH;
import static org.lwjgl.opengl.GL43.glBindVertexBuffer;
import static org.lwjgl.opengl.GL43.glCopyImageSubData;
import static org.lwjgl.opengl.GL43.glDebugMessageCallback;
import static org.lwjgl.opengl.GL43.glVertexAttribBinding;
import static org.lwjgl.opengl.GL43.glVertexAttribFormat;
import static org.lwjgl.opengl.GL43.glVertexBindingDivisor;
import static org.lwjgl.opengl.GL31.glDrawElementsInstanced;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL15;
import org.lwjgl.opengl.GL30;
import org.lwjgl.opengl.GL32;
import org.lwjgl.opengl.GL40;
import org.lwjgl.opengl.GL42;
import org.lwjgl.opengl.GL43;

public final class OpenGlRenderBackend implements RenderBackend {
    private int[] pendingViewport;

    private static final int VERTEX_BINDING_INDEX = 0;
    private static final int INSTANCE_BINDING_INDEX = 1;

    private static final int UNKNOWN_ANISOTROPY = -1;

    private final AtomicLong nextId = new AtomicLong(1);
    private int maximumAnisotropy = UNKNOWN_ANISOTROPY;
    private long shaderCompileNanos;
    private int shaderCompileCount;
    private long frameShaderCompileNanos;
    private long worstFrameShaderCompileNanos;
    private long worstProgramShaderCompileNanos;
    private final Map<Long, PipelineResource> pipelines = new HashMap<>();
    private final Map<Long, MeshResource> meshes = new HashMap<>();
    private final Map<Long, BufferResource> buffers = new HashMap<>();
    private final Map<Long, TextureResource> textures = new HashMap<>();
    private final Map<Long, RenderTargetResource> renderTargets = new HashMap<>();
    private static final boolean TRACK_BINDING_SETS =
            Boolean.getBoolean("epysia.trackBindingSets");

    private final Map<Long, BindingSetResource> bindingSets = new HashMap<>();
    private final Map<Long, String> bindingSetGraveyard = new HashMap<>();
    private long frameIndex;

    private PipelineResource currentPipeline;
    private RenderState appliedRenderState;
    private boolean persistentMappingSupported;
    private boolean directStateAccessSupported;
    private boolean multiBindSupported;
    private boolean unifiedUniformMemorySupported;
    private int ringFrameSlot;
    private int uniformOverrideSlot = -1;
    private BufferResource uniformOverrideBuffer;
    private long uniformOverrideOffset;
    private long uniformOverrideSize;
    private final long[] ringFrameFences = new long[BufferResource.RING_SLOTS];
    private long currentBindingSetId;
    private int currentVertexBufferId;
    private int currentIndexBufferId;
    private GLDebugMessageCallback debugCallback;
    private int screenWidth;
    private int screenHeight;
    private int currentTargetHeight;
    private ScissorRect appliedScissor = ScissorRect.disabled();

    private static final String DEBUG_PROPERTY = "epysia.gl.debug";
    private static final String GPU_PROFILING_PROPERTY = "epysia.gpu.profiling";
    private static final String SYNCHRONOUS_DEBUG_PROPERTY = "epysia.gl.debug.synchronous";
    private static final String DIRECT_STATE_ACCESS_DISABLED_PROPERTY = "epysia.gl.directStateAccess.disabled";
    private static final int DIRECT_WRITE_BYTE_LIMIT = 2048;
    private static final String MULTI_BIND_DISABLED_PROPERTY = "epysia.gl.multiBind.disabled";
    private static final String UNIFIED_MEMORY_ENABLED_PROPERTY = "epysia.gl.unifiedMemory.enabled";
    private static final int NO_TEXTURE_RANGE = -1;
    private static final int PROFILE_FRAME_LAG = 4;
    private static final int MAX_PROFILE_SECTIONS = 32;
    private final int[][] profileQueries = new int[PROFILE_FRAME_LAG][MAX_PROFILE_SECTIONS];
    private final String[][] profileSectionNames = new String[PROFILE_FRAME_LAG][MAX_PROFILE_SECTIONS];
    private final int[] profileSectionCounts = new int[PROFILE_FRAME_LAG];
    private final Map<String, Long> latestTimings = new LinkedHashMap<>();
    private final DrawStatistics drawStatistics = new DrawStatistics();
    private int profileFrameSlot;
    private int currentProfileSection;
    private int suppressedSectionDepth;
    private boolean profileSectionActive;
    private boolean profileQueriesAllocated;
    private final int[] frameStartQueries = new int[PROFILE_FRAME_LAG];
    private final int[] frameEndQueries = new int[PROFILE_FRAME_LAG];
    private int profileStallCount;

    @Override
    public void initialize(RenderSurface surface) {
        GLCapabilities capabilities = GL.createCapabilities();
        verifyMinimumGlVersion();
        installDebugCallback(capabilities);
        persistentMappingSupported = (capabilities.OpenGL44 || capabilities.GL_ARB_buffer_storage)
                && !Boolean.getBoolean("epysia.ring.disabled");
        directStateAccessSupported = (capabilities.OpenGL45 || capabilities.GL_ARB_direct_state_access)
                && !Boolean.getBoolean(DIRECT_STATE_ACCESS_DISABLED_PROPERTY);
        multiBindSupported = (capabilities.OpenGL44 || capabilities.GL_ARB_multi_bind)
                && !Boolean.getBoolean(MULTI_BIND_DISABLED_PROPERTY);
        enableUnifiedUniformMemory(capabilities);
        glEnable(GL32.GL_TEXTURE_CUBE_MAP_SEAMLESS);
        screenWidth = surface.framebufferWidth();
        screenHeight = surface.framebufferHeight();
        glViewport(0, 0, screenWidth, screenHeight);
        allocateProfileQueries();
    }

    private void enableUnifiedUniformMemory(GLCapabilities capabilities) {
        unifiedUniformMemorySupported = capabilities.GL_NV_uniform_buffer_unified_memory
                && capabilities.GL_NV_vertex_buffer_unified_memory
                && capabilities.GL_NV_shader_buffer_load
                && Boolean.getBoolean(UNIFIED_MEMORY_ENABLED_PROPERTY);
        if (unifiedUniformMemorySupported) {
            GL11.glEnableClientState(NVUniformBufferUnifiedMemory.GL_UNIFORM_BUFFER_UNIFIED_NV);
        }
    }

    private long[] makeUniformBufferResident(int glTarget, int[] bufferIds) {
        if (!unifiedUniformMemorySupported || glTarget != GL_UNIFORM_BUFFER) {
            return new long[0];
        }
        long[] addresses = new long[bufferIds.length];
        long[] queried = new long[1];
        for (int slot = 0; slot < bufferIds.length; slot++) {
            NVShaderBufferLoad.glMakeNamedBufferResidentNV(bufferIds[slot], GL15.GL_READ_ONLY);
            NVShaderBufferLoad.glGetNamedBufferParameterui64vNV(bufferIds[slot],
                    NVShaderBufferLoad.GL_BUFFER_GPU_ADDRESS_NV, queried);
            addresses[slot] = queried[0];
        }
        return addresses;
    }

    private void makeBufferNonResident(BufferResource resource) {
        if (!resource.resident()) {
            return;
        }
        for (int bufferId : resource.bufferIds()) {
            NVShaderBufferLoad.glMakeNamedBufferNonResidentNV(bufferId);
        }
    }

    private void allocateProfileQueries() {
        if (!Boolean.getBoolean(GPU_PROFILING_PROPERTY)) {
            System.out.println("[gpu-profiling] disabled (-D" + GPU_PROFILING_PROPERTY + "=true to enable)");
            return;
        }
        System.out.println("[gpu-profiling] enabled, allocating timer queries");
        for (int frame = 0; frame < PROFILE_FRAME_LAG; frame++) {
            for (int section = 0; section < MAX_PROFILE_SECTIONS; section++) {
                profileQueries[frame][section] = glGenQueries();
            }
            frameStartQueries[frame] = glGenQueries();
            frameEndQueries[frame] = glGenQueries();
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
        if (!capabilities.OpenGL43 || !Boolean.getBoolean(DEBUG_PROPERTY)) {
            return;
        }
        glEnable(GL_DEBUG_OUTPUT);
        if (Boolean.getBoolean(SYNCHRONOUS_DEBUG_PROPERTY)) {
            glEnable(GL_DEBUG_OUTPUT_SYNCHRONOUS);
        }
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
        buffers.values().forEach(this::destroyBufferResource);
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
        int glUsage = isDynamicUsage(descriptor.usage()) ? GL_DYNAMIC_DRAW : GL_STATIC_DRAW;
        long id = nextId.getAndIncrement();
        if (descriptor.perFrame() && persistentMappingSupported) {
            buffers.put(id, createRingBuffer(glTarget, descriptor.data()));
            return new BufferHandle(id);
        }
        int bufferId = allocateBuffer(glTarget, descriptor.data(), glUsage);
        int[] bufferIds = {bufferId};
        buffers.put(id, BufferResource.single(bufferIds, makeUniformBufferResident(glTarget, bufferIds),
                glTarget, descriptor.data().remaining(), glUsage));
        return new BufferHandle(id);
    }

    private int allocateBuffer(int glTarget, ByteBuffer data, int glUsage) {
        if (directStateAccessSupported) {
            int bufferId = glCreateBuffers();
            glNamedBufferData(bufferId, data, glUsage);
            return bufferId;
        }
        int bufferId = glGenBuffers();
        glBindBuffer(glTarget, bufferId);
        glBufferData(glTarget, data, glUsage);
        return bufferId;
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
    public void updateMeshRange(MeshHandle handle, int firstIndex, int indexCount) {
        MeshResource existing = meshes.get(handle.id());
        if (existing == null) {
            throw new EpysiaException("Unknown mesh handle: " + handle.id());
        }
        meshes.put(handle.id(), new MeshResource(existing.vertexBufferId(), existing.indexBufferId(),
                firstIndex, indexCount, existing.indexFormat()));
    }

    @Override
    public PipelineHandle createPipeline(PipelineDescriptor descriptor) {
        int program = compileProgram(descriptor.shaders());
        int vao = createVertexArrayObject(descriptor.vertexLayout(), descriptor.instanceLayout());
        int instanceStride = descriptor.isInstanced() ? descriptor.instanceLayout().byteStride() : 0;
        long id = nextId.getAndIncrement();
        pipelines.put(id, new PipelineResource(
                program,
                vao,
                descriptor.state(),
                descriptor.vertexLayout().byteStride(),
                instanceStride
        ));
        return new PipelineHandle(id);
    }

    @Override
    public void readBuffer(BufferHandle handle, ByteBuffer destination, long byteOffset) {
        BufferResource resource = requireBuffer(handle);
        if (directStateAccessSupported) {
            glGetNamedBufferSubData(resource.bufferId(), byteOffset, destination);
            return;
        }
        glBindBuffer(resource.glTarget(), resource.bufferId());
        GL15.glGetBufferSubData(resource.glTarget(), byteOffset, destination);
    }

    @Override
    public PipelineHandle createComputePipeline(ComputePipelineDescriptor descriptor) {
        int shader = compileShader(GL43.GL_COMPUTE_SHADER, descriptor.computeSource());
        int program = glCreateProgram();
        glAttachShader(program, shader);
        glLinkProgram(program);
        checkProgramLink(program);
        glDetachShader(program, shader);
        glDeleteShader(shader);
        long id = nextId.getAndIncrement();
        pipelines.put(id, PipelineResource.compute(program));
        return new PipelineHandle(id);
    }

    @Override
    public void dispatchCompute(ComputeDispatch dispatch) {
        PipelineResource pipeline = requirePipeline(dispatch.pipeline());
        glUseProgram(pipeline.programId());
        currentPipeline = null;
        applyBindings(dispatch.bindings());
        currentBindingSetId = -1L;
        GL43.glDispatchCompute(dispatch.groupCountX(), dispatch.groupCountY(),
                dispatch.groupCountZ());
    }

    @Override
    public void computeBarrier(ComputeBarrier barrier) {
        GL42.glMemoryBarrier(barrierBits(barrier));
    }

    private static int storageImageAccess(StorageImageAccess access) {
        return switch (access) {
            case READ_ONLY -> GL15.GL_READ_ONLY;
            case WRITE_ONLY -> GL15.GL_WRITE_ONLY;
            case READ_WRITE -> GL15.GL_READ_WRITE;
        };
    }

    private static int barrierBits(ComputeBarrier barrier) {
        return switch (barrier) {
            case STORAGE_BUFFER -> GL43.GL_SHADER_STORAGE_BARRIER_BIT;
            case STORAGE_IMAGE -> GL42.GL_SHADER_IMAGE_ACCESS_BARRIER_BIT;
            case VERTEX_ATTRIBUTES -> GL42.GL_VERTEX_ATTRIB_ARRAY_BARRIER_BIT;
            case TEXTURE_FETCH -> GL42.GL_TEXTURE_FETCH_BARRIER_BIT;
            case ALL -> GL42.GL_ALL_BARRIER_BITS;
        };
    }

    private int createVertexArrayObject(VertexLayout layout, VertexLayout instanceLayout) {
        if (directStateAccessSupported) {
            return createVertexArrayObjectNamed(layout, instanceLayout);
        }
        int vao = glGenVertexArrays();
        glBindVertexArray(vao);
        for (VertexAttribute attribute : layout.attributes()) {
            glEnableVertexAttribArray(attribute.location());
            attributeFormat(attribute);
            glVertexAttribBinding(attribute.location(), VERTEX_BINDING_INDEX);
        }
        if (instanceLayout != null) {
            for (VertexAttribute attribute : instanceLayout.attributes()) {
                glEnableVertexAttribArray(attribute.location());
                attributeFormat(attribute);
                glVertexAttribBinding(attribute.location(), INSTANCE_BINDING_INDEX);
            }
            glVertexBindingDivisor(INSTANCE_BINDING_INDEX, 1);
        }
        glBindVertexArray(0);
        return vao;
    }

    private int createVertexArrayObjectNamed(VertexLayout layout, VertexLayout instanceLayout) {
        int vao = glCreateVertexArrays();
        for (VertexAttribute attribute : layout.attributes()) {
            glEnableVertexArrayAttrib(vao, attribute.location());
            namedAttributeFormat(vao, attribute);
            glVertexArrayAttribBinding(vao, attribute.location(), VERTEX_BINDING_INDEX);
        }
        if (instanceLayout != null) {
            for (VertexAttribute attribute : instanceLayout.attributes()) {
                glEnableVertexArrayAttrib(vao, attribute.location());
                namedAttributeFormat(vao, attribute);
                glVertexArrayAttribBinding(vao, attribute.location(), INSTANCE_BINDING_INDEX);
            }
            glVertexArrayBindingDivisor(vao, INSTANCE_BINDING_INDEX, 1);
        }
        return vao;
    }

    private void namedAttributeFormat(int vao, VertexAttribute attribute) {
        if (attribute.format().integer()) {
            glVertexArrayAttribIFormat(vao, attribute.location(),
                    attribute.format().componentCount(),
                    attribute.format().wideInteger() ? GL11.GL_UNSIGNED_INT : GL11.GL_UNSIGNED_SHORT,
                    attribute.byteOffset());
            return;
        }
        glVertexArrayAttribFormat(vao, attribute.location(),
                attribute.format().componentCount(), GL11.GL_FLOAT, false, attribute.byteOffset());
    }

    private void attributeFormat(VertexAttribute attribute) {
        if (attribute.format().integer()) {
            GL43.glVertexAttribIFormat(
                    attribute.location(),
                    attribute.format().componentCount(),
                    attribute.format().wideInteger()
                            ? GL11.GL_UNSIGNED_INT
                            : GL11.GL_UNSIGNED_SHORT,
                    attribute.byteOffset()
            );
        } else {
            glVertexAttribFormat(
                    attribute.location(),
                    attribute.format().componentCount(),
                    GL_FLOAT,
                    false,
                    attribute.byteOffset()
            );
        }
    }

    private int compileProgram(ShaderSource source) {
        long start = System.nanoTime();
        try {
            return compileProgramUntimed(source);
        } finally {
            long elapsed = System.nanoTime() - start;
            shaderCompileNanos += elapsed;
            frameShaderCompileNanos += elapsed;
            worstFrameShaderCompileNanos = Math.max(worstFrameShaderCompileNanos, frameShaderCompileNanos);
            worstProgramShaderCompileNanos = Math.max(worstProgramShaderCompileNanos, elapsed);
            shaderCompileCount++;
        }
    }

    public long worstFrameShaderCompileNanos() {
        return worstFrameShaderCompileNanos;
    }

    public long worstProgramShaderCompileNanos() {
        return worstProgramShaderCompileNanos;
    }

    public long shaderCompileNanos() {
        return shaderCompileNanos;
    }

    public int shaderCompileCount() {
        return shaderCompileCount;
    }

    public long frameShaderCompileNanos() {
        return frameShaderCompileNanos;
    }

    private int compileProgramUntimed(ShaderSource source) {
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
        int glTarget = textureTargetToGl(descriptor.kind());
        int textureId = createTextureObject(glTarget);
        TextureMutator mutator = textureMutator(textureId, glTarget);
        configureTextureStorage(mutator, descriptor);
        configureTextureSamplerState(mutator, descriptor);
        long id = nextId.getAndIncrement();
        textures.put(id, new TextureResource(textureId, descriptor.width(), descriptor.height(), descriptor.format(), descriptor.kind()));
        return new TextureHandle(id);
    }

    private int createTextureObject(int glTarget) {
        if (directStateAccessSupported) {
            return glCreateTextures(glTarget);
        }
        int textureId = glGenTextures();
        glBindTexture(glTarget, textureId);
        return textureId;
    }

    private TextureMutator textureMutator(int textureId, int glTarget) {
        if (directStateAccessSupported) {
            return new NamedTextureMutator(textureId);
        }
        glBindTexture(glTarget, textureId);
        return new BoundTextureMutator(glTarget);
    }

    private void configureTextureStorage(TextureMutator mutator, TextureDescriptor descriptor) {
        int internalFormat = internalFormatToGl(descriptor.format());
        int levels = Math.max(1, descriptor.mipLevels());
        switch (descriptor.kind()) {
            case TEXTURE_2D, CUBEMAP -> mutator.storage2d(levels, internalFormat,
                    descriptor.width(), descriptor.height());
            case TEXTURE_3D, ARRAY_2D -> mutator.storage3d(levels, internalFormat,
                    descriptor.width(), descriptor.height(), descriptor.layers());
        }
    }

    private static int pixelFormatToGl(TextureFormat format) {
        return switch (format) {
            case DEPTH32F -> GL_DEPTH_COMPONENT;
            case DEPTH32F_STENCIL8 -> GL_DEPTH_STENCIL;
            case R16F, R32F -> GL11.GL_RED;
            default -> GL_RGBA;
        };
    }

    private static int internalFormatToGl(TextureFormat format) {
        return switch (format) {
            case RGBA8 -> GL_RGBA8;
            case SRGB8_ALPHA8 -> GL_SRGB8_ALPHA8;
            case RGBA16F -> GL30.GL_RGBA16F;
            case R11G11B10F -> GL30.GL_R11F_G11F_B10F;
            case R16F -> GL30.GL_R16F;
            case R32F -> GL30.GL_R32F;
            case DEPTH32F -> GL_DEPTH_COMPONENT32F;
            case DEPTH32F_STENCIL8 -> GL_DEPTH32F_STENCIL8;
        };
    }

    private static int pixelTypeToGl(TextureFormat format) {
        return switch (format) {
            case RGBA8, SRGB8_ALPHA8 -> GL_UNSIGNED_BYTE;
            case RGBA16F, R11G11B10F, R16F, R32F, DEPTH32F -> GL_FLOAT;
            case DEPTH32F_STENCIL8 -> GL_FLOAT_32_UNSIGNED_INT_24_8_REV;
        };
    }

    private void configureTextureSamplerState(TextureMutator mutator, TextureDescriptor descriptor) {
        boolean nearest = descriptor.samplerFilter() == SamplerFilter.NEAREST;
        int magFilter = nearest ? GL_NEAREST : GL_LINEAR;
        int mipmapMinFilter = nearest ? GL11.GL_NEAREST_MIPMAP_LINEAR
                : GL11.GL_LINEAR_MIPMAP_LINEAR;
        int minFilter = descriptor.mipLevels() > 1 ? mipmapMinFilter : magFilter;
        int wrapMode = wrapToGl(descriptor.wrap());
        mutator.parameterInt(GL_TEXTURE_MIN_FILTER, minFilter);
        mutator.parameterInt(GL_TEXTURE_MAG_FILTER, magFilter);
        mutator.parameterInt(GL_TEXTURE_WRAP_S, wrapMode);
        mutator.parameterInt(GL_TEXTURE_WRAP_T, wrapMode);
        if (descriptor.kind() == TextureKind.TEXTURE_3D) {
            mutator.parameterInt(GL12.GL_TEXTURE_WRAP_R, wrapMode);
        } else if (descriptor.kind() != TextureKind.TEXTURE_2D) {
            mutator.parameterInt(GL12.GL_TEXTURE_WRAP_R, GL_CLAMP_TO_EDGE);
        }
        if (descriptor.format() == TextureFormat.DEPTH32F && descriptor.usage() == TextureUsage.SAMPLED_DEPTH_SHADOW) {
            mutator.parameterInt(GL_TEXTURE_COMPARE_MODE, GL_COMPARE_REF_TO_TEXTURE);
            mutator.parameterInt(GL_TEXTURE_COMPARE_FUNC, GL_LEQUAL);
        }
        configureAnisotropy(mutator, descriptor);
    }

    private void configureAnisotropy(TextureMutator mutator, TextureDescriptor descriptor) {
        if (descriptor.mipLevels() <= 1 || descriptor.samplerFilter() == SamplerFilter.NEAREST) {
            return;
        }
        int supported = maximumAnisotropy();
        if (supported <= TextureDescriptor.NO_ANISOTROPY) {
            return;
        }
        int requested = Math.min(descriptor.anisotropy(), supported);
        if (requested > TextureDescriptor.NO_ANISOTROPY) {
            mutator.parameterFloat(GL_TEXTURE_MAX_ANISOTROPY_EXT, requested);
        }
    }

    private int maximumAnisotropy() {
        if (maximumAnisotropy == UNKNOWN_ANISOTROPY) {
            maximumAnisotropy = GL.getCapabilities().GL_EXT_texture_filter_anisotropic
                    ? glGetInteger(GL_MAX_TEXTURE_MAX_ANISOTROPY_EXT)
                    : TextureDescriptor.NO_ANISOTROPY;
        }
        return maximumAnisotropy;
    }

    private static int wrapToGl(TextureWrap wrap) {
        return switch (wrap) {
            case CLAMP_TO_EDGE -> GL_CLAMP_TO_EDGE;
            case REPEAT -> GL_REPEAT;
            case MIRRORED_REPEAT -> GL_MIRRORED_REPEAT;
        };
    }

    private static int textureTargetToGl(TextureKind kind) {
        return switch (kind) {
            case TEXTURE_2D -> GL_TEXTURE_2D;
            case TEXTURE_3D -> GL12.GL_TEXTURE_3D;
            case CUBEMAP -> GL13.GL_TEXTURE_CUBE_MAP;
            case ARRAY_2D -> GL30.GL_TEXTURE_2D_ARRAY;
        };
    }

    @Override
    public RenderTargetHandle createRenderTarget(RenderTargetDescriptor descriptor) {
        int fboId = createFramebufferObject();
        attachColorTextures(fboId, descriptor);
        descriptor.depthAttachment().ifPresent(depth -> attachDepthTexture(fboId, descriptor, depth));
        if (descriptor.colorAttachments().isEmpty()) {
            disableColorBuffers(fboId);
        }
        verifyFramebufferComplete(fboId);
        long id = nextId.getAndIncrement();
        renderTargets.put(id, new RenderTargetResource(fboId, descriptor.width(), descriptor.height()));
        return new RenderTargetHandle(id);
    }

    private int createFramebufferObject() {
        if (directStateAccessSupported) {
            return glCreateFramebuffers();
        }
        int fboId = glGenFramebuffers();
        glBindFramebuffer(GL_FRAMEBUFFER, fboId);
        return fboId;
    }

    private void disableColorBuffers(int fboId) {
        if (directStateAccessSupported) {
            glNamedFramebufferDrawBuffer(fboId, GL_NONE);
            glNamedFramebufferReadBuffer(fboId, GL_NONE);
            return;
        }
        glDrawBuffer(GL_NONE);
        glReadBuffer(GL_NONE);
    }

    private void verifyFramebufferComplete(int fboId) {
        int status = directStateAccessSupported
                ? glCheckNamedFramebufferStatus(fboId, GL_FRAMEBUFFER)
                : glCheckFramebufferStatus(GL_FRAMEBUFFER);
        if (status != GL_FRAMEBUFFER_COMPLETE) {
            throw new EpysiaException("Render target framebuffer is incomplete.");
        }
        if (!directStateAccessSupported) {
            glBindFramebuffer(GL_FRAMEBUFFER, 0);
        }
    }

    private void attachColorTextures(int fboId, RenderTargetDescriptor descriptor) {
        int attachmentIndex = 0;
        for (TextureHandle color : descriptor.colorAttachments()) {
            TextureResource resource = requireTexture(color);
            attachTexture(fboId, GL_COLOR_ATTACHMENT0 + attachmentIndex, resource,
                    descriptor.colorLayer(), descriptor.colorMipLevel());
            attachmentIndex++;
        }
    }

    private void attachDepthTexture(int fboId, RenderTargetDescriptor descriptor, TextureHandle depth) {
        TextureResource resource = requireTexture(depth);
        int attachment = resource.format() == TextureFormat.DEPTH32F_STENCIL8
                ? GL_DEPTH_STENCIL_ATTACHMENT
                : GL_DEPTH_ATTACHMENT;
        attachTexture(fboId, attachment, resource, descriptor.depthLayer(), 0);
    }

    private void attachTexture(int fboId, int attachmentPoint, TextureResource resource, int layer, int mipLevel) {
        if (directStateAccessSupported) {
            attachTextureNamed(fboId, attachmentPoint, resource, layer, mipLevel);
            return;
        }
        switch (resource.kind()) {
            case TEXTURE_2D -> glFramebufferTexture2D(GL_FRAMEBUFFER, attachmentPoint,
                    GL_TEXTURE_2D, resource.textureId(), mipLevel);
            case CUBEMAP -> glFramebufferTexture2D(GL_FRAMEBUFFER, attachmentPoint,
                    GL13.GL_TEXTURE_CUBE_MAP_POSITIVE_X + Math.max(layer, 0),
                    resource.textureId(), mipLevel);
            case ARRAY_2D -> GL30.glFramebufferTextureLayer(GL_FRAMEBUFFER, attachmentPoint,
                    resource.textureId(), mipLevel, Math.max(layer, 0));
        }
    }

    private void attachTextureNamed(int fboId, int attachmentPoint, TextureResource resource, int layer, int mipLevel) {
        switch (resource.kind()) {
            case TEXTURE_2D -> glNamedFramebufferTexture(fboId, attachmentPoint,
                    resource.textureId(), mipLevel);
            case CUBEMAP, ARRAY_2D -> glNamedFramebufferTextureLayer(fboId, attachmentPoint,
                    resource.textureId(), mipLevel, Math.max(layer, 0));
        }
    }

    @Override
    public BindingSetHandle createBindingSet(BindingSetDescriptor descriptor) {
        List<ResolvedBinding> resolved = new ArrayList<>(descriptor.bindings().size());
        for (Binding binding : descriptor.bindings()) {
            resolved.add(resolveBinding(binding));
        }
        long id = nextId.getAndIncrement();
        bindingSets.put(id, buildBindingSet(resolved));
        return new BindingSetHandle(id);
    }

    private BindingSetResource buildBindingSet(List<ResolvedBinding> resolved) {
        int firstSlot = Integer.MAX_VALUE;
        int lastSlot = NO_TEXTURE_RANGE;
        for (ResolvedBinding binding : resolved) {
            if (binding instanceof ResolvedTexture texture) {
                firstSlot = Math.min(firstSlot, texture.slot());
                lastSlot = Math.max(lastSlot, texture.slot());
            }
        }
        if (!multiBindSupported || lastSlot == NO_TEXTURE_RANGE) {
            return new BindingSetResource(resolved, NO_TEXTURE_RANGE, new int[0]);
        }
        int[] textureIds = new int[lastSlot - firstSlot + 1];
        for (ResolvedBinding binding : resolved) {
            if (binding instanceof ResolvedTexture texture) {
                textureIds[texture.slot() - firstSlot] = texture.textureId();
            }
        }
        return new BindingSetResource(resolved, firstSlot, textureIds);
    }

    private ResolvedBinding resolveBinding(Binding binding) {
        return switch (binding.resource()) {
            case UniformBufferBinding ubo -> new ResolvedUbo(
                    binding.slotIndex(),
                    requireBuffer(ubo.buffer()),
                    ubo.byteOffset(),
                    ubo.byteSize()
            );
            case StorageImageBinding image -> new ResolvedStorageImage(
                    binding.slotIndex(), requireTexture(image.texture()).textureId(), image.mipLevel(),
                    storageImageAccess(image.access()),
                    internalFormatToGl(requireTexture(image.texture()).format()));
            case StorageBufferBinding storage -> new ResolvedStorage(
                    binding.slotIndex(),
                    requireBuffer(storage.buffer()),
                    storage.byteOffset(),
                    storage.byteSize()
            );
            case SampledTextureBinding texture -> {
                TextureResource resource = requireTexture(texture.texture());
                yield new ResolvedTexture(binding.slotIndex(), resource.textureId(), textureTargetToGl(resource.kind()));
            }
        };
    }

    @Override
    public void writeBuffer(BufferHandle handle, ByteBuffer data, long byteOffset) {
        BufferResource resource = requireBuffer(handle);
        if (resource.ringBuffered()) {
            ByteBuffer mapping = resource.mapping(ringFrameSlot);
            mapping.clear();
            mapping.position((int) byteOffset);
            mapping.put(data.duplicate());
            return;
        }
        if (directStateAccessSupported && data.remaining() <= DIRECT_WRITE_BYTE_LIMIT) {
            glNamedBufferSubData(resource.bufferId(), byteOffset, data);
            return;
        }
        glBindBuffer(resource.glTarget(), resource.bufferId());
        glBufferSubData(resource.glTarget(), byteOffset, data);
    }

    @Override
    public int uniformBufferOffsetAlignment() {
        return Math.max(1, glGetInteger(GL_UNIFORM_BUFFER_OFFSET_ALIGNMENT));
    }

    @Override
    public int perFrameBufferSlots() {
        return persistentMappingSupported ? BufferResource.RING_SLOTS : 1;
    }

    @Override
    public void writeTexture(TextureHandle handle, ByteBuffer rgbaPixels) {
        TextureResource resource = requireTexture(handle);
        if (directStateAccessSupported) {
            glTextureSubImage2D(resource.textureId(), 0, 0, 0, resource.width(), resource.height(),
                    GL_RGBA, GL_UNSIGNED_BYTE, rgbaPixels);
            return;
        }
        glBindTexture(GL_TEXTURE_2D, resource.textureId());
        glTexSubImage2D(GL_TEXTURE_2D, 0, 0, 0, resource.width(), resource.height(), GL_RGBA, GL_UNSIGNED_BYTE, rgbaPixels);
    }

    @Override
    public void writeTexture(TextureHandle handle, java.nio.FloatBuffer rgbaPixels) {
        TextureResource resource = requireTexture(handle);
        if (directStateAccessSupported) {
            glTextureSubImage2D(resource.textureId(), 0, 0, 0, resource.width(), resource.height(),
                    GL_RGBA, GL_FLOAT, rgbaPixels);
            return;
        }
        glBindTexture(GL_TEXTURE_2D, resource.textureId());
        glTexSubImage2D(GL_TEXTURE_2D, 0, 0, 0, resource.width(), resource.height(),
                GL_RGBA, GL_FLOAT, rgbaPixels);
    }

    @Override
    public void generateMipmaps(TextureHandle handle) {
        TextureResource resource = requireTexture(handle);
        if (directStateAccessSupported) {
            glGenerateTextureMipmap(resource.textureId());
            return;
        }
        glBindTexture(GL_TEXTURE_2D, resource.textureId());
        GL30.glGenerateMipmap(GL_TEXTURE_2D);
    }

    @Override
    public void readTextureLevel(TextureHandle handle, int mipLevel, java.nio.FloatBuffer destination) {
        TextureResource resource = requireTexture(handle);
        if (directStateAccessSupported) {
            glGetTextureImage(resource.textureId(), mipLevel,
                    pixelFormatToGl(resource.format()), GL_FLOAT, destination);
            return;
        }
        int glTarget = textureTargetToGl(resource.kind());
        glBindTexture(glTarget, resource.textureId());
        GL11.glGetTexImage(glTarget, mipLevel,
                pixelFormatToGl(resource.format()), GL_FLOAT, destination);
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
    public void copyTextureLayer(TextureHandle source, int sourceLayer,
                                 TextureHandle destination, int destinationLayer) {
        TextureResource from = requireTexture(source);
        copyTextureRegion(source, sourceLayer, 0, 0, destination, destinationLayer, 0, 0,
                from.width(), from.height());
    }

    @Override
    public void copyTextureRegion(TextureHandle source, int sourceLayer, int sourceX, int sourceY,
                                  TextureHandle destination, int destinationLayer,
                                  int destinationX, int destinationY, int width, int height) {
        TextureResource from = requireTexture(source);
        TextureResource to = requireTexture(destination);
        if (from.format() != to.format() || from.width() != to.width() || from.height() != to.height()) {
            throw new EpysiaException("copyTextureRegion requires matching format and dimensions.");
        }
        glCopyImageSubData(
                from.textureId(), textureTargetToGl(from.kind()), 0, sourceX, sourceY, sourceLayer,
                to.textureId(), textureTargetToGl(to.kind()), 0, destinationX, destinationY, destinationLayer,
                width, height, 1);
    }

    @Override
    public void updatePipelineShaders(PipelineHandle handle, ShaderSource shaders) {
        PipelineResource existing = requirePipeline(handle);
        int newProgram = compileProgram(shaders);
        glDeleteProgram(existing.programId());
        pipelines.put(handle.id(), new PipelineResource(newProgram, existing.vaoId(), existing.state(), existing.vertexStride(), existing.instanceStride()));
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
    public void setUniformSlotOverride(int slot, BufferHandle buffer, long byteOffset, long byteSize) {
        uniformOverrideSlot = slot;
        uniformOverrideBuffer = requireBuffer(buffer);
        uniformOverrideOffset = byteOffset;
        uniformOverrideSize = byteSize;
        currentBindingSetId = 0L;
        bindUniformRange(slot, uniformOverrideBuffer, byteOffset, byteSize);
    }

    @Override
    public void clearUniformSlotOverride() {
        uniformOverrideSlot = -1;
        uniformOverrideBuffer = null;
        currentBindingSetId = 0L;
    }

    private void destroyBufferResource(BufferResource resource) {
        makeBufferNonResident(resource);
        for (int bufferId : resource.bufferIds()) {
            if (resource.ringBuffered()) {
                unmapBuffer(resource.glTarget(), bufferId);
            }
            glDeleteBuffers(bufferId);
        }
    }

    private void unmapBuffer(int glTarget, int bufferId) {
        if (directStateAccessSupported) {
            glUnmapNamedBuffer(bufferId);
            return;
        }
        glBindBuffer(glTarget, bufferId);
        glUnmapBuffer(glTarget);
    }

    @Override
    public void destroy(BufferHandle handle) {
        BufferResource resource = buffers.remove(handle.id());
        if (resource != null) {
            destroyBufferResource(resource);
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
        if (bindingSets.remove(handle.id()) != null && TRACK_BINDING_SETS) {
            bindingSetGraveyard.put(handle.id(), describeCurrentSite());
        }
    }

    private String describeCurrentSite() {
        StringBuilder site = new StringBuilder("frame ").append(frameIndex);
        StackWalker.getInstance()
                .walk(frames -> frames.skip(2).limit(8).map(StackWalker.StackFrame::toString).toList())
                .forEach(frame -> site.append("\n        at ").append(frame));
        return site.toString();
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
        frameIndex++;
        drainProfileQueries();
        frameShaderCompileNanos = 0L;
        currentProfileSection = 0;
        suppressedSectionDepth = 0;
        drawStatistics.reset();
        waitForRingSlot();
        if (profileQueriesAllocated) {
            glQueryCounter(frameStartQueries[profileFrameSlot], GL_TIMESTAMP);
        }
    }

    private void waitForRingSlot() {
        long fence = ringFrameFences[ringFrameSlot];
        if (fence == 0L) {
            return;
        }
        glClientWaitSync(fence, GL_SYNC_FLUSH_COMMANDS_BIT, 1_000_000_000L);
        glDeleteSync(fence);
        ringFrameFences[ringFrameSlot] = 0L;
    }

    @Override
    public DrawStatistics drawStatistics() {
        return drawStatistics;
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
        if (!profileResultsAvailable(readSlot, sectionCount)) {
            profileStallCount++;
            if (profileStallCount % 240 == 1) {
                System.out.println("[gpu-profiling] results not ready for " + sectionCount
                        + " sections (stall " + profileStallCount + ")");
            }
            return;
        }
        latestTimings.clear();
        for (int i = 0; i < sectionCount; i++) {
            long nanos = glGetQueryObjectui64(profileQueries[readSlot][i], GL_QUERY_RESULT);
            String name = profileSectionNames[readSlot][i];
            latestTimings.merge(name, nanos, Long::sum);
        }
        if (glGetQueryObjecti(frameEndQueries[readSlot], GL_QUERY_RESULT_AVAILABLE) != GL_FALSE) {
            long start = glGetQueryObjectui64(frameStartQueries[readSlot], GL_QUERY_RESULT);
            long end = glGetQueryObjectui64(frameEndQueries[readSlot], GL_QUERY_RESULT);
            latestTimings.put("frameTotal", end - start);
        }
    }

    private boolean profileResultsAvailable(int readSlot, int sectionCount) {
        for (int i = 0; i < sectionCount; i++) {
            if (glGetQueryObjecti(profileQueries[readSlot][i], GL_QUERY_RESULT_AVAILABLE) == GL_FALSE) {
                return false;
            }
        }
        return true;
    }

    @Override
    public void beginProfileSection(String name) {
        if (!profileQueriesAllocated || profileSectionActive || currentProfileSection >= MAX_PROFILE_SECTIONS) {
            suppressedSectionDepth++;
            return;
        }
        profileSectionNames[profileFrameSlot][currentProfileSection] = name;
        glBeginQuery(GL_TIME_ELAPSED, profileQueries[profileFrameSlot][currentProfileSection]);
        profileSectionActive = true;
    }

    @Override
    public void endProfileSection() {
        if (suppressedSectionDepth > 0) {
            suppressedSectionDepth--;
            return;
        }
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
    public void setPassViewport(int x, int y, int width, int height) {
        pendingViewport = width <= 0 || height <= 0 ? null : new int[] {x, y, width, height};
    }

    private void applyPassViewport(int targetWidth, int targetHeight) {
        if (pendingViewport == null) {
            glViewport(0, 0, targetWidth, targetHeight);
            return;
        }
        glViewport(pendingViewport[0], pendingViewport[1], pendingViewport[2], pendingViewport[3]);
        pendingViewport = null;
    }

    @Override
    public void beginPass(RenderTargetHandle target, PassClear clear) {
        if (target.id() == 0L) {
            glBindFramebuffer(GL_FRAMEBUFFER, 0);
            currentTargetHeight = screenHeight;
            applyPassViewport(screenWidth, screenHeight);
        } else {
            RenderTargetResource resource = requireRenderTarget(target);
            glBindFramebuffer(GL_FRAMEBUFFER, resource.fboId());
            currentTargetHeight = resource.height();
            applyPassViewport(resource.width(), resource.height());
        }
        disableScissor();
        currentPipeline = null;
        appliedRenderState = null;
        currentBindingSetId = 0L;
        currentVertexBufferId = 0;
        currentIndexBufferId = 0;
        drawStatistics.recordPass();
        GL11.glDepthMask(true);
        GL11.glColorMask(true, true, true, true);
        applyClear(clear);
    }

    private void disableScissor() {
        glDisable(GL11.GL_SCISSOR_TEST);
        appliedScissor = ScissorRect.disabled();
    }

    private void applyScissor(ScissorRect rect) {
        if (rect.equals(appliedScissor)) {
            return;
        }
        if (!rect.enabled()) {
            disableScissor();
            return;
        }
        glEnable(GL11.GL_SCISSOR_TEST);
        GL11.glScissor(rect.x(), currentTargetHeight - rect.y() - rect.height(),
                rect.width(), rect.height());
        appliedScissor = rect;
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
        if (clear.clearStencil()) {
            glStencilMask(StencilState.ALL_BITS);
            glClearStencil(clear.stencil());
            mask |= GL_STENCIL_BUFFER_BIT;
            appliedRenderState = null;
        }
        if (mask != 0) {
            glClear(mask);
        }
    }

    @Override
    public void execute(DrawCommand command) {
        PipelineResource pipeline = pipelines.get(command.pipeline().id());
        MeshResource mesh = meshes.get(command.mesh().id());
        if (pipeline == null || mesh == null) {
            drawStatistics.recordDroppedDraw();
            return;
        }
        if (currentPipeline != pipeline) {
            applyPipeline(pipeline);
            currentPipeline = pipeline;
            currentVertexBufferId = 0;
            currentIndexBufferId = 0;
            drawStatistics.recordPipelineSwitch();
        }
        if (currentBindingSetId != command.bindings().id()) {
            applyBindings(command.bindings());
            currentBindingSetId = command.bindings().id();
            drawStatistics.recordBindingSetSwitch();
        }
        if (currentVertexBufferId != mesh.vertexBufferId()) {
            glBindVertexBuffer(VERTEX_BINDING_INDEX, mesh.vertexBufferId(), 0L, pipeline.vertexStride());
            currentVertexBufferId = mesh.vertexBufferId();
        }
        if (currentIndexBufferId != mesh.indexBufferId()) {
            glBindBuffer(GL_ELEMENT_ARRAY_BUFFER, mesh.indexBufferId());
            currentIndexBufferId = mesh.indexBufferId();
        }
        applyScissor(command.scissor());
        int indexCount = command.indexCountOverride() == DrawCommand.USE_MESH_INDEX_COUNT
                ? mesh.indexCount()
                : command.indexCountOverride();
        int firstIndex = command.firstIndexOverride() == DrawCommand.USE_MESH_FIRST_INDEX
                ? mesh.firstIndex()
                : command.firstIndexOverride();
        long indexOffset = (long) firstIndex * mesh.indexFormat().byteSize();
        if (command.indirectBuffer() != null) {
            executeIndirect(command, pipeline, mesh, indexCount);
            return;
        }
        boolean hasInstanceAttributes = command.instanceBuffer() != null && pipeline.instanceStride() > 0;
        if (hasInstanceAttributes || command.instanceCount() > 1) {
            if (hasInstanceAttributes) {
                BufferResource instanceBuffer = requireBuffer(command.instanceBuffer());
                glBindVertexBuffer(INSTANCE_BINDING_INDEX, instanceBuffer.bufferId(), 0L, pipeline.instanceStride());
            }
            drawStatistics.recordDraw(pipeline.state().topology(), indexCount, command.instanceCount(), true);
            glDrawElementsInstanced(
                    topologyToGl(pipeline.state().topology()),
                    indexCount,
                    indexFormatToGl(mesh.indexFormat()),
                    indexOffset,
                    command.instanceCount()
            );
            return;
        }
        drawStatistics.recordDraw(pipeline.state().topology(), indexCount, 1, false);
        glDrawElements(
                topologyToGl(pipeline.state().topology()),
                indexCount,
                indexFormatToGl(mesh.indexFormat()),
                indexOffset
        );
    }

    private void executeIndirect(DrawCommand command, PipelineResource pipeline,
                                 MeshResource mesh, int indexCount) {
        BufferResource indirectBuffer = requireBuffer(command.indirectBuffer());
        glBindBuffer(GL40.GL_DRAW_INDIRECT_BUFFER, indirectBuffer.bufferId());
        bindPerDrawBuffer(command, pipeline);
        if (!command.isMultiDraw()) {
            drawStatistics.recordDraw(pipeline.state().topology(), indexCount, 1, true);
            GL40.glDrawElementsIndirect(
                    topologyToGl(pipeline.state().topology()),
                    indexFormatToGl(mesh.indexFormat()),
                    0L
            );
            return;
        }
        drawStatistics.recordDraw(pipeline.state().topology(), indexCount, command.indirectDrawCount(), true);
        GL43.glMultiDrawElementsIndirect(
                topologyToGl(pipeline.state().topology()),
                indexFormatToGl(mesh.indexFormat()),
                0L,
                command.indirectDrawCount(),
                0
        );
    }

    private void bindPerDrawBuffer(DrawCommand command, PipelineResource pipeline) {
        if (command.instanceBuffer() == null || pipeline.instanceStride() <= 0) {
            return;
        }
        BufferResource perDraw = requireBuffer(command.instanceBuffer());
        glBindVertexBuffer(INSTANCE_BINDING_INDEX, perDraw.bufferId(), 0L, pipeline.instanceStride());
    }

    @Override
    public void endPass() {
    }

    @Override
    public void endFrame() {
        if (profileQueriesAllocated) {
            glQueryCounter(frameEndQueries[profileFrameSlot], GL_TIMESTAMP);
        }
        profileSectionCounts[profileFrameSlot] = currentProfileSection;
        profileFrameSlot = (profileFrameSlot + 1) % PROFILE_FRAME_LAG;
        ringFrameFences[ringFrameSlot] = glFenceSync(GL_SYNC_GPU_COMMANDS_COMPLETE, 0);
        ringFrameSlot = (ringFrameSlot + 1) % BufferResource.RING_SLOTS;
    }

    @Override
    public int readPixelArgb(RenderTargetHandle target, int x, int y) {
        int previousReadFbo = beginPixelRead(target);
        java.nio.ByteBuffer pixel = BufferUtils.createByteBuffer(4);
        GL11.glReadPixels(x, y, 1, 1, GL_RGBA, GL11.GL_UNSIGNED_BYTE, pixel);
        endPixelRead(target, previousReadFbo);
        int r = pixel.get(0) & 0xFF;
        int g = pixel.get(1) & 0xFF;
        int b = pixel.get(2) & 0xFF;
        int a = pixel.get(3) & 0xFF;
        return (a << 24) | (r << 16) | (g << 8) | b;
    }

    @Override
    public PixelColor readPixelFloat(RenderTargetHandle target, int x, int y) {
        int previousReadFbo = beginPixelRead(target);
        java.nio.FloatBuffer pixel = BufferUtils.createFloatBuffer(4);
        GL11.glReadPixels(x, y, 1, 1, GL_RGBA, GL11.GL_FLOAT, pixel);
        endPixelRead(target, previousReadFbo);
        return new PixelColor(pixel.get(0), pixel.get(1), pixel.get(2), pixel.get(3));
    }

    private int beginPixelRead(RenderTargetHandle target) {
        int previousReadFbo = GL11.glGetInteger(GL30.GL_READ_FRAMEBUFFER_BINDING);
        if (target.id() == RenderTargetHandle.SCREEN.id()) {
            GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, 0);
            GL11.glReadBuffer(GL11.GL_FRONT);
        } else {
            GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, requireRenderTarget(target).fboId());
        }
        return previousReadFbo;
    }

    @Override
    public void readPixelsRgba(RenderTargetHandle target, int x, int y, int width, int height,
                               java.nio.ByteBuffer destination) {
        int previousReadFbo = beginPixelRead(target);
        GL11.glPixelStorei(GL11.GL_PACK_ALIGNMENT, 1);
        GL11.glReadPixels(x, y, width, height, GL_RGBA,
                GL11.GL_UNSIGNED_BYTE, destination);
        endPixelRead(target, previousReadFbo);
    }

    private void endPixelRead(RenderTargetHandle target, int previousReadFbo) {
        if (target.id() == RenderTargetHandle.SCREEN.id()) {
            GL11.glReadBuffer(GL11.GL_BACK);
        }
        GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, previousReadFbo);
    }

    private void applyPipeline(PipelineResource pipeline) {
        glUseProgram(pipeline.programId());
        glBindVertexArray(pipeline.vaoId());
        RenderState state = pipeline.state();
        if (appliedRenderState == null || appliedRenderState.depthTest() != state.depthTest()) {
            applyDepthTest(state.depthTest());
        }
        if (appliedRenderState == null || appliedRenderState.blendMode() != state.blendMode()) {
            applyBlendMode(state.blendMode());
        }
        if (appliedRenderState == null || appliedRenderState.cullMode() != state.cullMode()) {
            applyCullMode(state.cullMode());
        }
        if (appliedRenderState == null || appliedRenderState.colorWrite() != state.colorWrite()) {
            boolean write = state.colorWrite();
            GL11.glColorMask(write, write, write, write);
        }
        if (appliedRenderState == null || appliedRenderState.depthWrite() != state.depthWrite()) {
            GL11.glDepthMask(state.depthWrite());
        }
        if (appliedRenderState == null || appliedRenderState.depthClamp() != state.depthClamp()) {
            if (state.depthClamp()) {
                glEnable(GL32.GL_DEPTH_CLAMP);
            } else {
                glDisable(GL32.GL_DEPTH_CLAMP);
            }
        }
        applyStencilState(state.stencil());
        appliedRenderState = state;
    }

    private void applyStencilState(StencilState stencil) {
        if (appliedRenderState != null && appliedRenderState.stencil().equals(stencil)) {
            return;
        }
        if (!stencil.enabled()) {
            glDisable(GL_STENCIL_TEST);
            glStencilMask(StencilState.ALL_BITS);
            return;
        }
        glEnable(GL_STENCIL_TEST);
        glStencilFunc(stencilTestToGl(stencil.test()), stencil.reference(), stencil.compareMask());
        glStencilMask(stencil.writeMask());
        glStencilOp(stencilOperationToGl(stencil.onStencilFail()),
                stencilOperationToGl(stencil.onDepthFail()),
                stencilOperationToGl(stencil.onPass()));
    }

    private static int stencilTestToGl(StencilTest test) {
        return switch (test) {
            case NEVER -> GL_NEVER;
            case LESS -> GL_LESS;
            case LESS_EQUAL -> GL_LEQUAL;
            case GREATER -> GL_GREATER;
            case GREATER_EQUAL -> GL_GEQUAL;
            case EQUAL -> GL_EQUAL;
            case NOT_EQUAL -> GL_NOTEQUAL;
            case ALWAYS -> GL_ALWAYS;
        };
    }

    private static int stencilOperationToGl(StencilOperation operation) {
        return switch (operation) {
            case KEEP -> GL_KEEP;
            case ZERO -> GL_ZERO;
            case REPLACE -> GL_REPLACE;
            case INCREMENT_CLAMP -> GL_INCR;
            case DECREMENT_CLAMP -> GL_DECR;
            case INVERT -> GL_INVERT;
            case INCREMENT_WRAP -> GL_INCR_WRAP;
            case DECREMENT_WRAP -> GL_DECR_WRAP;
        };
    }

    private void applyBindings(BindingSetHandle handle) {
        if (handle.id() == 0L) {
            return;
        }
        BindingSetResource resource = requireBindingSet(handle);
        if (resource.firstTextureSlot() == NO_TEXTURE_RANGE) {
            for (ResolvedBinding binding : resource.bindings()) {
                applyResolvedBinding(binding);
            }
            return;
        }
        glBindTextures(resource.firstTextureSlot(), resource.textureIds());
        for (ResolvedBinding binding : resource.bindings()) {
            if (!(binding instanceof ResolvedTexture)) {
                applyResolvedBinding(binding);
            }
        }
    }

    private void applyResolvedBinding(ResolvedBinding binding) {
        switch (binding) {
            case ResolvedUbo ubo -> {
                if (ubo.slot() == uniformOverrideSlot) {
                    bindUniformRange(ubo.slot(), uniformOverrideBuffer, uniformOverrideOffset, uniformOverrideSize);
                } else {
                    bindUniformRange(ubo.slot(), ubo.buffer(), ubo.offset(), ubo.size());
                }
            }
            case ResolvedStorageImage image -> GL42.glBindImageTexture(
                    image.slot(), image.textureId(), image.mipLevel(), true, 0, image.access(), image.format());
            case ResolvedStorage storage -> glBindBufferRange(GL_SHADER_STORAGE_BUFFER, storage.slot(),
                    storage.buffer().bufferId(ringFrameSlot), storage.offset(), storage.size());
            case ResolvedTexture texture -> {
                glActiveTexture(GL_TEXTURE0 + texture.slot());
                glBindTexture(texture.glTarget(), texture.textureId());
            }
        }
    }

    private void bindUniformRange(int slot, BufferResource buffer, long byteOffset, long byteSize) {
        if (buffer.resident()) {
            NVVertexBufferUnifiedMemory.glBufferAddressRangeNV(
                    NVUniformBufferUnifiedMemory.GL_UNIFORM_BUFFER_ADDRESS_NV, slot,
                    buffer.gpuAddress(ringFrameSlot) + byteOffset, byteSize);
            return;
        }
        glBindBufferRange(GL_UNIFORM_BUFFER, slot, buffer.bufferId(ringFrameSlot), byteOffset, byteSize);
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
            case ADDITIVE -> {
                glEnable(GL_BLEND);
                glBlendFuncSeparate(GL11.GL_ONE, GL11.GL_ONE,
                        GL11.GL_ONE, GL11.GL_ONE);
            }
            case MULTIPLY -> {
                glEnable(GL_BLEND);
                glBlendFuncSeparate(GL11.GL_DST_COLOR, GL11.GL_ZERO,
                        GL11.GL_DST_ALPHA, GL11.GL_ZERO);
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
            case STORAGE -> GL_SHADER_STORAGE_BUFFER;
            case INDIRECT -> GL40.GL_DRAW_INDIRECT_BUFFER;
        };
    }

    private BufferResource createRingBuffer(int glTarget, ByteBuffer initialData) {
        int byteSize = initialData.remaining();
        int storageFlags = GL_MAP_WRITE_BIT | GL_MAP_PERSISTENT_BIT | GL_MAP_COHERENT_BIT;
        int[] bufferIds = new int[BufferResource.RING_SLOTS];
        ByteBuffer[] mappings = new ByteBuffer[BufferResource.RING_SLOTS];
        for (int slot = 0; slot < BufferResource.RING_SLOTS; slot++) {
            bufferIds[slot] = allocateRingSlot(glTarget, initialData.duplicate(), storageFlags);
            mappings[slot] = mapRingSlot(glTarget, bufferIds[slot], byteSize, storageFlags);
        }
        return BufferResource.ring(bufferIds, mappings,
                makeUniformBufferResident(glTarget, bufferIds), glTarget, byteSize);
    }

    private int allocateRingSlot(int glTarget, ByteBuffer initialData, int storageFlags) {
        if (directStateAccessSupported) {
            int bufferId = glCreateBuffers();
            glNamedBufferStorage(bufferId, initialData, storageFlags);
            return bufferId;
        }
        int bufferId = glGenBuffers();
        glBindBuffer(glTarget, bufferId);
        glBufferStorage(glTarget, initialData, storageFlags);
        return bufferId;
    }

    private ByteBuffer mapRingSlot(int glTarget, int bufferId, int byteSize, int storageFlags) {
        if (directStateAccessSupported) {
            return glMapNamedBufferRange(bufferId, 0L, byteSize, storageFlags);
        }
        glBindBuffer(glTarget, bufferId);
        return glMapBufferRange(glTarget, 0L, byteSize, storageFlags);
    }

    private static boolean isDynamicUsage(BufferUsage usage) {
        return usage == BufferUsage.UNIFORM || usage == BufferUsage.STORAGE
                || usage == BufferUsage.INDIRECT;
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

    @Override
    public boolean isAlive(MeshHandle handle) {
        return meshes.containsKey(handle.id());
    }

    private MeshResource requireMesh(MeshHandle handle) {
        MeshResource resource = meshes.get(handle.id());
        if (resource == null) {
            throw new EpysiaException("Unknown mesh handle: " + handle.id());
        }
        return resource;
    }

    @Override
    public boolean isAlive(PipelineHandle handle) {
        return pipelines.containsKey(handle.id());
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

    public void updateTextureFilter(TextureHandle handle, SamplerFilter filter) {
        TextureResource resource = requireTexture(handle);
        TextureMutator mutator = textureMutator(resource.textureId(), textureTargetToGl(resource.kind()));
        int glFilter = filter == SamplerFilter.NEAREST ? GL_NEAREST : GL_LINEAR;
        mutator.parameterInt(GL_TEXTURE_MIN_FILTER, glFilter);
        mutator.parameterInt(GL_TEXTURE_MAG_FILTER, glFilter);
    }

    public void updateTextureWrap(TextureHandle handle, TextureWrap wrap) {
        TextureResource resource = requireTexture(handle);
        TextureMutator mutator = textureMutator(resource.textureId(), textureTargetToGl(resource.kind()));
        mutator.parameterInt(GL_TEXTURE_WRAP_S, wrapToGl(wrap));
        mutator.parameterInt(GL_TEXTURE_WRAP_T, wrapToGl(wrap));
    }

    @Override
    public boolean hasTexture(TextureHandle handle) {
        return textures.containsKey(handle.id());
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
            throw new EpysiaException("Unknown binding set handle: " + handle.id()
                    + " while executing in frame " + frameIndex + describeDestruction(handle));
        }
        return resource;
    }

    private String describeDestruction(BindingSetHandle handle) {
        if (!TRACK_BINDING_SETS) {
            return " ";
        }
        String site = bindingSetGraveyard.get(handle.id());
        return (site == null ? ", never created" : ", destroyed in " + site)
                + "\n    submitted from "
                + Frame.submitSiteOf(handle.id());
    }

    private record PipelineResource(int programId, int vaoId, RenderState state, int vertexStride, int instanceStride) {
        static PipelineResource compute(int programId) {
            return new PipelineResource(programId, 0, null, 0, 0);
        }
    }

    private record MeshResource(int vertexBufferId, int indexBufferId, int firstIndex, int indexCount, IndexFormat indexFormat) {
    }

    private static final class BufferResource {
        static final int RING_SLOTS = 3;

        private final int[] bufferIds;
        private final ByteBuffer[] mappings;
        private final long[] gpuAddresses;
        private final int glTarget;
        private final int byteSize;
        private final int glUsage;

        private BufferResource(int[] bufferIds, ByteBuffer[] mappings, long[] gpuAddresses,
                               int glTarget, int byteSize, int glUsage) {
            this.bufferIds = bufferIds;
            this.mappings = mappings;
            this.gpuAddresses = gpuAddresses;
            this.glTarget = glTarget;
            this.byteSize = byteSize;
            this.glUsage = glUsage;
        }

        static BufferResource single(int[] bufferIds, long[] gpuAddresses, int glTarget, int byteSize, int glUsage) {
            return new BufferResource(bufferIds, new ByteBuffer[0], gpuAddresses, glTarget, byteSize, glUsage);
        }

        static BufferResource ring(int[] bufferIds, ByteBuffer[] mappings, long[] gpuAddresses,
                                   int glTarget, int byteSize) {
            return new BufferResource(bufferIds, mappings, gpuAddresses, glTarget, byteSize, GL_DYNAMIC_DRAW);
        }

        boolean resident() {
            return gpuAddresses.length > 0;
        }

        long gpuAddress(int frameSlot) {
            return gpuAddresses[frameSlot % gpuAddresses.length];
        }

        boolean ringBuffered() {
            return mappings.length > 0;
        }

        int bufferId() {
            return bufferIds[0];
        }

        int bufferId(int frameSlot) {
            return bufferIds[frameSlot % bufferIds.length];
        }

        ByteBuffer mapping(int frameSlot) {
            return mappings[frameSlot % mappings.length];
        }

        int[] bufferIds() {
            return bufferIds;
        }

        int glTarget() {
            return glTarget;
        }

        int byteSize() {
            return byteSize;
        }

        int glUsage() {
            return glUsage;
        }
    }

    private record TextureResource(int textureId, int width, int height, TextureFormat format, TextureKind kind) {
    }

    private record RenderTargetResource(int fboId, int width, int height) {
    }

    private record BindingSetResource(List<ResolvedBinding> bindings, int firstTextureSlot, int[] textureIds) {
    }

    private sealed interface ResolvedBinding permits ResolvedUbo, ResolvedStorage, ResolvedTexture, ResolvedStorageImage {
    }

    private record ResolvedUbo(int slot, BufferResource buffer, long offset, long size) implements ResolvedBinding {
    }

    private record ResolvedStorageImage(int slot, int textureId, int mipLevel, int access, int format)
            implements ResolvedBinding {
    }

    private record ResolvedStorage(int slot, BufferResource buffer, long offset, long size) implements ResolvedBinding {
    }

    private record ResolvedTexture(int slot, int textureId, int glTarget) implements ResolvedBinding {
    }

    private sealed interface TextureMutator permits BoundTextureMutator, NamedTextureMutator {

        void storage2d(int levels, int internalFormat, int width, int height);

        void storage3d(int levels, int internalFormat, int width, int height, int depth);

        void parameterInt(int name, int value);

        void parameterFloat(int name, float value);
    }

    private record BoundTextureMutator(int glTarget) implements TextureMutator {

        public void storage2d(int levels, int internalFormat, int width, int height) {
            GL42.glTexStorage2D(glTarget, levels, internalFormat, width, height);
        }

        public void storage3d(int levels, int internalFormat, int width, int height, int depth) {
            GL42.glTexStorage3D(glTarget, levels, internalFormat, width, height, depth);
        }

        public void parameterInt(int name, int value) {
            glTexParameteri(glTarget, name, value);
        }

        public void parameterFloat(int name, float value) {
            glTexParameterf(glTarget, name, value);
        }
    }

    private record NamedTextureMutator(int textureId) implements TextureMutator {

        public void storage2d(int levels, int internalFormat, int width, int height) {
            glTextureStorage2D(textureId, levels, internalFormat, width, height);
        }

        public void storage3d(int levels, int internalFormat, int width, int height, int depth) {
            glTextureStorage3D(textureId, levels, internalFormat, width, height, depth);
        }

        public void parameterInt(int name, int value) {
            glTextureParameteri(textureId, name, value);
        }

        public void parameterFloat(int name, float value) {
            glTextureParameterf(textureId, name, value);
        }
    }
}
