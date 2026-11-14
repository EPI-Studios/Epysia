package fr.epistudio.epysia.ui;

import fr.epistudio.epysia.render.backend.Binding;
import fr.epistudio.epysia.render.backend.BindingSetDescriptor;
import fr.epistudio.epysia.render.backend.BindingSetHandle;
import fr.epistudio.epysia.render.backend.BindingSetLayout;
import fr.epistudio.epysia.render.backend.BufferDescriptor;
import fr.epistudio.epysia.render.backend.BufferHandle;
import fr.epistudio.epysia.render.backend.BufferUsage;
import fr.epistudio.epysia.render.backend.IndexFormat;
import fr.epistudio.epysia.render.backend.MeshDescriptor;
import fr.epistudio.epysia.render.backend.MeshHandle;
import fr.epistudio.epysia.render.backend.RenderBackend;
import fr.epistudio.epysia.render.backend.SampledTextureBinding;
import fr.epistudio.epysia.render.backend.TextureHandle;
import fr.epistudio.epysia.render.backend.UniformBufferBinding;
import org.joml.Matrix4f;
import org.lwjgl.BufferUtils;

import java.nio.ByteBuffer;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

final class UiWorldBatch {
    static final int UBO_SIZE = 96;

    private static final int MATRIX_OFFSET = 32;
    private static final int INITIAL_QUADS = 256;

    private final RenderBackend backend;
    private final BindingSetLayout bindingLayout;
    private final UiDrawList drawList = new UiDrawList();
    private final Map<TextureHandle, BindingSetHandle> bindingSets = new HashMap<>();
    private final ByteBuffer uboScratch = BufferUtils.createByteBuffer(UBO_SIZE);
    private final BufferHandle ubo;
    private BufferHandle vertexBuffer;
    private BufferHandle indexBuffer;
    private MeshHandle mesh;
    private int quadCapacity;

    UiWorldBatch(RenderBackend backend, BindingSetLayout bindingLayout) {
        this.backend = backend;
        this.bindingLayout = bindingLayout;
        this.ubo = backend.createBuffer(new BufferDescriptor(BufferUsage.UNIFORM,
                BufferUtils.createByteBuffer(UBO_SIZE)));
        allocateGeometry(INITIAL_QUADS);
    }

    UiDrawList drawList() {
        return drawList;
    }

    MeshHandle mesh() {
        return mesh;
    }

    void upload(float width, float height, float timeSeconds, Matrix4f canvasToClip) {
        uboScratch.clear();
        uboScratch.putFloat(width).putFloat(height).putFloat(0.0f).putFloat(0.0f);
        uboScratch.putFloat(timeSeconds).putFloat(0.0f).putFloat(0.0f).putFloat(0.0f);
        canvasToClip.get(MATRIX_OFFSET, uboScratch);
        uboScratch.position(0).limit(UBO_SIZE);
        backend.writeBuffer(ubo, uboScratch, 0L);
        ByteBuffer vertexData = drawList.vertexData();
        ByteBuffer indexData = drawList.indexData();
        int requiredQuads = indexData.remaining() / (UiDrawList.INDICES_PER_QUAD * Integer.BYTES) + 1;
        if (requiredQuads > quadCapacity) {
            allocateGeometry(Math.max(requiredQuads, quadCapacity * 2));
        }
        backend.writeBuffer(vertexBuffer, vertexData, 0L);
        backend.writeBuffer(indexBuffer, indexData, 0L);
    }

    BindingSetHandle bindingsFor(TextureHandle texture) {
        return bindingSets.computeIfAbsent(texture, handle -> backend.createBindingSet(
                new BindingSetDescriptor(bindingLayout, List.of(
                        new Binding(0, UniformBufferBinding.whole(ubo, UBO_SIZE)),
                        new Binding(1, new SampledTextureBinding(handle))))));
    }

    void destroy() {
        for (BindingSetHandle bindings : bindingSets.values()) {
            backend.destroy(bindings);
        }
        bindingSets.clear();
        releaseGeometry();
        backend.destroy(ubo);
    }

    private void allocateGeometry(int quads) {
        releaseGeometry();
        quadCapacity = quads;
        vertexBuffer = backend.createBuffer(new BufferDescriptor(BufferUsage.VERTEX,
                BufferUtils.createByteBuffer(quads * UiDrawList.VERTICES_PER_QUAD * UiDrawList.VERTEX_BYTES)));
        indexBuffer = backend.createBuffer(new BufferDescriptor(BufferUsage.INDEX,
                BufferUtils.createByteBuffer(quads * UiDrawList.INDICES_PER_QUAD * Integer.BYTES)));
        mesh = backend.createMesh(new MeshDescriptor(vertexBuffer, indexBuffer, 0,
                quads * UiDrawList.INDICES_PER_QUAD, IndexFormat.UINT32));
    }

    private void releaseGeometry() {
        if (quadCapacity == 0) {
            return;
        }
        backend.destroy(mesh);
        backend.destroy(vertexBuffer);
        backend.destroy(indexBuffer);
        quadCapacity = 0;
    }
}
