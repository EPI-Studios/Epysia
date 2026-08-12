package fr.epistudio.epysia.render.vulkan;

import fr.epistudio.epysia.render.backend.SamplerFilter;
import fr.epistudio.epysia.render.backend.TextureDescriptor;
import fr.epistudio.epysia.render.backend.TextureFormat;
import fr.epistudio.epysia.render.backend.TextureKind;
import fr.epistudio.epysia.render.backend.TextureUsage;
import fr.epistudio.epysia.render.backend.TextureWrap;
import org.lwjgl.PointerBuffer;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.util.vma.Vma;
import org.lwjgl.util.vma.VmaAllocationCreateInfo;
import org.lwjgl.util.vma.VmaAllocationInfo;
import org.lwjgl.vulkan.VK10;
import org.lwjgl.vulkan.VkImageCreateInfo;
import org.lwjgl.vulkan.VkImageViewCreateInfo;
import org.lwjgl.vulkan.VkSamplerCreateInfo;

import java.nio.LongBuffer;

public final class VulkanTextureFactory {

    private final VulkanDevice device;

    public VulkanTextureFactory(VulkanDevice device) {
        this.device = device;
    }

    public VulkanTexture create(TextureDescriptor descriptor) {
        int vulkanFormat = VulkanFormats.of(descriptor.format());
        int aspect = VulkanFormats.aspectOf(descriptor.format());
        try (MemoryStack stack = MemoryStack.stackPush()) {
            LongBuffer createdImage = stack.mallocLong(1);
            PointerBuffer createdAllocation = stack.mallocPointer(1);
            allocateImage(descriptor, vulkanFormat, createdImage, createdAllocation, stack);
            long view = createView(createdImage.get(0), descriptor, vulkanFormat, aspect,
                    VulkanFormats.viewTypeOf(descriptor.kind()), 0, descriptor.mipLevels(),
                    0, arrayLayersOf(descriptor), stack);
            return new VulkanTexture(createdImage.get(0), createdAllocation.get(0), view,
                    createSampler(descriptor, stack), descriptor.format(), descriptor.kind(),
                    vulkanFormat, aspect, descriptor.width(), descriptor.height(),
                    descriptor.layers(), Math.max(1, descriptor.mipLevels()),
                    VK10.VK_IMAGE_LAYOUT_UNDEFINED);
        }
    }

    private void allocateImage(TextureDescriptor descriptor, int vulkanFormat,
                               LongBuffer createdImage, PointerBuffer createdAllocation,
                               MemoryStack stack) {
        VkImageCreateInfo imageInfo = VkImageCreateInfo.calloc(stack)
                .sType$Default()
                .flags(descriptor.kind() == TextureKind.CUBEMAP
                        ? VK10.VK_IMAGE_CREATE_CUBE_COMPATIBLE_BIT : 0)
                .imageType(VulkanFormats.imageTypeOf(descriptor.kind()))
                .format(vulkanFormat)
                .mipLevels(Math.max(1, descriptor.mipLevels()))
                .arrayLayers(arrayLayersOf(descriptor))
                .samples(VK10.VK_SAMPLE_COUNT_1_BIT)
                .tiling(VK10.VK_IMAGE_TILING_OPTIMAL)
                .usage(usageOf(descriptor))
                .sharingMode(VK10.VK_SHARING_MODE_EXCLUSIVE)
                .initialLayout(VK10.VK_IMAGE_LAYOUT_UNDEFINED);
        imageInfo.extent().width(descriptor.width()).height(descriptor.height())
                .depth(descriptor.kind() == TextureKind.TEXTURE_3D ? descriptor.layers() : 1);
        VmaAllocationCreateInfo allocationInfo = VmaAllocationCreateInfo.calloc(stack)
                .usage(Vma.VMA_MEMORY_USAGE_AUTO_PREFER_DEVICE);
        VulkanResult.check(Vma.vmaCreateImage(device.allocator(), imageInfo, allocationInfo,
                createdImage, createdAllocation, VmaAllocationInfo.calloc(stack)), "vmaCreateImage");
    }

    private static int arrayLayersOf(TextureDescriptor descriptor) {
        return switch (descriptor.kind()) {
            case CUBEMAP -> 6;
            case ARRAY_2D -> descriptor.layers();
            case TEXTURE_2D, TEXTURE_3D -> 1;
        };
    }

    private static int usageOf(TextureDescriptor descriptor) {
        int shared = VK10.VK_IMAGE_USAGE_SAMPLED_BIT
                | VK10.VK_IMAGE_USAGE_TRANSFER_DST_BIT
                | VK10.VK_IMAGE_USAGE_TRANSFER_SRC_BIT;
        TextureFormat format = descriptor.format();
        if (format == TextureFormat.DEPTH32F || format == TextureFormat.DEPTH32F_STENCIL8) {
            return shared | VK10.VK_IMAGE_USAGE_DEPTH_STENCIL_ATTACHMENT_BIT;
        }
        if (format == TextureFormat.SRGB8_ALPHA8) {
            return shared | VK10.VK_IMAGE_USAGE_COLOR_ATTACHMENT_BIT;
        }
        return shared | VK10.VK_IMAGE_USAGE_COLOR_ATTACHMENT_BIT | storageBitFor(descriptor);
    }

    private static int storageBitFor(TextureDescriptor descriptor) {
        return descriptor.usage() == TextureUsage.SAMPLED ? VK10.VK_IMAGE_USAGE_STORAGE_BIT : 0;
    }

    public long attachmentView(VulkanTexture texture, int mipLevel, int layer) {
        VulkanTexture.AttachmentViewKey key = new VulkanTexture.AttachmentViewKey(mipLevel, layer);
        Long existing = texture.attachmentViews().get(key);
        if (existing != null) {
            return existing;
        }
        long created = createSingleSubresourceView(texture, mipLevel, layer);
        texture.attachmentViews().put(key, created);
        return created;
    }

    private long createSingleSubresourceView(VulkanTexture texture, int mipLevel, int layer) {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            VkImageViewCreateInfo createInfo = VkImageViewCreateInfo.calloc(stack)
                    .sType$Default()
                    .image(texture.image())
                    .viewType(texture.kind() == TextureKind.TEXTURE_3D
                            ? VK10.VK_IMAGE_VIEW_TYPE_3D : VK10.VK_IMAGE_VIEW_TYPE_2D)
                    .format(texture.vulkanFormat());
            createInfo.subresourceRange()
                    .aspectMask(texture.aspectMask())
                    .baseMipLevel(mipLevel)
                    .levelCount(1)
                    .baseArrayLayer(Math.max(0, layer))
                    .layerCount(1);
            LongBuffer created = stack.mallocLong(1);
            VulkanResult.check(VK10.vkCreateImageView(device.handle(), createInfo, null, created),
                    "vkCreateImageView");
            return created.get(0);
        }
    }

    private long createView(long image, TextureDescriptor descriptor, int vulkanFormat, int aspect,
                            int viewType, int baseMip, int mipCount, int baseLayer, int layerCount,
                            MemoryStack stack) {
        VkImageViewCreateInfo createInfo = VkImageViewCreateInfo.calloc(stack)
                .sType$Default()
                .image(image)
                .viewType(viewType)
                .format(vulkanFormat);
        createInfo.subresourceRange()
                .aspectMask(aspect)
                .baseMipLevel(baseMip)
                .levelCount(Math.max(1, mipCount))
                .baseArrayLayer(baseLayer)
                .layerCount(layerCount);
        LongBuffer created = stack.mallocLong(1);
        VulkanResult.check(VK10.vkCreateImageView(device.handle(), createInfo, null, created),
                "vkCreateImageView");
        return created.get(0);
    }

    private long createSampler(TextureDescriptor descriptor, MemoryStack stack) {
        boolean nearest = descriptor.samplerFilter() == SamplerFilter.NEAREST;
        int filter = nearest ? VK10.VK_FILTER_NEAREST : VK10.VK_FILTER_LINEAR;
        VkSamplerCreateInfo createInfo = VkSamplerCreateInfo.calloc(stack)
                .sType$Default()
                .magFilter(filter)
                .minFilter(filter)
                .mipmapMode(VK10.VK_SAMPLER_MIPMAP_MODE_LINEAR)
                .addressModeU(addressModeOf(descriptor.wrap()))
                .addressModeV(addressModeOf(descriptor.wrap()))
                .addressModeW(addressModeOf(descriptor.wrap()))
                .minLod(0.0f)
                .maxLod(Math.max(1, descriptor.mipLevels()))
                .borderColor(VK10.VK_BORDER_COLOR_FLOAT_OPAQUE_WHITE);
        configureAnisotropy(createInfo, descriptor, nearest);
        configureShadowCompare(createInfo, descriptor);
        LongBuffer created = stack.mallocLong(1);
        VulkanResult.check(VK10.vkCreateSampler(device.handle(), createInfo, null, created),
                "vkCreateSampler");
        return created.get(0);
    }

    private void configureAnisotropy(VkSamplerCreateInfo createInfo, TextureDescriptor descriptor,
                                     boolean nearest) {
        int requested = Math.min(descriptor.anisotropy(), device.limits().maximumSamplerAnisotropy());
        if (nearest || descriptor.mipLevels() <= 1 || requested <= TextureDescriptor.NO_ANISOTROPY) {
            return;
        }
        createInfo.anisotropyEnable(true).maxAnisotropy(requested);
    }

    private static void configureShadowCompare(VkSamplerCreateInfo createInfo,
                                               TextureDescriptor descriptor) {
        if (descriptor.usage() != TextureUsage.SAMPLED_DEPTH_SHADOW) {
            return;
        }
        createInfo.compareEnable(true).compareOp(VK10.VK_COMPARE_OP_LESS_OR_EQUAL);
    }

    private static int addressModeOf(TextureWrap wrap) {
        return switch (wrap) {
            case CLAMP_TO_EDGE -> VK10.VK_SAMPLER_ADDRESS_MODE_CLAMP_TO_EDGE;
            case REPEAT -> VK10.VK_SAMPLER_ADDRESS_MODE_REPEAT;
            case MIRRORED_REPEAT -> VK10.VK_SAMPLER_ADDRESS_MODE_MIRRORED_REPEAT;
        };
    }

    public void destroy(VulkanTexture texture) {
        texture.attachmentViews().values()
                .forEach(view -> VK10.vkDestroyImageView(device.handle(), view, null));
        texture.attachmentViews().clear();
        VK10.vkDestroySampler(device.handle(), texture.sampler(), null);
        VK10.vkDestroyImageView(device.handle(), texture.defaultView(), null);
        Vma.vmaDestroyImage(device.allocator(), texture.image(), texture.allocation());
    }
}
