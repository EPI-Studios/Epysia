package fr.epistudio.epysia.render.backend;

public record UniformBufferBinding(BufferHandle buffer, long byteOffset, long byteSize) implements BindingResource {

    public static UniformBufferBinding whole(BufferHandle buffer, long byteSize) {
        return new UniformBufferBinding(buffer, 0L, byteSize);
    }
}
