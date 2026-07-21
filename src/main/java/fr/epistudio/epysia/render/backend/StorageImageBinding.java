package fr.epistudio.epysia.render.backend;

public record StorageImageBinding(TextureHandle texture, int mipLevel, StorageImageAccess access)
        implements BindingResource {

    public static StorageImageBinding writeOnly(TextureHandle texture) {
        return new StorageImageBinding(texture, 0, StorageImageAccess.WRITE_ONLY);
    }

    public static StorageImageBinding readWrite(TextureHandle texture) {
        return new StorageImageBinding(texture, 0, StorageImageAccess.READ_WRITE);
    }
}
