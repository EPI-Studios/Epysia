package fr.epistudio.epysia.render.backend;

public record StorageBufferBinding(BufferHandle buffer, long byteOffset, long byteSize) implements BindingResource {

    public static StorageBufferBinding whole(BufferHandle buffer, long byteSize) {
        return new StorageBufferBinding(buffer, 0L, byteSize);
    }
}
