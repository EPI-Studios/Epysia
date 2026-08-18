package fr.epistudio.epysia.render.decal;

import fr.epistudio.epysia.render.backend.Binding;
import fr.epistudio.epysia.render.backend.BindingSetDescriptor;
import fr.epistudio.epysia.render.backend.BindingSetHandle;
import fr.epistudio.epysia.render.backend.BindingSetLayout;
import fr.epistudio.epysia.render.backend.BindingSlot;
import fr.epistudio.epysia.render.backend.BindingType;
import fr.epistudio.epysia.render.backend.BufferDescriptor;
import fr.epistudio.epysia.render.backend.BufferHandle;
import fr.epistudio.epysia.render.backend.BufferUsage;
import fr.epistudio.epysia.render.backend.RenderBackend;
import fr.epistudio.epysia.render.backend.SampledTextureBinding;
import fr.epistudio.epysia.render.backend.TextureHandle;
import fr.epistudio.epysia.render.backend.UniformBufferBinding;
import org.joml.Matrix4f;
import org.lwjgl.BufferUtils;

import java.nio.ByteBuffer;
import java.util.List;

final class DecalResources {

    static final int UBO_BYTES = 4 * 64 + 4 * 16;

    private static final int UBO_BINDING = 0;
    private static final int DEPTH_BINDING = 1;
    private static final int NORMAL_BINDING = 2;
    private static final int TEXTURE_BINDING = 3;

    private final BufferHandle ubo;
    private final BindingSetHandle bindings;
    private final TextureHandle texture;
    private final TextureHandle depth;
    private final TextureHandle normal;
    private final ByteBuffer staging = BufferUtils.createByteBuffer(UBO_BYTES);

    DecalResources(RenderBackend backend, TextureHandle texture, TextureHandle depth, TextureHandle normal) {
        this.texture = texture;
        this.depth = depth;
        this.normal = normal;
        this.ubo = backend.createBuffer(new BufferDescriptor(BufferUsage.UNIFORM,
                BufferUtils.createByteBuffer(UBO_BYTES), true));
        this.bindings = backend.createBindingSet(new BindingSetDescriptor(layout(), List.of(
                new Binding(UBO_BINDING, UniformBufferBinding.whole(ubo, UBO_BYTES)),
                new Binding(DEPTH_BINDING, new SampledTextureBinding(depth)),
                new Binding(NORMAL_BINDING, new SampledTextureBinding(normal)),
                new Binding(TEXTURE_BINDING, new SampledTextureBinding(texture)))));
    }

    static BindingSetLayout layout() {
        return new BindingSetLayout(List.of(
                new BindingSlot(UBO_BINDING, BindingType.UNIFORM_BUFFER),
                new BindingSlot(DEPTH_BINDING, BindingType.SAMPLED_TEXTURE_2D),
                new BindingSlot(NORMAL_BINDING, BindingType.SAMPLED_TEXTURE_2D),
                new BindingSlot(TEXTURE_BINDING, BindingType.SAMPLED_TEXTURE_2D)));
    }

    boolean matches(TextureHandle otherTexture, TextureHandle otherDepth, TextureHandle otherNormal) {
        return texture.equals(otherTexture) && depth.equals(otherDepth) && normal.equals(otherNormal);
    }

    BindingSetHandle bindings() {
        return bindings;
    }

    void write(Matrix4f viewProjection, Matrix4f inverseViewProjection, Matrix4f model,
               Matrix4f inverseModel, Decal decal) {
        staging.clear();
        viewProjection.get(0, staging);
        inverseViewProjection.get(64, staging);
        model.get(128, staging);
        inverseModel.get(192, staging);
        staging.position(256);
        staging.putFloat(decal.tint().x).putFloat(decal.tint().y).putFloat(decal.tint().z)
                .putFloat(decal.opacity());
        staging.putFloat(decal.uvScale().x).putFloat(decal.uvScale().y)
                .putFloat(decal.uvOffset().x).putFloat(decal.uvOffset().y);
        staging.putFloat(decal.angleFadeCosine()).putFloat(0.0f).putFloat(0.0f).putFloat(0.0f);
        staging.putInt(decal.layerMask()).putInt(decal.blend().ordinal()).putInt(0).putInt(0);
        staging.flip();
    }

    ByteBuffer staging() {
        return staging;
    }

    BufferHandle ubo() {
        return ubo;
    }

    void destroy(RenderBackend backend) {
        backend.destroy(bindings);
        backend.destroy(ubo);
    }
}
