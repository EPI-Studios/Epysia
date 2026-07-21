package fr.epistudio.epysia.render.postfx;

import fr.epistudio.epysia.logging.Logger;
import fr.epistudio.epysia.render.backend.Binding;
import fr.epistudio.epysia.render.backend.BindingSetDescriptor;
import fr.epistudio.epysia.render.backend.BindingSetHandle;
import fr.epistudio.epysia.render.backend.BindingSetLayout;
import fr.epistudio.epysia.render.backend.BindingSlot;
import fr.epistudio.epysia.render.backend.BindingType;
import fr.epistudio.epysia.render.backend.BlendMode;
import fr.epistudio.epysia.render.backend.BufferDescriptor;
import fr.epistudio.epysia.render.backend.BufferHandle;
import fr.epistudio.epysia.render.backend.BufferUsage;
import fr.epistudio.epysia.render.backend.CullMode;
import fr.epistudio.epysia.render.backend.DepthTest;
import fr.epistudio.epysia.render.backend.DrawCommand;
import fr.epistudio.epysia.render.backend.PassClear;
import fr.epistudio.epysia.render.backend.PipelineDescriptor;
import fr.epistudio.epysia.render.backend.PipelineHandle;
import fr.epistudio.epysia.render.backend.RenderBackend;
import fr.epistudio.epysia.render.backend.RenderState;
import fr.epistudio.epysia.render.backend.RenderTargetDescriptor;
import fr.epistudio.epysia.render.backend.RenderTargetHandle;
import fr.epistudio.epysia.render.backend.SampledTextureBinding;
import fr.epistudio.epysia.render.backend.ShaderSource;
import fr.epistudio.epysia.render.backend.TextureDescriptor;
import fr.epistudio.epysia.render.backend.TextureFormat;
import fr.epistudio.epysia.render.backend.TextureHandle;
import fr.epistudio.epysia.render.backend.TextureUsage;
import fr.epistudio.epysia.render.backend.Topology;
import fr.epistudio.epysia.render.backend.UniformBufferBinding;
import fr.epistudio.epysia.render.environment.FullscreenQuad;
import fr.epistudio.epysia.render.shader.ShaderUniformParser.ParsedSource;
import fr.epistudio.epysia.render.shader.LoadedShader;
import fr.epistudio.epysia.render.shader.ShaderLoader;
import fr.epistudio.epysia.render.shader.ShaderWatcher;
import fr.epistudio.epysia.render.texture.Texture2D;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.lwjgl.BufferUtils;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import fr.epistudio.epysia.render.shader.ShaderUniformValue;
import fr.epistudio.epysia.render.shader.ShaderUniformDefaults;
import fr.epistudio.epysia.render.shader.ShaderUniformParser;
import fr.epistudio.epysia.render.shader.ShaderUniformDeclaration;
import fr.epistudio.epysia.render.shader.ShaderUniformPacker;

public final class PostEffectChain {

    private static final String TEMPLATE_PATH = "postfx/effect_template.frag.glsl";
    private static final String VERTEX_PATH = "post.vert.glsl";
    private static final int PING_PONG_COUNT = 2;
    private static final double NANOS_PER_SECOND = 1_000_000_000.0;
    private static final RenderState PASS_STATE = new RenderState(
            Topology.TRIANGLES, DepthTest.DISABLED, BlendMode.OPAQUE, CullMode.NONE);

    private final ShaderLoader shaderLoader;
    private final FullscreenQuad quad;
    private final Logger logger;
    private final long startNanos = System.nanoTime();
    private final List<CompiledEffect> compiledEffects = new ArrayList<>();
    private final Map<PostEffectInsertionPoint, TextureHandle[]> pingTextures = new EnumMap<>(PostEffectInsertionPoint.class);
    private final Map<PostEffectInsertionPoint, RenderTargetHandle[]> pingTargets = new EnumMap<>(PostEffectInsertionPoint.class);
    private final Map<PostEffectInsertionPoint, TextureHandle> chainOutputs = new EnumMap<>(PostEffectInsertionPoint.class);
    private final Map<String, TextureHandle> textureCache = new HashMap<>();
    private final Set<String> watchedPaths = new HashSet<>();
    private final ByteBuffer frameScratch = BufferUtils.createByteBuffer(PostEffectComposer.FRAME_UNIFORM_SIZE);

    private RenderBackend backend;
    private Optional<ShaderWatcher> shaderWatcher = Optional.empty();
    private TextureHandle fallbackTexture;
    private BufferHandle frameUniformBuffer;
    private float nearPlane = 0.1f;
    private float farPlane = 100.0f;
    private final Vector3f cameraPosition = new Vector3f();
    private final Matrix4f inverseViewProjection = new Matrix4f();
    private TextureHandle sceneColorTexture;
    private TextureHandle sceneDepthTexture;
    private TextureHandle ldrColorTexture;
    private int targetWidth;
    private int targetHeight;
    private PostEffectStack activeStack;
    private long activeStructureRevision = -1L;
    private boolean rebuildRequested = true;
    private volatile boolean reloadRequested;

    public PostEffectChain(ShaderLoader shaderLoader, FullscreenQuad quad, Logger logger) {
        this.shaderLoader = shaderLoader;
        this.quad = quad;
        this.logger = logger;
    }

    public void setShaderWatcher(ShaderWatcher watcher) {
        this.shaderWatcher = Optional.of(watcher);
    }

    public void initialize(RenderBackend backend) {
        this.backend = backend;
        fallbackTexture = Texture2D.whitePixel(backend);
        frameUniformBuffer = backend.createBuffer(new BufferDescriptor(BufferUsage.UNIFORM,
                BufferUtils.createByteBuffer(PostEffectComposer.FRAME_UNIFORM_SIZE)));
    }

    public void configure(TextureHandle sceneColor, TextureHandle sceneDepth, TextureHandle ldrColor,
                          int width, int height) {
        this.sceneColorTexture = sceneColor;
        this.sceneDepthTexture = sceneDepth;
        this.ldrColorTexture = ldrColor;
        this.targetWidth = width;
        this.targetHeight = height;
        chainOutputs.clear();
        rebuildRequested = true;
    }

    public void prepare(PostEffectStack stack) {
        shaderWatcher.ifPresent(ShaderWatcher::poll);
        if (rebuildRequired(stack)) {
            rebuild(stack);
        }
        writeFrameUniforms();
        writeEffectUniforms();
    }

    private boolean rebuildRequired(PostEffectStack stack) {
        if (rebuildRequested || reloadRequested) {
            return true;
        }
        if (stack == activeStack && stack.combinedStructureRevision() == activeStructureRevision) {
            return false;
        }
        if (compiledEffects.isEmpty() && countEnabled(stack) == 0) {
            activeStack = stack;
            activeStructureRevision = stack.combinedStructureRevision();
            return false;
        }
        return true;
    }

    private static int countEnabled(PostEffectStack stack) {
        int count = 0;
        for (PostEffect effect : stack.effects()) {
            if (effect.enabled()) {
                count++;
            }
        }
        return count;
    }

    public TextureHandle outputTexture(PostEffectInsertionPoint point) {
        return chainOutputs.getOrDefault(point, baseInput(point));
    }

    public boolean hasEffects(PostEffectInsertionPoint point) {
        for (CompiledEffect compiled : compiledEffects) {
            if (compiled.point() == point) {
                return true;
            }
        }
        return false;
    }

    public void render(PostEffectInsertionPoint point) {
        for (CompiledEffect compiled : compiledEffects) {
            if (compiled.point() != point) {
                continue;
            }
            backend.beginPass(compiled.outputTarget(), PassClear.none());
            backend.execute(DrawCommand.of(compiled.pipeline(), quad.mesh(), compiled.bindings()));
            backend.endPass();
        }
    }

    private TextureHandle baseInput(PostEffectInsertionPoint point) {
        return point == PostEffectInsertionPoint.BEFORE_TONEMAP ? sceneColorTexture : ldrColorTexture;
    }

    private void rebuild(PostEffectStack stack) {
        destroyCompiled();
        applyDeclaredInsertionPoints(stack);
        activeStack = stack;
        activeStructureRevision = stack.combinedStructureRevision();
        rebuildRequested = false;
        reloadRequested = false;
        for (PostEffectInsertionPoint point : PostEffectInsertionPoint.values()) {
            rebuildPoint(stack, point);
        }
    }

    private void applyDeclaredInsertionPoints(PostEffectStack stack) {
        for (PostEffect effect : stack.effects()) {
            declaredInsertionPoint(effect).ifPresent(declared -> {
                if (declared != effect.insertionPoint()) {
                    effect.setInsertionPoint(declared);
                }
            });
        }
    }

    private Optional<PostEffectInsertionPoint> declaredInsertionPoint(PostEffect effect) {
        try {
            return PostEffectInsertionPoint.declaredIn(shaderLoader.load(effect.shaderPath()).source());
        } catch (RuntimeException unreadable) {
            return Optional.empty();
        }
    }

    private void rebuildPoint(PostEffectStack stack, PostEffectInsertionPoint point) {
        List<PostEffect> enabled = enabledEffectsAt(stack, point);
        if (enabled.isEmpty()) {
            return;
        }
        createPingPong(point);
        TextureHandle input = baseInput(point);
        int pingIndex = 0;
        for (PostEffect effect : enabled) {
            Optional<CompiledEffect> compiled = compileEffect(effect, point, input, pingIndex);
            if (compiled.isPresent()) {
                compiledEffects.add(compiled.get());
                input = pingTextures.get(point)[pingIndex];
                pingIndex = (pingIndex + 1) % PING_PONG_COUNT;
            }
        }
        chainOutputs.put(point, input);
    }

    private static List<PostEffect> enabledEffectsAt(PostEffectStack stack, PostEffectInsertionPoint point) {
        List<PostEffect> result = new ArrayList<>();
        for (PostEffect effect : stack.effects()) {
            if (effect.enabled() && effect.insertionPoint() == point) {
                result.add(effect);
            }
        }
        return result;
    }

    private void createPingPong(PostEffectInsertionPoint point) {
        TextureFormat format = point == PostEffectInsertionPoint.BEFORE_TONEMAP
                ? TextureFormat.RGBA16F : TextureFormat.RGBA8;
        TextureHandle[] textures = new TextureHandle[PING_PONG_COUNT];
        RenderTargetHandle[] targets = new RenderTargetHandle[PING_PONG_COUNT];
        for (int index = 0; index < PING_PONG_COUNT; index++) {
            textures[index] = backend.createTexture(new TextureDescriptor(
                    targetWidth, targetHeight, format, TextureUsage.SAMPLED));
            targets[index] = backend.createRenderTarget(new RenderTargetDescriptor(
                    targetWidth, targetHeight, List.of(textures[index]), Optional.empty()));
        }
        pingTextures.put(point, textures);
        pingTargets.put(point, targets);
    }

    private Optional<CompiledEffect> compileEffect(PostEffect effect, PostEffectInsertionPoint point,
                                                   TextureHandle input, int pingIndex) {
        try {
            return Optional.of(buildCompiledEffect(effect, point, input, pingIndex));
        } catch (RuntimeException error) {
            logger.error("[PostEffectChain] Failed to build effect '" + effect.name()
                    + "' from " + effect.shaderPath(), error);
            return Optional.empty();
        }
    }

    private CompiledEffect buildCompiledEffect(PostEffect effect, PostEffectInsertionPoint point,
                                               TextureHandle input, int pingIndex) {
        LoadedShader userShader = shaderLoader.load(effect.shaderPath());
        ParsedSource parsed = ShaderUniformParser.parse(userShader.source());
        String fragment = PostEffectComposer.compose(shaderLoader.load(TEMPLATE_PATH).source(), parsed);
        BindingSetLayout layout = layoutFor(parsed);
        PipelineHandle pipeline = backend.createPipeline(new PipelineDescriptor(
                new ShaderSource(shaderLoader.load(VERTEX_PATH).source(), fragment),
                FullscreenQuad.LAYOUT, PASS_STATE, layout));
        BufferHandle uniformBuffer = backend.createBuffer(new BufferDescriptor(BufferUsage.UNIFORM,
                BufferUtils.createByteBuffer(parsed.uniformBufferSize())));
        BindingSetHandle bindings = createBindings(effect, parsed, layout, input, uniformBuffer);
        watchSources(userShader);
        return new CompiledEffect(effect, point, parsed, pipeline, uniformBuffer, bindings,
                pingTargets.get(point)[pingIndex]);
    }

    private BindingSetLayout layoutFor(ParsedSource parsed) {
        List<BindingSlot> slots = new ArrayList<>();
        slots.add(new BindingSlot(PostEffectComposer.SCENE_COLOR_BINDING, BindingType.SAMPLED_TEXTURE_2D));
        slots.add(new BindingSlot(PostEffectComposer.SCENE_DEPTH_BINDING, BindingType.SAMPLED_TEXTURE_2D));
        slots.add(new BindingSlot(PostEffectComposer.FRAME_UNIFORM_BINDING, BindingType.UNIFORM_BUFFER));
        slots.add(new BindingSlot(PostEffectComposer.USER_UNIFORM_BINDING, BindingType.UNIFORM_BUFFER));
        for (int index = 0; index < parsed.samplerDeclarations().size(); index++) {
            slots.add(new BindingSlot(PostEffectComposer.FIRST_SAMPLER_BINDING + index,
                    BindingType.SAMPLED_TEXTURE_2D));
        }
        return new BindingSetLayout(List.copyOf(slots));
    }

    private BindingSetHandle createBindings(PostEffect effect, ParsedSource parsed, BindingSetLayout layout,
                                            TextureHandle input, BufferHandle uniformBuffer) {
        List<Binding> bindings = new ArrayList<>();
        bindings.add(new Binding(PostEffectComposer.SCENE_COLOR_BINDING, new SampledTextureBinding(input)));
        bindings.add(new Binding(PostEffectComposer.SCENE_DEPTH_BINDING, new SampledTextureBinding(sceneDepthTexture)));
        bindings.add(new Binding(PostEffectComposer.FRAME_UNIFORM_BINDING,
                UniformBufferBinding.whole(frameUniformBuffer, PostEffectComposer.FRAME_UNIFORM_SIZE)));
        bindings.add(new Binding(PostEffectComposer.USER_UNIFORM_BINDING,
                UniformBufferBinding.whole(uniformBuffer, parsed.uniformBufferSize())));
        appendSamplerBindings(bindings, effect, parsed);
        return backend.createBindingSet(new BindingSetDescriptor(layout, bindings));
    }

    private void appendSamplerBindings(List<Binding> bindings, PostEffect effect, ParsedSource parsed) {
        int slot = PostEffectComposer.FIRST_SAMPLER_BINDING;
        for (ShaderUniformDeclaration declaration : parsed.samplerDeclarations()) {
            bindings.add(new Binding(slot, new SampledTextureBinding(resolveTexture(effect, declaration))));
            slot++;
        }
    }

    private TextureHandle resolveTexture(PostEffect effect, ShaderUniformDeclaration declaration) {
        Optional<ShaderUniformValue> value = effect.uniformValue(declaration.name());
        if (value.isPresent() && value.get() instanceof ShaderUniformValue.TextureValue texture
                && !texture.path().isEmpty()) {
            return textureCache.computeIfAbsent(texture.path(), this::loadTexture);
        }
        if (declaration.hasDefault()) {
            return textureCache.computeIfAbsent(declaration.defaultText(), this::loadTexture);
        }
        return fallbackTexture;
    }

    private TextureHandle loadTexture(String path) {
        try {
            return Texture2D.load(backend, path);
        } catch (RuntimeException error) {
            logger.error("[PostEffectChain] Failed to load texture " + path, error);
            return fallbackTexture;
        }
    }

    private void watchSources(LoadedShader userShader) {
        if (shaderWatcher.isEmpty()) {
            return;
        }
        List<String> newPaths = new ArrayList<>();
        for (String path : userShader.dependencyPaths()) {
            if (watchedPaths.add(path)) {
                newPaths.add(path);
            }
        }
        if (!newPaths.isEmpty()) {
            shaderWatcher.get().watch(newPaths, () -> reloadRequested = true);
        }
    }

    public void setCameraState(float near, float far, Vector3f position, Matrix4f inverseViewProjection) {
        this.nearPlane = near;
        this.farPlane = far;
        this.cameraPosition.set(position);
        this.inverseViewProjection.set(inverseViewProjection);
    }

    private void writeFrameUniforms() {
        frameScratch.clear();
        frameScratch.putFloat((float) ((System.nanoTime() - startNanos) / NANOS_PER_SECOND));
        frameScratch.putFloat(nearPlane);
        frameScratch.putFloat((float) targetWidth).putFloat((float) targetHeight);
        frameScratch.putFloat(cameraPosition.x).putFloat(cameraPosition.y).putFloat(cameraPosition.z);
        frameScratch.putFloat(farPlane);
        inverseViewProjection.get(PostEffectComposer.FRAME_INVERSE_VIEW_PROJECTION_OFFSET, frameScratch);
        frameScratch.position(0);
        frameScratch.limit(PostEffectComposer.FRAME_UNIFORM_SIZE);
        backend.writeBuffer(frameUniformBuffer, frameScratch, 0L);
    }

    private void writeEffectUniforms() {
        for (CompiledEffect compiled : compiledEffects) {
            if (compiled.lastWrittenValueRevision() != compiled.effect().valueRevision()) {
                writeUniformBuffer(compiled);
            }
        }
    }

    private void writeUniformBuffer(CompiledEffect compiled) {
        ByteBuffer packed = BufferUtils.createByteBuffer(compiled.parsed().uniformBufferSize());
        for (ShaderUniformDeclaration declaration : compiled.parsed().bufferDeclarations()) {
            int offset = compiled.parsed().byteOffsetsByName().get(declaration.name());
            compiled.effect().uniformValue(declaration.name())
                    .or(() -> ShaderUniformDefaults.of(declaration))
                    .ifPresent(value -> ShaderUniformPacker.pack(packed, offset, declaration, value));
        }
        backend.writeBuffer(compiled.uniformBuffer(), packed, 0L);
        compiled.markValuesWritten();
    }

    private void destroyCompiled() {
        for (CompiledEffect compiled : compiledEffects) {
            backend.destroy(compiled.bindings());
            backend.destroy(compiled.pipeline());
            backend.destroy(compiled.uniformBuffer());
        }
        compiledEffects.clear();
        destroyPingPong();
        chainOutputs.clear();
    }

    private void destroyPingPong() {
        for (RenderTargetHandle[] targets : pingTargets.values()) {
            for (RenderTargetHandle target : targets) {
                backend.destroy(target);
            }
        }
        for (TextureHandle[] textures : pingTextures.values()) {
            for (TextureHandle texture : textures) {
                backend.destroy(texture);
            }
        }
        pingTargets.clear();
        pingTextures.clear();
    }

    public void shutdown() {
        if (backend == null) {
            return;
        }
        destroyCompiled();
        for (TextureHandle texture : textureCache.values()) {
            if (texture != fallbackTexture) {
                backend.destroy(texture);
            }
        }
        textureCache.clear();
        backend.destroy(fallbackTexture);
        backend.destroy(frameUniformBuffer);
        backend = null;
    }

    private static final class CompiledEffect {

        private final PostEffect effect;
        private final PostEffectInsertionPoint point;
        private final ParsedSource parsed;
        private final PipelineHandle pipeline;
        private final BufferHandle uniformBuffer;
        private final BindingSetHandle bindings;
        private final RenderTargetHandle outputTarget;
        private long lastWrittenValueRevision = -1L;

        private CompiledEffect(PostEffect effect, PostEffectInsertionPoint point, ParsedSource parsed,
                               PipelineHandle pipeline, BufferHandle uniformBuffer, BindingSetHandle bindings,
                               RenderTargetHandle outputTarget) {
            this.effect = effect;
            this.point = point;
            this.parsed = parsed;
            this.pipeline = pipeline;
            this.uniformBuffer = uniformBuffer;
            this.bindings = bindings;
            this.outputTarget = outputTarget;
        }

        private PostEffect effect() {
            return effect;
        }

        private PostEffectInsertionPoint point() {
            return point;
        }

        private ParsedSource parsed() {
            return parsed;
        }

        private PipelineHandle pipeline() {
            return pipeline;
        }

        private BufferHandle uniformBuffer() {
            return uniformBuffer;
        }

        private BindingSetHandle bindings() {
            return bindings;
        }

        private RenderTargetHandle outputTarget() {
            return outputTarget;
        }

        private long lastWrittenValueRevision() {
            return lastWrittenValueRevision;
        }

        private void markValuesWritten() {
            lastWrittenValueRevision = effect.valueRevision();
        }
    }
}
