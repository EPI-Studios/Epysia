package fr.epistudio.epysia.render.mesh;

import fr.epistudio.epysia.logging.Logger;
import fr.epistudio.epysia.render.backend.Binding;
import fr.epistudio.epysia.render.backend.BindingSlot;
import fr.epistudio.epysia.render.backend.BindingType;
import fr.epistudio.epysia.render.backend.BufferDescriptor;
import fr.epistudio.epysia.render.backend.BufferHandle;
import fr.epistudio.epysia.render.backend.BufferUsage;
import fr.epistudio.epysia.render.backend.RenderBackend;
import fr.epistudio.epysia.render.backend.SampledTextureBinding;
import fr.epistudio.epysia.render.backend.TextureHandle;
import fr.epistudio.epysia.render.backend.UniformBufferBinding;
import fr.epistudio.epysia.render.material.LitMaterial;
import fr.epistudio.epysia.render.material.Material;
import fr.epistudio.epysia.render.shader.ShaderUniformDeclaration;
import fr.epistudio.epysia.render.shader.ShaderUniformDefaults;
import fr.epistudio.epysia.render.shader.ShaderUniformPacker;
import fr.epistudio.epysia.render.shader.ShaderUniformParser.ParsedSource;
import fr.epistudio.epysia.render.shader.ShaderUniformValue;
import fr.epistudio.epysia.render.shader.ShaderUniformValues;
import fr.epistudio.epysia.render.shader.SurfaceShaderComposer;
import fr.epistudio.epysia.render.texture.Texture2D;
import org.lwjgl.BufferUtils;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

final class SurfaceUniformBinder {

    private static final int SCRATCH_BYTES = 4096;
    private static final byte[] NO_UNIFORM_BYTES = new byte[0];

    private final Logger logger;
    private final Map<Material, SizedBuffer> uniformBuffers = new IdentityHashMap<>();
    private final Map<String, TextureHandle> textureCache = new HashMap<>();
    private final Set<Material> writtenThisFrame = Collections.newSetFromMap(new IdentityHashMap<>());
    private final List<BufferHandle> ownedBuffers = new ArrayList<>();
    private final ByteBuffer scratch = BufferUtils.createByteBuffer(SCRATCH_BYTES);

    private RenderBackend backend;
    private TextureHandle fallbackTexture;

    SurfaceUniformBinder(Logger logger) {
        this.logger = logger;
    }

    void initialize(RenderBackend backend) {
        this.backend = backend;
        this.fallbackTexture = Texture2D.whitePixel(backend);
    }

    void beginFrame() {
        writtenThisFrame.clear();
    }

    static void appendSlots(List<BindingSlot> slots, ParsedSource parsed) {
        if (parsed.hasBufferDeclarations()) {
            slots.add(new BindingSlot(SurfaceShaderComposer.USER_UNIFORM_BINDING, BindingType.UNIFORM_BUFFER));
        }
        for (int index = 0; index < parsed.samplerDeclarations().size(); index++) {
            slots.add(new BindingSlot(SurfaceShaderComposer.FIRST_SAMPLER_BINDING + index,
                    BindingType.SAMPLED_TEXTURE_2D));
        }
    }

    void appendBindings(List<Binding> bindings, Material material, ParsedSource parsed) {
        if (parsed.hasBufferDeclarations()) {
            bindings.add(new Binding(SurfaceShaderComposer.USER_UNIFORM_BINDING,
                    UniformBufferBinding.whole(ensureUniformBuffer(material, parsed), parsed.uniformBufferSize())));
        }
        int slot = SurfaceShaderComposer.FIRST_SAMPLER_BINDING;
        for (ShaderUniformDeclaration declaration : parsed.samplerDeclarations()) {
            bindings.add(new Binding(slot, new SampledTextureBinding(resolveTexture(material, declaration))));
            slot++;
        }
    }

    private BufferHandle ensureUniformBuffer(Material material, ParsedSource parsed) {
        SizedBuffer existing = uniformBuffers.get(material);
        if (existing != null && existing.byteSize() == parsed.uniformBufferSize()) {
            return existing.handle();
        }
        if (existing != null) {
            backend.destroy(existing.handle());
            ownedBuffers.remove(existing.handle());
        }
        BufferHandle created = backend.createBuffer(new BufferDescriptor(BufferUsage.UNIFORM,
                BufferUtils.createByteBuffer(parsed.uniformBufferSize())));
        ownedBuffers.add(created);
        uniformBuffers.put(material, new SizedBuffer(created, parsed.uniformBufferSize()));
        return created;
    }

    void writeIfNeeded(Material material, ParsedSource parsed) {
        if (!parsed.hasBufferDeclarations() || !writtenThisFrame.add(material)) {
            return;
        }
        SizedBuffer buffer = uniformBuffers.get(material);
        if (buffer == null || buffer.byteSize() != parsed.uniformBufferSize()) {
            return;
        }
        packInto(material, parsed);
        backend.writeBuffer(buffer.handle(), scratch, 0L);
    }

    private void packInto(Material material, ParsedSource parsed) {
        int size = Math.min(parsed.uniformBufferSize(), SCRATCH_BYTES);
        scratch.clear();
        for (int index = 0; index < size; index++) {
            scratch.put(index, (byte) 0);
        }
        for (ShaderUniformDeclaration declaration : parsed.bufferDeclarations()) {
            Integer offset = parsed.byteOffsetsByName().get(declaration.name());
            Optional<ShaderUniformValue> value = resolveValue(material, declaration);
            if (offset != null && value.isPresent() && offset + declaration.packedByteSize() <= size) {
                ShaderUniformPacker.pack(scratch, offset, declaration, value.get());
            }
        }
        scratch.position(0);
        scratch.limit(size);
    }

    private Optional<ShaderUniformValue> resolveValue(Material material, ShaderUniformDeclaration declaration) {
        Optional<ShaderUniformValue> assigned = valuesOf(material).value(declaration.name());
        return assigned.isPresent() ? assigned : ShaderUniformDefaults.of(declaration);
    }

    private static ShaderUniformValues valuesOf(Material material) {
        return material instanceof LitMaterial lit ? lit.surfaceUniforms() : new ShaderUniformValues();
    }

    static long valueRevisionOf(Material material) {
        return material instanceof LitMaterial lit ? lit.surfaceUniforms().valueRevision() : 0L;
    }

    static long structureRevisionOf(Material material) {
        return material instanceof LitMaterial lit ? lit.surfaceUniforms().structureRevision() : 0L;
    }

    byte[] uniformSnapshotOf(Material material, ParsedSource parsed) {
        if (!parsed.hasBufferDeclarations()) {
            return NO_UNIFORM_BYTES;
        }
        packInto(material, parsed);
        byte[] snapshot = new byte[scratch.limit()];
        for (int index = 0; index < snapshot.length; index++) {
            snapshot[index] = scratch.get(index);
        }
        return snapshot;
    }

    long[] samplerHandlesOf(Material material, ParsedSource parsed) {
        long[] handles = new long[parsed.samplerDeclarations().size()];
        for (int index = 0; index < handles.length; index++) {
            handles[index] = resolveTexture(material, parsed.samplerDeclarations().get(index)).id();
        }
        return handles;
    }

    private TextureHandle resolveTexture(Material material, ShaderUniformDeclaration declaration) {
        Optional<ShaderUniformValue> value = valuesOf(material).value(declaration.name());
        if (value.orElse(null) instanceof ShaderUniformValue.TextureValue texture && !texture.path().isEmpty()) {
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
            logger.error("[SurfaceUniformBinder] Failed to load texture " + path, error);
            return fallbackTexture;
        }
    }

    void shutdown() {
        if (backend == null) {
            return;
        }
        for (BufferHandle buffer : ownedBuffers) {
            backend.destroy(buffer);
        }
        ownedBuffers.clear();
        uniformBuffers.clear();
        writtenThisFrame.clear();
        for (TextureHandle texture : textureCache.values()) {
            if (texture != fallbackTexture) {
                backend.destroy(texture);
            }
        }
        textureCache.clear();
        backend.destroy(fallbackTexture);
    }

    private record SizedBuffer(BufferHandle handle, int byteSize) {
    }
}
