package fr.epistudio.epysia.render.backend;

public sealed interface BindingResource permits UniformBufferBinding, SampledTextureBinding, StorageBufferBinding, StorageImageBinding {
}
